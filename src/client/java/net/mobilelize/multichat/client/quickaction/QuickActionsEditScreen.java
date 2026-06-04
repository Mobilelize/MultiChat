package net.mobilelize.multichat.client.quickaction;

import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.client.LzTextField;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzFormatting;
import net.mobilelize.fabriclib.text.LzStyle;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.Keys;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.config.MultiChatConfig;

import java.util.Collections;
import java.util.List;

public class QuickActionsEditScreen extends LzScreen {

    private static final int LIST_TOP = 36;
    private static final int ROW_H    = 20;
    private static final int DEL_W    = 28;
    private static final int EDIT_W   = 34;
    private static final int BTN_H    = 18;
    private static final int BTN_W    = 40;
    private static final int BTN_GAP  = 3;

    private final Screen parent;
    private LzTextField nameField;
    private LzTextField templateField;
    private int     scrollOffset = 0;
    private int     editingIdx   = -1;
    private int     selectedIdx  = -1;
    private boolean swapPending  = false;

    public QuickActionsEditScreen(Screen parent) {
        super("MultiChat — Quick Actions");
        this.parent = parent;
    }

    private List<MultiChatConfig.QuickActionConfig> actions() { return MultichatClient.CONFIG.quickActions; }
    private int listBottom() { return height - 96; }
    private int maxScroll()  { return Math.max(0, actions().size() * ROW_H - (listBottom() - LIST_TOP)); }
    private int lx1()        { return width / 2 - 200; }
    private int lx2()        { return width / 2 + 200; }

    // Control button column — left of list
    private int cbX1() { return lx1() - BTN_W - 6; }
    private int cbX2() { return lx1() - 6; }
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
            int rowY = LIST_TOP + editingIdx * ROW_H - scrollOffset;
            nameField = new LzTextField(lx1 + 2, rowY + 1, 130, ROW_H - 2);
            nameField.setPlaceholder(LzText.literal("Name...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
            templateField = new LzTextField(lx1 + 136, rowY + 1, 262, ROW_H - 2);
            templateField.setPlaceholder(LzText.literal("Template...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
            var a = actions().get(editingIdx);
            nameField.setText(a.name);
            templateField.setText(a.template);
            addSelectableChild(nameField);
            addSelectableChild(templateField);
            setFocused(nameField);

            addDrawableChild(LzButton.of("Save",   lx1 + 2,  height - 76, 80, 18, b -> commit()));
            addDrawableChild(LzButton.of("Cancel", lx1 + 86, height - 76, 80, 18, b -> {
                editingIdx = -1; clearChildren(); init();
            }));
        } else {
            nameField = new LzTextField(lx1 + 2, height - 80, 148, 18);
            nameField.setPlaceholder(LzText.literal("Name...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
            templateField = new LzTextField(lx1 + 154, height - 80, 244, 18);
            templateField.setPlaceholder(LzText.literal("Template — use {selected}...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
            addSelectableChild(nameField);
            addSelectableChild(templateField);
            addDrawableChild(LzButton.of("Add", lx1 + 2, height - 56, 60, 18, b -> commit()));
        }

        addDrawableChild(LzButton.of("Done", lx2 - 50, height - 26, 50, 20, b -> close()));
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
    public void lzRenderBackground(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        LzDraw.fill(ctx, 0, 0, width, height, 0xBB000000);
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        super.lzRender(ctx, mouseX, mouseY, delta);

        nameField.render(ctx.mc(), mouseX, mouseY, delta);
        templateField.render(ctx.mc(), mouseX, mouseY, delta);

        LzDraw.drawCenteredText(ctx, LzText.literal("Quick Actions"), width / 2, 10, 0xFFFFCC44);
        LzDraw.drawCenteredText(ctx, LzText.format("&7Use &e{selected}&7 in templates — replaced with selected chat text"), width / 2, 23, 0xFFFFFFFF);

        int lx1 = lx1(), lx2 = lx2();
        LzDraw.fill(ctx, lx1, LIST_TOP, lx2, listBottom(), 0x44000000);
        LzDraw.drawBorder(ctx, lx1, LIST_TOP, lx2, listBottom(), 0xFF444444);

        // Scrollbar
        int max = maxScroll();
        if (max > 0) {
            int sbX    = lx2 + 2;
            int trackH = listBottom() - LIST_TOP;
            int totalPx = actions().size() * ROW_H;
            int thumbH = Math.max(10, trackH * trackH / totalPx);
            int thumbY = LIST_TOP + (int)((long) scrollOffset * (trackH - thumbH) / max);
            LzDraw.fill(ctx, sbX, LIST_TOP, sbX + 4, listBottom(), 0x33FFFFFF);
            LzDraw.fill(ctx, sbX + 1, thumbY, sbX + 3, thumbY + thumbH, 0xBBAAAAAA);
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

            if (!editing) {
                LzDraw.drawText(ctx, LzText.literal(a.name), lx1 + 4, ry + 5, 0xFFFFFFFF);
                String tplStr = a.template.length() > 36 ? a.template.substring(0, 34) + "..." : a.template;
                LzDraw.drawText(ctx, LzText.literal(tplStr), lx1 + 112, ry + 5, 0xFF999999);

                int dx = lx2 - DEL_W - 2;
                boolean dHov = mouseX >= dx && mouseX < dx + DEL_W && mouseY >= ry + 3 && mouseY < ry + 3 + BTN_H;
                LzDraw.fill(ctx, dx, ry + 3, dx + DEL_W, ry + 3 + BTN_H, dHov ? 0xAAFF3333 : 0x44FF3333);
                LzDraw.drawBorder(ctx, dx, ry + 3, dx + DEL_W, ry + 3 + BTN_H, 0x88FF5555);
                LzDraw.drawCenteredText(ctx, LzText.literal("Del"), dx + DEL_W / 2, ry + 5, 0xFFFF8888);

                int ex = dx - EDIT_W - 2;
                boolean eHov = mouseX >= ex && mouseX < ex + EDIT_W && mouseY >= ry + 3 && mouseY < ry + 3 + BTN_H;
                LzDraw.fill(ctx, ex, ry + 3, ex + EDIT_W, ry + 3 + BTN_H, eHov ? 0xAAFFCC44 : 0x44FFCC44);
                LzDraw.drawBorder(ctx, ex, ry + 3, ex + EDIT_W, ry + 3 + BTN_H, 0x88FFCC44);
                LzDraw.drawCenteredText(ctx, LzText.literal("Edit"), ex + EDIT_W / 2, ry + 5, 0xFFFFCC44);
            }
        }
        LzDraw.disableScissor(ctx);

        if (list.isEmpty()) {
            LzDraw.drawCenteredText(ctx, LzText.literal("No actions yet. Add one below."),
                width / 2, (LIST_TOP + listBottom()) / 2 - 4, 0xFF666666);
        }

        if (editingIdx < 0) {
            LzDraw.drawText(ctx, LzText.literal("Name"),     lx1 + 2,   height - 90, 0xFFAAAAAA);
            LzDraw.drawText(ctx, LzText.literal("Template"), lx1 + 154, height - 90, 0xFFAAAAAA);
        } else {
            LzDraw.drawCenteredText(ctx, LzText.format("&7Enter to save  ·  Esc to cancel edit"), width / 2, height - 44, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

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
            int dx = lx2 - DEL_W - 2;
            if (mouseX >= dx && mouseX < dx + DEL_W && mouseY >= ry + 3 && mouseY < ry + 3 + BTN_H) {
                list.remove(i);
                if (editingIdx > i)  editingIdx--;
                if (selectedIdx > i) selectedIdx--;
                else if (selectedIdx == i) selectedIdx = -1;
                swapPending = false;
                MultichatClient.saveTabsToConfig();
                clearChildren(); init();
                return true;
            }

            // Edit button
            int ex = dx - EDIT_W - 2;
            if (mouseX >= ex && mouseX < ex + EDIT_W && mouseY >= ry + 3 && mouseY < ry + 3 + BTN_H) {
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
    public boolean mouseScrolled(double mouseX, double mouseY, double hDelta, double vDelta) {
        if (editingIdx >= 0) return true;
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

    @Override
    public void close() {
        MultichatClient.saveTabsToConfig();
        LzClient.mc().setScreen(parent);
    }
}
