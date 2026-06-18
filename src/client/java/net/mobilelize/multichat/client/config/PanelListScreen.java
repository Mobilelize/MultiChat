package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.input.LzKeys;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzTextField;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.text.LzFormatting;
import net.mobilelize.fabriclib.text.LzStyle;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.Keys;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.ChatManager;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PanelListScreen extends ConfigScreen {

    private static final int LIST_TOP   = 32;
    private static final int ROW_HEIGHT = 22;

    private LzTextField panelNameField;
    private int     scrollOffset       = 0;
    private boolean scrollDragging     = false;
    private int     scrollDragStartY   = 0;
    private int     scrollDragStartOff = 0;

    public PanelListScreen(Screen parent) {
        super("Panels", parent);
    }

    private List<ChatPanel> panels() { return ChatManager.getAllPanels(); }
    private int listBottom()         { return height - 80; }
    private int maxScroll()          { return Math.max(0, panels().size() * ROW_HEIGHT - (listBottom() - LIST_TOP)); }

    @Override
    protected void init() {
        scrollOffset = Math.min(scrollOffset, maxScroll());

        // Name field + Add + Layout on one row — left-anchored at list edge
        panelNameField = new LzTextField(width / 2 - 198, height - 74, 284, 20);
        panelNameField.setPlaceholder(LzText.literal("New panel name...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
        addSelectableChild(panelNameField);
        addDrawableChild(LzButton.of("Layout", width / 2 + 90, height - 74, 60, 20, b -> LzClient.mc().setScreen(new PanelLayoutScreen(this))));
        addDrawableChild(LzButton.of("Add",    width / 2 + 154, height - 74, 40, 20, b -> addPanel()));

        addConfigNav(width, height, NavPage.PANELS);
    }

    private void addPanel() {
        Set<Integer> ids = ChatManager.getAllPanels().stream().map(ChatPanel::id).collect(Collectors.toSet());
        int id = 0;
        while (ids.contains(id)) id++;

        ChatPanel panel = new ChatPanel();
        panel.setId(id);
        String fieldText = panelNameField != null ? panelNameField.getText().trim() : "";
        String baseName = fieldText.isEmpty() ? "Panel " + (ChatManager.getAllPanels().size() + 1) : fieldText;
        Set<String> usedNames = ChatManager.getAllPanels().stream().map(ChatPanel::name).collect(Collectors.toSet());
        String uniqueName = baseName;
        int suffix = 2;
        while (usedNames.contains(uniqueName)) uniqueName = baseName + " (" + suffix++ + ")";
        panel.setName(uniqueName);
        panel.setEnabled(true);
        panel.setAnchor(ChatPanel.Anchor.CUSTOM);
        panel.setCustomX(0.5f);
        panel.setCustomY(0.5f);

        // Add all tabs to the new panel (skip VANILLA — primary only)
        for (ChatTab t : ChatManager.getTabs()) {
            if (t.type() == net.mobilelize.multichat.client.TabType.VANILLA) continue;
            t.visibleOnPanels().add(id);
        }

        ChatManager.getAllPanels().add(panel);
        MultichatClient.saveTabsToConfig();
        clearAndInit();
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        super.lzRender(ctx, mouseX, mouseY, delta);
        LzDraw.drawCenteredText(ctx, LzText.wrapMc(title), width / 2, 10, 0xAAFFCC);
        LzDraw.drawCenteredText(ctx, LzText.format("&7layout panels  ·  click Tabs to assign"), width / 2, 21, 0xFFFFFF);
        if (panelNameField != null) panelNameField.render(ctx.mc(), mouseX, mouseY, delta);

        int lx = width / 2 - 200;
        List<ChatPanel> panels = panels();


        LzDraw.enableScissor(ctx, 0, LIST_TOP, width, listBottom());

        for (int i = 0; i < panels.size(); i++) {
            ChatPanel panel = panels.get(i);
            int y = LIST_TOP + i * ROW_HEIGHT - scrollOffset;
            if (y + ROW_HEIGHT <= LIST_TOP || y >= listBottom()) continue;

            int rowY1 = y + 1, rowY2 = y + ROW_HEIGHT - 1;
            int btnY1 = y + 3, btnY2 = y + ROW_HEIGHT - 3;
            int textY = y + (ROW_HEIGHT - 8) / 2;

            int rowBg  = i == 0 ? 0x44AAAAFF : i == 1 ? 0x44BB88FF : 0x4488FFAA;
            int rowBdr = i == 0 ? 0x886688CC : i == 1 ? 0x88886699 : 0x8855AA77;

            LzDraw.fill(ctx, lx, rowY1, lx + 400, rowY2, rowBg);
            LzDraw.drawBorder(ctx, lx, rowY1, lx + 400, rowY2, rowBdr);

            // Hover highlight
            boolean hoverRow = mouseX >= lx && mouseX < lx + 400 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hoverRow) {
                int hoverTint = i == 0 ? 0x44AAAAFF : i == 1 ? 0x44BB88FF : 0x4488FFAA;
                LzDraw.fill(ctx, lx, rowY1, lx + 400, rowY2, hoverTint);
            }

            // Name + tab count + anchor (anchor color = enabled status)
            String anchorTag = "[" + panel.anchor().name().toLowerCase() + "]";
            String displayName = panel.name().isEmpty() ? String.valueOf(panel.id()) : panel.name();
            long tabCount = ChatManager.getTabs().stream().filter(t -> t.visibleOnPanels().contains(panel.id())).count();
            String nameStr  = displayName + " ";
            String countStr = "(" + tabCount + ") ";
            int nameX = lx + 6;
            LzDraw.drawText(ctx, nameStr, nameX, textY, 0xFFFFFF);
            int afterNameStr = nameX + LzDraw.textWidth(nameStr);
            LzDraw.drawText(ctx, countStr, afterNameStr, textY, 0xFF888888);
            int afterName = afterNameStr + LzDraw.textWidth(countStr);
            LzDraw.drawText(ctx, anchorTag, afterName, textY, panel.isEnabled() ? 0xFF55FF55 : 0xFFFF5555);

            // Tabs button — always
            boolean hoverTabs = mouseX >= lx + 282 && mouseX < lx + 318 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            LzDraw.fill(ctx, lx + 282, btnY1, lx + 318, btnY2, hoverTabs ? 0xAA336688 : 0x66224466);
            LzDraw.drawBorder(ctx, lx + 282, btnY1, lx + 318, btnY2, 0x88446688);
            LzDraw.drawCenteredText(ctx, LzText.literal("Tabs"), lx + 300, textY, 0xAADDFF);

            // Edit button — always
            boolean hoverEdit = mouseX >= lx + 322 && mouseX < lx + 358 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            LzDraw.fill(ctx, lx + 322, btnY1, lx + 358, btnY2, hoverEdit ? 0xAAFFCC44 : 0x44FFCC44);
            LzDraw.drawBorder(ctx, lx + 322, btnY1, lx + 358, btnY2, 0x88FFCC44);
            LzDraw.drawCenteredText(ctx, LzText.literal("Edit"), lx + 340, textY, 0xFFFFCC44);

            // Del button — only for extra panels (index >= 2)
            if (i >= 2) {
                boolean hoverRemove   = mouseX >= lx + 362 && mouseX < lx + 398 && mouseY >= y && mouseY < y + ROW_HEIGHT;
                boolean instantDelete = TabListScreen.quickDeleteMode || LzKeys.isCtrlDown();
                int xBg  = instantDelete ? (hoverRemove ? 0xAAFF4444 : 0x66882222)
                                         : (hoverRemove ? 0xAAFF8800 : 0x66552200);
                int xBdr = instantDelete ? 0x88FF5555 : 0x88FFAA00;
                int xCol = instantDelete ? 0xFFFF8888 : 0xFFFFAA44;
                LzDraw.fill(ctx, lx + 362, btnY1, lx + 398, btnY2, xBg);
                LzDraw.drawBorder(ctx, lx + 362, btnY1, lx + 398, btnY2, xBdr);
                LzDraw.drawCenteredText(ctx, LzText.literal("Del"), lx + 380, textY, xCol);
            }
        }

        LzDraw.disableScissor(ctx);

        // Scrollbar
        int max = maxScroll();
        if (max > 0) {
            int sbX    = lx + 405;
            int trackH = listBottom() - LIST_TOP;
            int totalH = panels.size() * ROW_HEIGHT;
            int thumbH = Math.max(20, trackH * trackH / totalH);
            int thumbY = LIST_TOP + (int)((long) scrollOffset * (trackH - thumbH) / max);
            LzDraw.fill(ctx, sbX, LIST_TOP, sbX + SB_W, listBottom(), 0x44FFFFFF);
            LzDraw.drawBorder(ctx, sbX, LIST_TOP, sbX + SB_W, listBottom(), 0x88666666);
            LzDraw.fill(ctx, sbX + 1, thumbY, sbX + SB_W - 1, thumbY + thumbH,
                scrollDragging ? 0xFFDDDDDD : 0xBBAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        int lx  = width / 2 - 200;
        int lxSb = lx + 405;
        int max  = maxScroll();

        // Scrollbar
        if (button == 0 && max > 0 && mx >= lxSb && mx < lxSb + SB_W && my >= LIST_TOP && my < listBottom()) {
            int trackH = listBottom() - LIST_TOP;
            int totalH = panels().size() * ROW_HEIGHT;
            int thumbH = Math.max(20, trackH * trackH / totalH);
            int thumbY = LIST_TOP + (int)((long) scrollOffset * (trackH - thumbH) / max);
            if (my >= thumbY && my < thumbY + thumbH) {
                scrollDragging     = true;
                scrollDragStartY   = (int) my;
                scrollDragStartOff = scrollOffset;
            } else {
                int travel = trackH - thumbH;
                if (travel > 0)
                    scrollOffset = Math.max(0, Math.min(max,
                        (int)((my - LIST_TOP - thumbH / 2.0) * max / travel)));
            }
            return true;
        }

        if (my < LIST_TOP || my >= listBottom()) return false;

        int relY  = (int)(my - LIST_TOP) + scrollOffset;
        int idx   = relY / ROW_HEIGHT;
        List<ChatPanel> panels = panels();
        if (idx < 0 || idx >= panels.size()) return false;

        ChatPanel panel = panels.get(idx);

        // Tabs button
        if (mx >= lx + 282 && mx < lx + 318) {
            LzClient.mc().setScreen(new PanelTabsScreen(this, panel));
            return true;
        }

        // Edit button
        if (mx >= lx + 322 && mx < lx + 358) {
            LzClient.mc().setScreen(PanelEditScreen.create(this, panel));
            return true;
        }

        // Del button (index >= 2 only)
        if (idx >= 2 && mx >= lx + 362 && mx < lx + 398) {
            if (TabListScreen.quickDeleteMode || LzKeys.isCtrlDown()) {
                doRemovePanel(panel);
            } else {
                LzClient.mc().setScreen(new DeleteConfirmScreen(this,
                    "Delete panel \"" + panel.name() + "\"?",
                    () -> doRemovePanel(panel)));
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (scrollDragging) {
            int max    = maxScroll();
            int trackH = listBottom() - LIST_TOP;
            int totalH = panels().size() * ROW_HEIGHT;
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
        if (keyCode == Keys.ESCAPE) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void doRemovePanel(ChatPanel panel) {
        int id = panel.id();
        ChatManager.getTabs().forEach(t -> t.visibleOnPanels().remove(id));
        ChatManager.getAllPanels().remove(panel);
        MultichatClient.saveTabsToConfig();
        clearAndInit();
    }

}
