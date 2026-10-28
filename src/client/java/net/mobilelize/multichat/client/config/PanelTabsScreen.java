package net.mobilelize.multichat.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.fabriclib.world.LzWorld;
import net.mobilelize.multichat.client.*;

import java.util.*;

public class PanelTabsScreen extends LzScreen {

    private static final int LIST_TOP   = 46;
    private static final int ROW_HEIGHT = 22;
    private static final int SB_W       = 6;

    private final Screen    parent;
    private final ChatPanel panel;

    private final Map<ChatTab, Set<Integer>> originals = new HashMap<>();
    private ChatTab originalPanelContextTab = null;

    private int     scrollOffset       = 0;
    private boolean scrollDragging     = false;
    private int     scrollDragStartY   = 0;
    private int     scrollDragStartOff = 0;

    public PanelTabsScreen(Screen parent, ChatPanel panel) {
        super("Tabs — " + (panel.name().isEmpty() ? "Panel " + panel.id() : panel.name()));
        this.parent = parent;
        this.panel  = panel;
    }

    private List<ChatTab> tabs()  { return ChatManager.getTabs(); }
    private int listBottom()      { return height - 36; }
    private int maxScroll()       { return Math.max(0, tabs().size() * ROW_HEIGHT - (listBottom() - LIST_TOP)); }

    @Override
    protected void init() {
        if (originals.isEmpty()) {
            for (ChatTab t : ChatManager.getTabs())
                originals.put(t, new LinkedHashSet<>(t.visibleOnPanels()));
            originalPanelContextTab = panel.panelContextTab();
        }

        scrollOffset = Math.min(scrollOffset, maxScroll());

        int btnGroupX = width / 2 - 74;
        addRenderableWidget(LzButton.of("Add All", btnGroupX, LIST_TOP - 22, 72, 18, b -> {
            for (ChatTab t : ChatManager.getTabs()) {
                if (t.type() == TabType.VANILLA && !panel.primary()) continue;
                if (t.type() == TabType.INFO   &&  panel.primary()) continue;
                t.visibleOnPanels().add(panel.id());
            }
        }));
        addRenderableWidget(LzButton.of("Remove All", btnGroupX + 76, LIST_TOP - 22, 72, 18, b -> {
            for (ChatTab t : ChatManager.getTabs()) t.visibleOnPanels().remove(panel.id());
        }));
        addRenderableWidget(LzButton.of("Save",   width / 2 - 53, height - 26, 50, 18, b -> save()));
        addRenderableWidget(LzButton.of("Cancel", width / 2 + 3,  height - 26, 50, 18, b -> attemptClose()));
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        if (LzWorld.isLoaded()) LzDraw.fill(ctx, 0, 0, width, height, Colors.OVERLAY);
        super.lzRender(ctx, mouseX, mouseY, delta);
        LzDraw.drawCenteredText(ctx, LzText.wrapMc(title), width / 2, 12, Colors.TEXT_WHITE);

        int lx = width / 2 - 130;
        List<ChatTab> tabList = tabs();

        LzDraw.enableScissor(ctx, 0, LIST_TOP, width, listBottom());

        for (int i = 0; i < tabList.size(); i++) {
            ChatTab tab = tabList.get(i);
            int y = LIST_TOP + i * ROW_HEIGHT - scrollOffset;
            if (y + ROW_HEIGHT <= LIST_TOP || y >= listBottom()) continue;

            int rowY1 = y + 1, rowY2 = y + ROW_HEIGHT - 1;
            int textY = y + (ROW_HEIGHT - 8) / 2;

            boolean locked = (tab.type() == TabType.VANILLA && !panel.primary())
                          || (tab.type() == TabType.INFO   &&  panel.primary());
            boolean on  = tab.visibleOnPanels().contains(panel.id());
            boolean hov = !locked && mouseX >= lx && mouseX < lx + 260 && mouseY >= y && mouseY < y + ROW_HEIGHT;

            LzDraw.fill(ctx, lx, rowY1, lx + 260, rowY2,
                locked ? Colors.PANEL_TAB_LOCKED_FILL : (on ? Colors.PANEL_TAB_ENABLED_FILL : Colors.PANEL_TAB_DISABLED_FILL));
            LzDraw.drawBorder(ctx, lx, rowY1, lx + 260, rowY2,
                locked ? Colors.PANEL_TAB_LOCKED_BORDER : (on ? Colors.PANEL_TAB_ENABLED_BORDER : Colors.PANEL_TAB_DISABLED_BORDER));

            int nameCol = locked ? Colors.PANEL_TAB_LOCKED_LABEL : switch (tab.type()) {
                case ALL     -> Colors.TAB_ALL_TEXT;
                case VANILLA -> Colors.TAB_ALL_TEXT;
                case INFO    -> Colors.TAB_ALL_TEXT;
                case SEARCH  -> Colors.TAB_SEARCH_TEXT;
                case API     -> Colors.TAB_API_TEXT;
                default      -> Colors.TEXT_WHITE;
            };
            LzDraw.drawText(ctx, tab.name(), lx + 6, textY, nameCol);

            // Type tag
            String typeTag = switch (tab.type()) {
                case ALL     -> "[all]";
                case VANILLA -> locked ? "[main only]" : "[vanilla]";
                case INFO    -> locked ? "[panel 2 only]" : "[info]";
                case SEARCH  -> "[search]";
                case API     -> "[api]";
                default      -> "";
            };
            if (!typeTag.isEmpty())
                LzDraw.drawText(ctx, typeTag, lx + 6 + LzDraw.textWidth(tab.name() + " "), textY,
                    locked ? Colors.PANEL_TAB_LOCK_ICON : Colors.TEXT_DARK_GRAY);

            // Toggle button
            boolean hovBtn = hov && mouseX >= lx + 156 && mouseX < lx + 192;
            LzDraw.fill(ctx, lx + 156, rowY1 + 2, lx + 192, rowY2 - 2,
                locked ? Colors.PANEL_TAB_LOCKED_FILL
                       : on ? (hovBtn ? Colors.TOGGLE_ON_HOVER : Colors.TOGGLE_ON_BG) : (hovBtn ? Colors.TOGGLE_OFF_HOVER : Colors.FMT_BTN_BG));
            LzDraw.drawCenteredText(ctx, LzText.literal(locked ? "--" : on ? "ON" : "OFF"), lx + 174, textY,
                locked ? Colors.PANEL_TAB_DISABLED_BORDER : (on ? Colors.PANEL_TAB_ON_TOGGLE_TEXT : Colors.TEXT_GRAY));

            // Panel Context button — FILTERED and API tabs only
            boolean canCtx = !locked && (tab.type() == TabType.FILTERED || tab.type() == TabType.API);
            if (canCtx) {
                boolean isCtx   = panel.panelContextTab() == tab;
                boolean hovCtx  = mouseX >= lx + 194 && mouseX < lx + 258 && mouseY >= y && mouseY < y + ROW_HEIGHT;
                int ctxBg = isCtx ? (hovCtx ? Colors.CTX_ACTIVE_BG_HOVER : Colors.CTX_ACTIVE_BG)
                                  : (hovCtx ? Colors.CTX_INACTIVE_BG_HOVER : Colors.CTX_INACTIVE_BG);
                LzDraw.fill(ctx, lx + 194, rowY1 + 2, lx + 258, rowY2 - 2, ctxBg);
                LzDraw.drawCenteredText(ctx, LzText.literal(isCtx ? "● CTX" : "Set CTX"), lx + 226, textY,
                    isCtx ? Colors.CTX_ACTIVE_TEXT : Colors.TEXT_INACTIVE);
            }
        }

        LzDraw.disableScissor(ctx);

        // Scrollbar
        int max = maxScroll();
        if (max > 0) {
            int sbX    = lx + 265;
            int trackH = listBottom() - LIST_TOP;
            int totalH = tabList.size() * ROW_HEIGHT;
            int thumbH = Math.max(20, trackH * trackH / totalH);
            int thumbY = LIST_TOP + (int)((long) scrollOffset * (trackH - thumbH) / max);
            LzDraw.fill(ctx, sbX, LIST_TOP, sbX + SB_W, listBottom(), Colors.SCROLLBAR_TRACK);
            LzDraw.fill(ctx, sbX + 1, thumbY, sbX + SB_W - 1, thumbY + thumbH,
                scrollDragging ? Colors.SCROLLBAR_THUMB_DRAG : Colors.SCROLLBAR_THUMB);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean isDoubleClick) {
        double mx = click.x(); double my = click.y(); int button = click.button();
        if (super.mouseClicked(click, isDoubleClick)) return true;
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false;

        int lx = width / 2 - 130;

        // Scrollbar
        int lxSb = lx + 265, max = maxScroll();
        if (max > 0 && mx >= lxSb && mx < lxSb + SB_W && my >= LIST_TOP && my < listBottom()) {
            int trackH = listBottom() - LIST_TOP;
            int totalH = tabs().size() * ROW_HEIGHT;
            int thumbH = Math.max(20, trackH * trackH / totalH);
            int thumbY = LIST_TOP + (int)((long) scrollOffset * (trackH - thumbH) / max);
            if (my >= thumbY && my < thumbY + thumbH) {
                scrollDragging = true; scrollDragStartY = (int) my; scrollDragStartOff = scrollOffset;
            } else {
                int travel = trackH - thumbH;
                if (travel > 0)
                    scrollOffset = Math.max(0, Math.min(max,
                        (int)((my - LIST_TOP - thumbH / 2.0) * max / travel)));
            }
            return true;
        }

        if (my < LIST_TOP || my >= listBottom()) return false;

        int relY = (int)(my - LIST_TOP) + scrollOffset;
        int idx  = relY / ROW_HEIGHT;
        List<ChatTab> tabList = tabs();
        if (idx < 0 || idx >= tabList.size()) return false;

        ChatTab tab = tabList.get(idx);
        if (tab.type() == TabType.VANILLA && !panel.primary()) return false;
        if (tab.type() == TabType.INFO   &&  panel.primary()) return false;

        // CTX button
        if (mx >= lx + 194 && mx < lx + 258
                && (tab.type() == TabType.FILTERED || tab.type() == TabType.API)) {
            panel.setPanelContextTab(panel.panelContextTab() == tab ? null : tab);
            MultichatClient.saveTabsToConfig();
            return true;
        }

        // Visibility toggle (name area + toggle button, but not CTX region)
        if (mx >= lx && mx < lx + 194) {
            if (tab.visibleOnPanels().contains(panel.id())) tab.visibleOnPanels().remove(panel.id());
            else                                         tab.visibleOnPanels().add(panel.id());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        double my = click.y();
        if (scrollDragging) {
            int max = maxScroll(), trackH = listBottom() - LIST_TOP;
            int totalH = tabs().size() * ROW_HEIGHT;
            int thumbH = Math.max(20, trackH * trackH / totalH);
            int travel = trackH - thumbH;
            if (travel > 0)
                scrollOffset = Math.max(0, Math.min(max,
                    scrollDragStartOff + (int)((my - scrollDragStartY) * max / travel)));
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        scrollDragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (my >= LIST_TOP && my < listBottom()) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll(), scrollOffset - v * ROW_HEIGHT));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == Keys.ESCAPE) { attemptClose(); return true; }
        return super.keyPressed(input);
    }

    private boolean isDirty() {
        if (panel.panelContextTab() != originalPanelContextTab) return true;
        for (Map.Entry<ChatTab, Set<Integer>> e : originals.entrySet()) {
            if (!e.getKey().visibleOnPanels().equals(e.getValue())) return true;
        }
        return false;
    }

    private void attemptClose() {
        if (isDirty()) LzClient.mc().gui.setScreen(new ConfirmScreen(this, "Save changes?", this::save, this::cancel));
        else cancel();
    }

    private void save() {
        MultichatClient.saveTabsToConfig();
        onClose();
    }

    private void cancel() {
        for (Map.Entry<ChatTab, Set<Integer>> e : originals.entrySet()) {
            e.getKey().visibleOnPanels().clear();
            e.getKey().visibleOnPanels().addAll(e.getValue());
        }
        panel.setPanelContextTab(originalPanelContextTab);
        MultichatClient.saveTabsToConfig();
        onClose();
    }

    @Override
    public void onClose() { LzClient.mc().gui.setScreen(parent); }
}
