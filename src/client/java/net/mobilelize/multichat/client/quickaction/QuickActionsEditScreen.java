package net.mobilelize.multichat.client.quickaction;

import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzTextField;
import net.mobilelize.fabriclib.input.LzKeys;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzFormatting;
import net.mobilelize.fabriclib.text.LzStyle;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.Keys;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.config.*;

import java.util.Collections;
import java.util.List;

public class QuickActionsEditScreen extends ConfigScreen {

    private static final int LIST_TOP = 36;
    private static final int ROW_H    = 20;
    private static final int ACT_W    = 36;
    private static final int BTN_H    = 18;
    private static final int BTN_W    = 40;
    private static final int BTN_GAP  = 3;

    private LzTextField nameField;
    private LzTextField templateField;
    private int     scrollOffset      = 0;
    private boolean scrollDragging    = false;
    private int     scrollDragStartY  = 0;
    private int     scrollDragStartOff = 0;
    private int     editingIdx        = -1;
    private int     selectedIdx       = -1;
    private boolean swapPending       = false;

    public QuickActionsEditScreen(Screen parent) {
        super("MultiChat — Quick Actions", parent);
    }

    private List<MultiChatConfig.QuickActionConfig> actions() { return MultichatClient.CONFIG.quickActions; }
    private int listBottom() { return height - 80; }
    private int maxScroll()  { return Math.max(0, actions().size() * ROW_H - (listBottom() - LIST_TOP)); }
    private int lx1()        { return width / 2 - 200; }
    private int lx2()        { return width / 2 + 200; }

    // Control button column — left of list
    private int cbX1() { return lx1() - BTN_W - 4; }
    private int cbX2() { return lx1() - 4; }
    private int cbCx() { return (cbX1() + cbX2()) / 2; }
    private int cbUpY1()   { int cy = (LIST_TOP + listBottom()) / 2; return cy - BTN_H - BTN_GAP - BTN_H / 2; }
    private int cbUpY2()   { return cbUpY1() + BTN_H; }
    private int cbDnY1()   { return cbUpY2() + BTN_GAP; }
    private int cbDnY2()   { return cbDnY1() + BTN_H; }
    private int cbSwY1()   { return cbDnY2() + BTN_GAP; }
    private int cbSwY2()   { return cbSwY1() + BTN_H; }

    @Override
    protected void init() {
        scrollOffset = Math.min(scrollOffset, maxScroll());
        if (editingIdx >= actions().size()) editingIdx = -1;
        if (selectedIdx >= actions().size()) selectedIdx = -1;

        int lx1 = lx1();
        int lx2 = lx2();

        if (editingIdx >= 0) {
            nameField = new LzTextField(lx1 + 2, height - 74, 80, 20);
            nameField.setPlaceholder(LzText.literal("Name...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
            templateField = new LzTextField(lx1 + 86, height - 74, 224, 20);
            templateField.setPlaceholder(LzText.literal("Template...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
            var a = actions().get(editingIdx);
            nameField.setText(a.name);
            templateField.setText(a.template);
            addSelectableChild(nameField);
            addSelectableChild(templateField);
            setFocused(nameField);

            addDrawableChild(LzButton.of("Cancel", lx1 + 314, height - 74, 40, 20, b -> {
                editingIdx = -1; clearChildren(); init();
            }));
            addDrawableChild(LzButton.of("Save", lx1 + 358, height - 74, 40, 20, b -> commit()));
        } else {
            nameField = new LzTextField(lx1 + 2, height - 74, 80, 20);
            nameField.setPlaceholder(LzText.literal("Name...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
            templateField = new LzTextField(lx1 + 86, height - 74, 264, 20);
            templateField.setPlaceholder(LzText.literal("Command — use {selected}...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
            addSelectableChild(nameField);
            addSelectableChild(templateField);
            addDrawableChild(LzButton.of("Add", lx1 + 354, height - 74, 40, 20, b -> commit()));
        }

        addConfigNav(width, height, NavPage.QUICK_ACTIONS);
    }

    private void move(int idx, boolean up) {
        List<MultiChatConfig.QuickActionConfig> list = actions();
        int target = up ? idx - 1 : idx + 1;
        if (target < 0 || target >= list.size()) return;
        Collections.swap(list, idx, target);
        selectedIdx = target;
        MultichatClient.saveTabsToConfig();
        scrollToSelected();
    }

    private void scrollToSelected() {
        if (selectedIdx < 0) return;
        int rowTop = LIST_TOP + selectedIdx * ROW_H;
        int rowBot = rowTop + ROW_H;
        if (rowTop - scrollOffset < LIST_TOP)       scrollOffset = rowTop - LIST_TOP;
        else if (rowBot - scrollOffset > listBottom()) scrollOffset = rowBot - listBottom();
        scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset));
    }

    private void commit() {
        String name = nameField.getText().trim();
        String tpl  = templateField.getText().trim();
        if (name.isEmpty() || tpl.isEmpty()) return;
        if (editingIdx >= 0 && editingIdx < actions().size()) {
            var a  = actions().get(editingIdx);
            a.name     = name;
            a.template = tpl;
            editingIdx = -1;
        } else {
            var cfg = new MultiChatConfig.QuickActionConfig();
            cfg.name     = name;
            cfg.template = tpl;
            actions().add(cfg);
        }
        MultichatClient.saveTabsToConfig();
        clearChildren();
        init();
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        super.lzRender(ctx, mouseX, mouseY, delta);

        nameField.render(ctx.mc(), mouseX, mouseY, delta);
        templateField.render(ctx.mc(), mouseX, mouseY, delta);

        LzDraw.drawCenteredText(ctx, LzText.literal("Quick Actions"), width / 2, 10, 0xFFFFCC44);
        if (editingIdx >= 0)
            LzDraw.drawCenteredText(ctx, LzText.format("&fEnter &7to save  ·  &fEsc &7to cancel"), width / 2, 23, 0xFFFFFFFF);
        else if (swapPending)
            LzDraw.drawCenteredText(ctx, LzText.format("&bSwap mode&7: click target action  ·  &fEsc &7to cancel"), width / 2, 23, 0xFFFFFFFF);
        else
            LzDraw.drawCenteredText(ctx, LzText.format("&7Use &e{selected}&7 in templates — replaced with selected chat text"), width / 2, 23, 0xFFFFFFFF);

        int lx1 = lx1(), lx2 = lx2();
        LzDraw.fill(ctx, lx1, LIST_TOP, lx2, listBottom(), 0x44000000);
        LzDraw.drawBorder(ctx, lx1, LIST_TOP, lx2, listBottom(), 0xFF444444);

        // Scrollbar
        int max = maxScroll();
        if (max > 0) {
            int sbX    = lx2 + 5;
            int trackH = listBottom() - LIST_TOP;
            int totalPx = actions().size() * ROW_H;
            int thumbH = Math.max(10, trackH * trackH / totalPx);
            int thumbY = LIST_TOP + (int)((long) scrollOffset * (trackH - thumbH) / max);
            LzDraw.fill(ctx, sbX, LIST_TOP, sbX + SB_W, listBottom(), 0x44FFFFFF);
            LzDraw.drawBorder(ctx, sbX, LIST_TOP, sbX + SB_W, listBottom(), 0x88666666);
            LzDraw.fill(ctx, sbX + 1, thumbY, sbX + SB_W - 1, thumbY + thumbH,
                scrollDragging ? 0xFFDDDDDD : 0xBBAAAAAA);
        }

        // Control buttons
        boolean hasSel  = selectedIdx >= 0 && selectedIdx < actions().size() && editingIdx < 0;
        boolean canUp   = hasSel && selectedIdx > 0;
        boolean canDown = hasSel && selectedIdx < actions().size() - 1;

        int cx1 = cbX1(), cx2 = cbX2(), cxM = cbCx();
        boolean hUp   = mouseX >= cx1 && mouseX < cx2 && mouseY >= cbUpY1() && mouseY < cbUpY2();
        boolean hDn   = mouseX >= cx1 && mouseX < cx2 && mouseY >= cbDnY1() && mouseY < cbDnY2();
        boolean hSw   = mouseX >= cx1 && mouseX < cx2 && mouseY >= cbSwY1() && mouseY < cbSwY2();

        LzDraw.fill(ctx, cx1, cbUpY1(), cx2, cbUpY2(), canUp   ? (hUp ? 0xAA888888 : 0x66555555) : 0x33333333);
        LzDraw.drawBorder(ctx, cx1, cbUpY1(), cx2, cbUpY2(), 0x88666666);
        LzDraw.drawCenteredText(ctx, LzText.literal("↑"), cxM, cbUpY1() + (BTN_H - 8) / 2, canUp ? 0xFFFFFF : 0x555555);

        LzDraw.fill(ctx, cx1, cbDnY1(), cx2, cbDnY2(), canDown ? (hDn ? 0xAA888888 : 0x66555555) : 0x33333333);
        LzDraw.drawBorder(ctx, cx1, cbDnY1(), cx2, cbDnY2(), 0x88666666);
        LzDraw.drawCenteredText(ctx, LzText.literal("↓"), cxM, cbDnY1() + (BTN_H - 8) / 2, canDown ? 0xFFFFFF : 0x555555);

        int swapBg = swapPending ? 0xAA4455FF : (hasSel ? (hSw ? 0xAA888888 : 0x66555555) : 0x33333333);
        LzDraw.fill(ctx, cx1, cbSwY1(), cx2, cbSwY2(), swapBg);
        LzDraw.drawBorder(ctx, cx1, cbSwY1(), cx2, cbSwY2(), 0x88666666);
        LzDraw.drawCenteredText(ctx, LzText.literal("Swap"), cxM, cbSwY1() + (BTN_H - 8) / 2, hasSel ? 0xFFFFFF : 0x555555);

        // Rows
        List<MultiChatConfig.QuickActionConfig> list = actions();
        LzDraw.enableScissor(ctx, lx1, LIST_TOP, lx2, listBottom());
        for (int i = 0; i < list.size(); i++) {
            int ry = LIST_TOP + i * ROW_H - scrollOffset;
            if (ry + ROW_H <= LIST_TOP || ry >= listBottom()) continue;

            var a       = list.get(i);
            boolean editing = editingIdx == i;
            boolean sel     = selectedIdx == i && !editing;
            boolean hov = !editing && !sel && mouseX >= lx1 && mouseX < lx2 && mouseY >= ry && mouseY < ry + ROW_H;

            if (editing)  LzDraw.fill(ctx, lx1, ry, lx2, ry + ROW_H, 0x55FFCC44);
            else if (sel) LzDraw.fill(ctx, lx1, ry, lx2, ry + ROW_H, swapPending ? 0x553366FF : 0x55FFFF44);
            else if (hov) {
                int hovCol = swapPending ? 0x3300FFAA : 0x22FFFFFF;
                LzDraw.fill(ctx, lx1, ry, lx2, ry + ROW_H, hovCol);
            }

            LzDraw.drawText(ctx, LzText.literal(a.name), lx1 + 4, ry + 5, 0xFFFFFFFF);
            String tplStr = a.template.length() > 36 ? a.template.substring(0, 34) + "..." : a.template;
            LzDraw.drawText(ctx, LzText.literal(tplStr), lx1 + 112, ry + 5, 0xFF999999);

            if (!editing) {
                int dx = lx2 - ACT_W - 2;
                boolean dHov = mouseX >= dx && mouseX < dx + ACT_W && mouseY >= ry + 2 && mouseY < ry + ROW_H - 2;
                boolean dInst = TabListScreen.quickDeleteMode || LzKeys.isCtrlDown();
                int dBg  = dInst ? (dHov ? 0xAAFF3333 : 0x44FF3333) : (dHov ? 0xAAFF8800 : 0x44884400);
                int dBdr = dInst ? 0x88FF5555 : 0x88FFAA00;
                int dTxt = dInst ? 0xFFFF8888 : 0xFFFFAA44;
                LzDraw.fill(ctx, dx, ry + 2, dx + ACT_W, ry + ROW_H - 2, dBg);
                LzDraw.drawBorder(ctx, dx, ry + 2, dx + ACT_W, ry + ROW_H - 2, dBdr);
                LzDraw.drawCenteredText(ctx, LzText.literal("Del"), dx + ACT_W / 2, ry + (ROW_H - 8) / 2, dTxt);

                int ex = dx - ACT_W - 4;
                boolean eHov = mouseX >= ex && mouseX < ex + ACT_W && mouseY >= ry + 2 && mouseY < ry + ROW_H - 2;
                LzDraw.fill(ctx, ex, ry + 2, ex + ACT_W, ry + ROW_H - 2, eHov ? 0xAAFFCC44 : 0x44FFCC44);
                LzDraw.drawBorder(ctx, ex, ry + 2, ex + ACT_W, ry + ROW_H - 2, 0x88FFCC44);
                LzDraw.drawCenteredText(ctx, LzText.literal("Edit"), ex + ACT_W / 2, ry + (ROW_H - 8) / 2, 0xFFFFCC44);
            }
        }
        LzDraw.disableScissor(ctx);

        if (list.isEmpty()) {
            LzDraw.drawCenteredText(ctx, LzText.literal("No actions yet. Add one below."),
                width / 2, (LIST_TOP + listBottom()) / 2 - 4, 0xFF666666);
        }

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        // Scrollbar
        int sbX = lx2() + 5;
        int max = maxScroll();
        if (max > 0 && mouseX >= sbX && mouseX < sbX + SB_W && mouseY >= LIST_TOP && mouseY < listBottom()) {
            int trackH = listBottom() - LIST_TOP;
            int totalPx = actions().size() * ROW_H;
            int thumbH = Math.max(10, trackH * trackH / totalPx);
            int thumbY = LIST_TOP + (int)((long) scrollOffset * (trackH - thumbH) / max);
            if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                scrollDragging     = true;
                scrollDragStartY   = (int) mouseY;
                scrollDragStartOff = scrollOffset;
            } else {
                int travel = trackH - thumbH;
                if (travel > 0)
                    scrollOffset = Math.max(0, Math.min(max,
                        (int)((mouseY - LIST_TOP - thumbH / 2.0) * max / travel)));
            }
            return true;
        }

        // Control buttons
        if (mouseX >= cbX1() && mouseX < cbX2() && editingIdx < 0) {
            boolean hasSel = selectedIdx >= 0 && selectedIdx < actions().size();
            if (mouseY >= cbUpY1() && mouseY < cbUpY2()) { if (hasSel) move(selectedIdx, true);  return true; }
            if (mouseY >= cbDnY1() && mouseY < cbDnY2()) { if (hasSel) move(selectedIdx, false); return true; }
            if (mouseY >= cbSwY1() && mouseY < cbSwY2()) { if (hasSel) swapPending = !swapPending; return true; }
        }

        int lx2 = lx2();
        List<MultiChatConfig.QuickActionConfig> list = actions();
        for (int i = 0; i < list.size(); i++) {
            if (editingIdx == i) continue;
            int ry = LIST_TOP + i * ROW_H - scrollOffset;
            if (ry + ROW_H <= LIST_TOP || ry >= listBottom()) continue;

            // Del button
            int dx = lx2 - ACT_W - 2;
            if (mouseX >= dx && mouseX < dx + ACT_W && mouseY >= ry + 2 && mouseY < ry + ROW_H - 2) {
                if (TabListScreen.quickDeleteMode || LzKeys.isCtrlDown()) {
                    doRemoveAction(list, i);
                } else {
                    int capturedI = i;
                    LzClient.mc().setScreen(new DeleteConfirmScreen(this,
                        "Delete \"" + list.get(capturedI).name + "\"?",
                        () -> doRemoveAction(list, capturedI)));
                }
                return true;
            }

            // Edit button
            int ex = dx - ACT_W - 4;
            if (mouseX >= ex && mouseX < ex + ACT_W && mouseY >= ry + 2 && mouseY < ry + ROW_H - 2) {
                editingIdx = i;
                swapPending = false;
                clearChildren(); init();
                return true;
            }

            // Row click — select / swap
            if (mouseX >= lx1() && mouseX < lx2 && mouseY >= ry && mouseY < ry + ROW_H && editingIdx < 0) {
                if (swapPending && i != selectedIdx) {
                    Collections.swap(list, selectedIdx, i);
                    selectedIdx = i;
                    swapPending = false;
                    MultichatClient.saveTabsToConfig();
                    scrollToSelected();
                } else {
                    swapPending = false;
                    selectedIdx = (i == selectedIdx) ? -1 : i;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (scrollDragging) {
            int max    = maxScroll();
            int trackH = listBottom() - LIST_TOP;
            int totalPx = actions().size() * ROW_H;
            int thumbH = Math.max(10, trackH * trackH / totalPx);
            int travel = trackH - thumbH;
            if (travel > 0)
                scrollOffset = Math.max(0, Math.min(max,
                    scrollDragStartOff + (int)((mouseY - scrollDragStartY) * max / travel)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hDelta, double vDelta) {
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int)(vDelta * ROW_H), maxScroll()));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingIdx >= 0 && (keyCode == Keys.ENTER || keyCode == Keys.NUMPAD_ENTER)) {
            commit(); return true;
        }
        if (keyCode == Keys.ESCAPE) {
            if (swapPending) { swapPending = false; return true; }
            if (editingIdx >= 0) { editingIdx = -1; clearChildren(); init(); return true; }
            close(); return true;
        }
        if (editingIdx < 0 && selectedIdx >= 0) {
            if (keyCode == Keys.UP)   { move(selectedIdx, true);  return true; }
            if (keyCode == Keys.DOWN) { move(selectedIdx, false); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void doRemoveAction(List<MultiChatConfig.QuickActionConfig> list, int i) {
        list.remove(i);
        if (editingIdx == i)     editingIdx = -1;
        else if (editingIdx > i) editingIdx--;
        if (selectedIdx > i) selectedIdx--;
        else if (selectedIdx == i) selectedIdx = -1;
        swapPending = false;
        MultichatClient.saveTabsToConfig();
        clearChildren(); init();
    }

    @Override
    protected void beforeNavigate() { MultichatClient.saveTabsToConfig(); }

    @Override
    protected void beforeClose() { MultichatClient.saveTabsToConfig(); }
}
