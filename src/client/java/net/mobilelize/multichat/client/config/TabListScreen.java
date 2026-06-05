package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzTextField;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.text.LzFormatting;
import net.mobilelize.fabriclib.text.LzStyle;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.Keys;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.quickaction.QuickActionsEditScreen;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.Collections;
import java.util.List;

public class TabListScreen extends LzScreen {

    private static final int LIST_TOP   = 32;
    private static final int ROW_HEIGHT = 22;
    private static final int SB_W       = 6;
    private static final int BTN_W      = 40;
    private static final int BTN_H      = 18;
    private static final int BTN_GAP    = 3;

    private final Screen parent;
    private LzTextField nameField;
    private int     scrollOffset       = 0;
    private boolean scrollDragging     = false;
    private int     scrollDragStartY   = 0;
    private int     scrollDragStartOff = 0;
    private int     selectedIdx        = -1;
    private boolean swapPending        = false;

    public TabListScreen(Screen parent) {
        super("MultiChat — Tabs");
        this.parent = parent;
    }

    private List<ChatTab> allTabs() { return ChatRenderer.TABS; }
    private int listBottom()        { return height - 62; }
    private int maxScroll()         { return Math.max(0, allTabs().size() * ROW_HEIGHT - (listBottom() - LIST_TOP)); }

    private boolean isMoveable(ChatTab tab) {
        return tab.type() == TabType.FILTERED || tab.type() == TabType.API || tab.type() == TabType.SEARCH;
    }

    private int firstMoveableIdx() {
        List<ChatTab> tabs = allTabs();
        for (int i = 0; i < tabs.size(); i++)
            if (isMoveable(tabs.get(i))) return i;
        return tabs.size();
    }

    @Override
    protected void init() {
        scrollOffset = Math.min(scrollOffset, maxScroll());
        if (selectedIdx >= allTabs().size()) selectedIdx = -1;

        nameField = new LzTextField(width / 2 - 100, height - 50, 158, 20);
        nameField.setPlaceholder(LzText.literal("New tab name...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
        this.addSelectableChild(nameField);

        addDrawableChild(LzButton.of("Add",      width / 2 + 62,  height - 50, 40, 20, b -> addTab()));
        addDrawableChild(LzButton.of("Panels",        width / 2 - 102, height - 26, 62, 20, b -> LzClient.mc().setScreen(new PanelListScreen(this))));
        addDrawableChild(LzButton.of("Settings",      width / 2 - 36,  height - 26, 72, 20, b -> LzClient.mc().setScreen(ConfigMenu.create(this))));
        addDrawableChild(LzButton.of("Done",          width / 2 + 40,  height - 26, 62, 20, b -> close()));
        addDrawableChild(LzButton.of("Quick Actions", 4,               height - 26, 90, 20, b -> LzClient.mc().setScreen(new QuickActionsEditScreen(this))));
    }

    private void addTab() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;
        if (ChatRenderer.TABS.stream().anyMatch(t -> t.type() == TabType.FILTERED && t.name().equalsIgnoreCase(name))) return;
        ChatRenderer.TABS.add(new ChatTab(name, TabType.FILTERED, java.util.List.of(0, 1)));
        MultichatClient.saveTabsToConfig();
        clearAndInit();
    }

    private void moveTab(int idx, boolean up) {
        List<ChatTab> tabs = allTabs();
        int target = up ? idx - 1 : idx + 1;
        if (target < firstMoveableIdx() || target >= tabs.size()) return;
        if (!isMoveable(tabs.get(target))) return;
        Collections.swap(tabs, idx, target);
        selectedIdx = target;
        MultichatClient.saveTabsToConfig();
        scrollToSelected();
    }

    private void scrollToSelected() {
        if (selectedIdx < 0) return;
        int rowTop = LIST_TOP + selectedIdx * ROW_HEIGHT;
        int rowBot = rowTop + ROW_HEIGHT;
        if (rowTop - scrollOffset < LIST_TOP)
            scrollOffset = rowTop - LIST_TOP;
        else if (rowBot - scrollOffset > listBottom())
            scrollOffset = rowBot - listBottom();
        scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset));
    }

    // Control button bounds — to the LEFT of the row list, vertically centered
    private int ctrlBtnX1() { return width / 2 - 130 - BTN_W - 4; }
    private int ctrlBtnX2() { return width / 2 - 130 - 4; }
    private int ctrlBtnCx() { return (ctrlBtnX1() + ctrlBtnX2()) / 2; }

    private int ctrlUpY1()   { int cy = (LIST_TOP + listBottom()) / 2; return cy - BTN_H - BTN_GAP - BTN_H / 2; }
    private int ctrlUpY2()   { return ctrlUpY1() + BTN_H; }
    private int ctrlDnY1()   { return ctrlUpY2() + BTN_GAP; }
    private int ctrlDnY2()   { return ctrlDnY1() + BTN_H; }
    private int ctrlSwapY1() { return ctrlDnY2() + BTN_GAP; }
    private int ctrlSwapY2() { return ctrlSwapY1() + BTN_H; }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        super.lzRender(ctx, mouseX, mouseY, delta);
        LzDraw.drawCenteredText(ctx, LzText.wrapMc(title), width / 2, 12, 0xFFFFFF);
        nameField.render(ctx.mc(), mouseX, mouseY, delta);

        int lx = width / 2 - 130;

        // --- Control buttons (left of list) ---
        int cx1 = ctrlBtnX1(), cx2 = ctrlBtnX2(), cxMid = ctrlBtnCx();
        int uy1 = ctrlUpY1(),   uy2 = ctrlUpY2();
        int dy1 = ctrlDnY1(),   dy2 = ctrlDnY2();
        int sy1 = ctrlSwapY1(), sy2 = ctrlSwapY2();

        List<ChatTab> tabs = allTabs();
        boolean hasSelected = selectedIdx >= 0 && selectedIdx < tabs.size()
                              && isMoveable(tabs.get(selectedIdx));
        boolean canUp   = hasSelected && selectedIdx > firstMoveableIdx();
        boolean canDown = hasSelected && selectedIdx < tabs.size() - 1
                          && isMoveable(tabs.get(selectedIdx + 1));

        boolean hoverUp   = mouseX >= cx1 && mouseX < cx2 && mouseY >= uy1 && mouseY < uy2;
        boolean hoverDown = mouseX >= cx1 && mouseX < cx2 && mouseY >= dy1 && mouseY < dy2;
        boolean hoverSwap = mouseX >= cx1 && mouseX < cx2 && mouseY >= sy1 && mouseY < sy2;

        int upBg   = canUp   ? (hoverUp   ? 0xAA888888 : 0x66555555) : 0x33333333;
        int dnBg   = canDown ? (hoverDown ? 0xAA888888 : 0x66555555) : 0x33333333;
        int swapBg = swapPending ? 0xAA4455FF
                   : (hasSelected ? (hoverSwap ? 0xAA888888 : 0x66555555) : 0x33333333);

        LzDraw.fill(ctx, cx1, uy1, cx2, uy2, upBg);
        LzDraw.drawBorder(ctx, cx1, uy1, cx2, uy2, 0x88666666);
        LzDraw.drawCenteredText(ctx, LzText.literal("↑"), cxMid, uy1 + (BTN_H - 8) / 2, canUp ? 0xFFFFFF : 0x555555);

        LzDraw.fill(ctx, cx1, dy1, cx2, dy2, dnBg);
        LzDraw.drawBorder(ctx, cx1, dy1, cx2, dy2, 0x88666666);
        LzDraw.drawCenteredText(ctx, LzText.literal("↓"), cxMid, dy1 + (BTN_H - 8) / 2, canDown ? 0xFFFFFF : 0x555555);

        LzDraw.fill(ctx, cx1, sy1, cx2, sy2, swapBg);
        LzDraw.drawBorder(ctx, cx1, sy1, cx2, sy2, 0x88666666);
        LzDraw.drawCenteredText(ctx, LzText.literal("Swap"), cxMid, sy1 + (BTN_H - 8) / 2, hasSelected ? 0xFFFFFF : 0x555555);

        // --- Tab rows ---
        LzDraw.enableScissor(ctx, 0, LIST_TOP, width, listBottom());

        for (int i = 0; i < tabs.size(); i++) {
            ChatTab tab = tabs.get(i);
            int y = LIST_TOP + i * ROW_HEIGHT - scrollOffset;
            if (y + ROW_HEIGHT <= LIST_TOP || y >= listBottom()) continue;

            int rowY1 = y + 1, rowY2 = y + ROW_HEIGHT - 1;
            int btnY1 = y + 3, btnY2 = y + ROW_HEIGHT - 3;
            int textY = y + (ROW_HEIGHT - 8) / 2;

            int rowBg = switch (tab.type()) {
                case FILTERED -> 0x55FFFFFF;
                case SEARCH   -> 0x4400FFAA;
                case API      -> 0x44FF9900;
                default       -> 0x44AAAAFF;
            };
            int rowBdr = switch (tab.type()) {
                case FILTERED -> 0x88AAAAAA;
                case SEARCH   -> 0x8800CC88;
                case API      -> 0x88CC6600;
                default       -> 0x886688CC;
            };
            LzDraw.fill(ctx, lx, rowY1, lx + 260, rowY2, rowBg);
            LzDraw.drawBorder(ctx, lx, rowY1, lx + 260, rowY2, rowBdr);

            // Selection highlight
            if (i == selectedIdx) {
                LzDraw.fill(ctx, lx, rowY1, lx + 260, rowY2, swapPending ? 0x553366FF : 0x55FFFF44);
            } else if (swapPending && isMoveable(tab)
                    && mouseX >= lx && mouseX < lx + 260 && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                LzDraw.fill(ctx, lx, rowY1, lx + 260, rowY2, 0x3300FFAA);
            }

            int nameColor = switch (tab.type()) {
                case FILTERED -> 0xFFFFFF;
                case SEARCH   -> 0xAAFFDD;
                case API      -> 0xFFCC88;
                default       -> 0xAADDFF;
            };

            // Name
            String nameStr = tab.name() + " ";
            LzDraw.drawText(ctx, nameStr, lx + 6, textY, nameColor);

            int infoX = lx + 6 + LzDraw.textWidth(nameStr);

            // Tab color swatch
            if (tab.tabColor() != 0) {
                LzDraw.fill(ctx, infoX, textY + 1, infoX + 7, textY + 7, 0xFF000000 | (tab.tabColor() & 0xFFFFFF));
                infoX += 10;
            }

            if (tab.type() == TabType.FILTERED || tab.type() == TabType.API) {
                int wlCount = tab.filterRules().whitelist().size();
                int blCount = tab.filterRules().blacklist().size();

                String wlStr = "WL:" + wlCount + " ";
                String blStr = "BL:" + blCount;
                LzDraw.drawText(ctx, wlStr, infoX, textY, wlCount > 0 ? 0xFF88FF88 : 0xFF666666);
                infoX += LzDraw.textWidth(wlStr);
                LzDraw.drawText(ctx, blStr, infoX, textY, blCount > 0 ? 0xFFFF8888 : 0xFF666666);
                infoX += LzDraw.textWidth(blStr);

                // Settings flags: T=timestamps C=collapse D=diff E=exception B=badge-off P=prefix S=sound-ping
                StringBuilder flags = new StringBuilder();
                if (tab.showTimestamps())           flags.append("T");
                if (tab.collapseRepeating())        flags.append("C");
                if (tab.differenceMode()) {
                    flags.append("D");
                    if (tab.differenceLayerIndex() != 0) flags.append(tab.differenceLayerIndex());
                }
                if (tab.differenceException())      flags.append("E");
                if (!tab.showBadge())               flags.append("B");
                if (!tab.contextPrefix().isEmpty()) flags.append("P");
                if (tab.pingEnabled())              flags.append("S");
                if (!flags.isEmpty())
                    LzDraw.drawText(ctx, "  " + flags, infoX, textY, 0xFF9999CC);
            } else {
                String typeTag = switch (tab.type()) {
                    case ALL     -> "[all]";
                    case VANILLA -> "[vanilla]";
                    case SEARCH  -> "[search]";
                    default      -> "";
                };
                if (!typeTag.isEmpty())
                    LzDraw.drawText(ctx, typeTag, infoX, textY, 0xFF666666);
            }

            boolean canEdit   = tab.type() == TabType.FILTERED || tab.type() == TabType.API;
            boolean canRemove = tab.type() == TabType.FILTERED || tab.type() == TabType.SEARCH;
            boolean hoverEdit   = canEdit   && mouseX >= lx + 186 && mouseX < lx + 228 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            boolean hoverRemove = canRemove && mouseX >= lx + 231 && mouseX < lx + 259 && mouseY >= y && mouseY < y + ROW_HEIGHT;

            LzDraw.fill(ctx, lx + 186, btnY1, lx + 228, btnY2, canEdit ? (hoverEdit ? 0xAA888888 : 0x66555555) : 0x33333333);
            LzDraw.drawCenteredText(ctx, LzText.literal("Edit"), lx + 207, textY, canEdit ? 0xFFFFFF : 0x555555);

            if (canRemove) {
                LzDraw.fill(ctx, lx + 231, btnY1, lx + 259, btnY2, hoverRemove ? 0xAAFF4444 : 0x66882222);
                LzDraw.drawCenteredText(ctx, LzText.literal("X"), lx + 245, textY, 0xFFFFFF);
            }
        }

        LzDraw.disableScissor(ctx);

        // Scrollbar
        int max = maxScroll();
        if (max > 0) {
            int sbX    = lx + 265;
            int trackH = listBottom() - LIST_TOP;
            int totalH = tabs.size() * ROW_HEIGHT;
            int thumbH = Math.max(20, trackH * trackH / totalH);
            int thumbY = LIST_TOP + (int)((long) scrollOffset * (trackH - thumbH) / max);

            LzDraw.fill(ctx, sbX, LIST_TOP, sbX + SB_W, listBottom(), 0x44FFFFFF);
            LzDraw.fill(ctx, sbX + 1, thumbY, sbX + SB_W - 1, thumbY + thumbH,
                scrollDragging ? 0xFFDDDDDD : 0xBBAAAAAA);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Keys.ESCAPE && swapPending) {
            swapPending = false;
            return true;
        }
        if (selectedIdx >= 0 && selectedIdx < allTabs().size()
                && isMoveable(allTabs().get(selectedIdx))) {
            if (keyCode == Keys.UP)   { moveTab(selectedIdx, true);  return true; }
            if (keyCode == Keys.DOWN) { moveTab(selectedIdx, false); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        int lx = width / 2 - 130;

        // Control buttons
        if (button == 0 && mx >= ctrlBtnX1() && mx < ctrlBtnX2()) {
            List<ChatTab> tabs = allTabs();
            boolean hasSelected = selectedIdx >= 0 && selectedIdx < tabs.size()
                                  && isMoveable(tabs.get(selectedIdx));
            if (my >= ctrlUpY1() && my < ctrlUpY2()) {
                if (hasSelected) moveTab(selectedIdx, true);
                return true;
            }
            if (my >= ctrlDnY1() && my < ctrlDnY2()) {
                if (hasSelected) moveTab(selectedIdx, false);
                return true;
            }
            if (my >= ctrlSwapY1() && my < ctrlSwapY2()) {
                if (hasSelected) swapPending = !swapPending;
                return true;
            }
        }

        // Scrollbar
        int lxSb = lx + 265;
        int max  = maxScroll();
        if (button == 0 && max > 0 && mx >= lxSb && mx < lxSb + SB_W && my >= LIST_TOP && my < listBottom()) {
            int trackH = listBottom() - LIST_TOP;
            int totalH = allTabs().size() * ROW_HEIGHT;
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

        int relY = (int)(my - LIST_TOP) + scrollOffset;
        int idx  = relY / ROW_HEIGHT;
        List<ChatTab> tabs = allTabs();
        if (idx < 0 || idx >= tabs.size()) return false;

        ChatTab tab = tabs.get(idx);

        // Edit button
        if (mx >= lx + 186 && mx < lx + 228) {
            if (tab.type() == TabType.FILTERED || tab.type() == TabType.API)
                LzClient.mc().setScreen(TabEditScreen.create(this, tab));
            return true;
        }

        // Remove button
        if ((tab.type() == TabType.FILTERED || tab.type() == TabType.SEARCH) && mx >= lx + 231 && mx < lx + 259) {
            if (idx == selectedIdx) selectedIdx = -1;
            else if (idx < selectedIdx) selectedIdx--;
            swapPending = false;
            ChatRenderer.TABS.remove(tab);
            if (tab.type() == TabType.FILTERED) MultichatClient.saveTabsToConfig();
            clearAndInit();
            return true;
        }

        // Row click — select / swap
        if (button == 0) {
            if (swapPending && idx != selectedIdx && isMoveable(tab)) {
                Collections.swap(tabs, selectedIdx, idx);
                selectedIdx = idx;
                swapPending = false;
                MultichatClient.saveTabsToConfig();
                scrollToSelected();
            } else {
                swapPending = false;
                selectedIdx = (idx == selectedIdx || !isMoveable(tab)) ? -1 : idx;
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
            int totalH = allTabs().size() * ROW_HEIGHT;
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
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        if (my >= LIST_TOP && my < listBottom()) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll(), scrollOffset - verticalAmount * ROW_HEIGHT));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() { LzClient.mc().setScreen(parent); }
}
