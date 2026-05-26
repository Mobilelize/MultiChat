package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.Keys;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PanelTabsScreen extends LzScreen {

    private static final int LIST_TOP   = 46;
    private static final int ROW_HEIGHT = 22;
    private static final int SB_W       = 6;

    private final Screen    parent;
    private final ChatPanel panel;

    private final Map<ChatTab, Set<Integer>> originals = new HashMap<>();

    private int     scrollOffset       = 0;
    private boolean scrollDragging     = false;
    private int     scrollDragStartY   = 0;
    private int     scrollDragStartOff = 0;

    public PanelTabsScreen(Screen parent, ChatPanel panel) {
        super("Tabs — " + (panel.name.isEmpty() ? "Panel " + panel.id : panel.name));
        this.parent = parent;
        this.panel  = panel;
    }

    private List<ChatTab> tabs()  { return ChatRenderer.TABS; }
    private int listBottom()      { return height - 36; }
    private int maxScroll()       { return Math.max(0, tabs().size() * ROW_HEIGHT - (listBottom() - LIST_TOP)); }

    @Override
    protected void init() {
        if (originals.isEmpty()) {
            for (ChatTab t : ChatRenderer.TABS)
                originals.put(t, new LinkedHashSet<>(t.visibleOnPanels));
        }

        scrollOffset = Math.min(scrollOffset, maxScroll());

        int bx = width / 2 - 130;
        addDrawableChild(LzButton.of("Add All", bx, LIST_TOP - 22, 56, 18, b -> {
            for (ChatTab t : ChatRenderer.TABS) {
                if (t.type == TabType.VANILLA && !panel.primary) continue;
                t.visibleOnPanels.add(panel.id);
            }
        }));
        addDrawableChild(LzButton.of("Remove All", bx + 60, LIST_TOP - 22, 72, 18, b -> {
            for (ChatTab t : ChatRenderer.TABS) t.visibleOnPanels.remove(Integer.valueOf(panel.id));
        }));
        addDrawableChild(LzButton.of("Save",   width / 2 - 53, height - 26, 50, 18, b -> save()));
        addDrawableChild(LzButton.of("Cancel", width / 2 + 3,  height - 26, 50, 18, b -> attemptClose()));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        LzDraw.drawCenteredText(ctx, LzText.wrapMc(title), width / 2, 12, 0xFFFFFF);

        int lx = width / 2 - 130;
        List<ChatTab> tabList = tabs();

        LzDraw.enableScissor(ctx, 0, LIST_TOP, width, listBottom());

        for (int i = 0; i < tabList.size(); i++) {
            ChatTab tab = tabList.get(i);
            int y = LIST_TOP + i * ROW_HEIGHT - scrollOffset;
            if (y + ROW_HEIGHT <= LIST_TOP || y >= listBottom()) continue;

            int rowY1 = y + 1, rowY2 = y + ROW_HEIGHT - 1;
            int textY = y + (ROW_HEIGHT - 8) / 2;

            boolean locked = tab.type == TabType.VANILLA && !panel.primary;
            boolean on  = tab.visibleOnPanels.contains(panel.id);
            boolean hov = !locked && mouseX >= lx && mouseX < lx + 260 && mouseY >= y && mouseY < y + ROW_HEIGHT;

            LzDraw.fill(ctx, lx, rowY1, lx + 260, rowY2,
                locked ? 0x11FFFFFF : (on ? 0x4455FF88 : 0x22FFFFFF));
            LzDraw.drawBorder(ctx, lx, rowY1, lx + 260, rowY2,
                locked ? 0x22666666 : (on ? 0x8833BB66 : 0x44888888));

            int nameCol = locked ? 0x55AAAAAA : switch (tab.type) {
                case ALL     -> 0xAADDFF;
                case VANILLA -> 0xAADDFF;
                case SEARCH  -> 0xAAFFDD;
                case API     -> 0xFFCC88;
                default      -> 0xFFFFFF;
            };
            LzDraw.drawText(ctx, tab.name, lx + 6, textY, nameCol);

            // Type tag
            String typeTag = switch (tab.type) {
                case ALL     -> "[all]";
                case VANILLA -> locked ? "[main only]" : "[vanilla]";
                case SEARCH  -> "[search]";
                case API     -> "[api]";
                default      -> "";
            };
            if (!typeTag.isEmpty())
                LzDraw.drawText(ctx, typeTag, lx + 6 + LzDraw.textWidth(tab.name + " "), textY,
                    locked ? 0xFF884444 : 0xFF666666);

            // Toggle button
            boolean hovBtn = hov && mouseX >= lx + 192 && mouseX < lx + 228;
            LzDraw.fill(ctx, lx + 192, rowY1 + 2, lx + 228, rowY2 - 2,
                locked ? 0x22333333
                       : on ? (hovBtn ? 0xCC55AA55 : 0xAA337733) : (hovBtn ? 0xCC555555 : 0x44333333));
            LzDraw.drawCenteredText(ctx, LzText.literal(locked ? "--" : on ? "ON" : "OFF"), lx + 210, textY,
                locked ? 0x44888888 : (on ? 0xAAFFAA : 0x888888));
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
            LzDraw.fill(ctx, sbX, LIST_TOP, sbX + SB_W, listBottom(), 0x44FFFFFF);
            LzDraw.fill(ctx, sbX + 1, thumbY, sbX + SB_W - 1, thumbY + thumbH,
                scrollDragging ? 0xFFDDDDDD : 0xBBAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;

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
        if (tab.type == TabType.VANILLA && !panel.primary) return false; // locked to main panel

        if (mx >= lx && mx < lx + 260) {
            if (tab.visibleOnPanels.contains(panel.id)) tab.visibleOnPanels.remove(Integer.valueOf(panel.id));
            else                                         tab.visibleOnPanels.add(panel.id);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
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
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        scrollDragging = false;
        return super.mouseReleased(mx, my, button);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Keys.ESCAPE) { attemptClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isDirty() {
        for (Map.Entry<ChatTab, Set<Integer>> e : originals.entrySet()) {
            if (!e.getKey().visibleOnPanels.equals(e.getValue())) return true;
        }
        return false;
    }

    private void attemptClose() {
        if (isDirty()) LzClient.mc().setScreen(new ConfirmScreen(this, "Save changes?", this::save, this::cancel));
        else cancel();
    }

    private void save() {
        MultichatClient.saveTabsToConfig();
        close();
    }

    private void cancel() {
        for (Map.Entry<ChatTab, Set<Integer>> e : originals.entrySet()) {
            e.getKey().visibleOnPanels.clear();
            e.getKey().visibleOnPanels.addAll(e.getValue());
        }
        close();
    }

    @Override
    public void close() { LzClient.mc().setScreen(parent); }

    @Override
    public boolean shouldPause() { return false; }
}
