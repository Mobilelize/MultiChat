package net.mobilelize.multichat.client.quickaction;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzTextField;
import net.mobilelize.fabriclib.input.LzKeys;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzFormatting;
import net.mobilelize.fabriclib.text.LzStyle;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.Colors;
import net.mobilelize.multichat.client.Keys;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.config.ConfigScreen;
import net.mobilelize.multichat.client.config.DeleteConfirmScreen;
import net.mobilelize.multichat.client.config.MultiChatConfig;
import net.mobilelize.multichat.client.config.TabListScreen;

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
            nameField.setHint(LzText.literal("Name...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
            templateField = new LzTextField(lx1 + 86, height - 74, 224, 20);
            templateField.setHint(LzText.literal("Template...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
            var a = actions().get(editingIdx);
            nameField.setValue(a.name);
            templateField.setValue(a.template);
            addWidget(nameField);
            addWidget(templateField);
            setFocused(nameField);

            addRenderableWidget(LzButton.of("Cancel", lx1 + 314, height - 74, 40, 20, b -> {
                editingIdx = -1; clearWidgets(); init();
            }));
            addRenderableWidget(LzButton.of("Save", lx1 + 358, height - 74, 40, 20, b -> commit()));
        } else {
            nameField = new LzTextField(lx1 + 2, height - 74, 80, 20);
            nameField.setHint(LzText.literal("Name...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
            templateField = new LzTextField(lx1 + 86, height - 74, 264, 20);
            templateField.setHint(LzText.literal("Command — use {selected}...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
            addWidget(nameField);
            addWidget(templateField);
            addRenderableWidget(LzButton.of("Add", lx1 + 354, height - 74, 40, 20, b -> commit()));
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
        String name = nameField.getValue().trim();
        String tpl  = templateField.getValue().trim();
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
        clearWidgets();
        init();
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        super.lzRender(ctx, mouseX, mouseY, delta);

        nameField.extractRenderState(ctx.mc(), mouseX, mouseY, delta);
        templateField.extractRenderState(ctx.mc(), mouseX, mouseY, delta);

        LzDraw.drawCenteredText(ctx, LzText.literal("Quick Actions"), width / 2, 10, Colors.GOLD);
        if (editingIdx >= 0)
            LzDraw.drawCenteredText(ctx, LzText.format("&fEnter &7to save  ·  &fEsc &7to cancel"), width / 2, 23, Colors.TEXT_WHITE);
        else if (swapPending)
            LzDraw.drawCenteredText(ctx, LzText.format("&bSwap mode&7: click target action  ·  &fEsc &7to cancel"), width / 2, 23, Colors.TEXT_WHITE);
        else
            LzDraw.drawCenteredText(ctx, LzText.format("&7Use &e{selected}&7 in templates — replaced with selected chat text"), width / 2, 23, Colors.TEXT_WHITE);

        int lx1 = lx1(), lx2 = lx2();
        LzDraw.fill(ctx, lx1, LIST_TOP, lx2, listBottom(), Colors.OVERLAY_LIGHT);
        LzDraw.drawBorder(ctx, lx1, LIST_TOP, lx2, listBottom(), Colors.BORDER_DARK);

        // Scrollbar
        int max = maxScroll();
        if (max > 0) {
            int sbX    = lx2 + 5;
            int trackH = listBottom() - LIST_TOP;
            int totalPx = actions().size() * ROW_H;
            int thumbH = Math.max(10, trackH * trackH / totalPx);
            int thumbY = LIST_TOP + (int)((long) scrollOffset * (trackH - thumbH) / max);
            LzDraw.fill(ctx, sbX, LIST_TOP, sbX + SB_W, listBottom(), Colors.SCROLLBAR_TRACK);
            LzDraw.drawBorder(ctx, sbX, LIST_TOP, sbX + SB_W, listBottom(), Colors.BTN_BORDER);
            LzDraw.fill(ctx, sbX + 1, thumbY, sbX + SB_W - 1, thumbY + thumbH,
                scrollDragging ? Colors.SCROLLBAR_THUMB_DRAG : Colors.SCROLLBAR_THUMB);
        }

        // Control buttons
        boolean hasSel  = selectedIdx >= 0 && selectedIdx < actions().size() && editingIdx < 0;
        boolean canUp   = hasSel && selectedIdx > 0;
        boolean canDown = hasSel && selectedIdx < actions().size() - 1;

        int cx1 = cbX1(), cx2 = cbX2(), cxM = cbCx();
        boolean hUp   = mouseX >= cx1 && mouseX < cx2 && mouseY >= cbUpY1() && mouseY < cbUpY2();
        boolean hDn   = mouseX >= cx1 && mouseX < cx2 && mouseY >= cbDnY1() && mouseY < cbDnY2();
        boolean hSw   = mouseX >= cx1 && mouseX < cx2 && mouseY >= cbSwY1() && mouseY < cbSwY2();

        LzDraw.fill(ctx, cx1, cbUpY1(), cx2, cbUpY2(), canUp   ? (hUp ? Colors.BTN_HOVER : Colors.BTN_NORMAL) : Colors.BTN_DISABLED);
        LzDraw.drawBorder(ctx, cx1, cbUpY1(), cx2, cbUpY2(), Colors.BTN_BORDER);
        LzDraw.drawCenteredText(ctx, LzText.literal("↑"), cxM, cbUpY1() + (BTN_H - 8) / 2, canUp ? Colors.TEXT_WHITE : Colors.TEXT_DISABLED);

        LzDraw.fill(ctx, cx1, cbDnY1(), cx2, cbDnY2(), canDown ? (hDn ? Colors.BTN_HOVER : Colors.BTN_NORMAL) : Colors.BTN_DISABLED);
        LzDraw.drawBorder(ctx, cx1, cbDnY1(), cx2, cbDnY2(), Colors.BTN_BORDER);
        LzDraw.drawCenteredText(ctx, LzText.literal("↓"), cxM, cbDnY1() + (BTN_H - 8) / 2, canDown ? Colors.TEXT_WHITE : Colors.TEXT_DISABLED);

        int swapBg = swapPending ? Colors.BTN_SWAP_PENDING : (hasSel ? (hSw ? Colors.BTN_HOVER : Colors.BTN_NORMAL) : Colors.BTN_DISABLED);
        LzDraw.fill(ctx, cx1, cbSwY1(), cx2, cbSwY2(), swapBg);
        LzDraw.drawBorder(ctx, cx1, cbSwY1(), cx2, cbSwY2(), Colors.BTN_BORDER);
        LzDraw.drawCenteredText(ctx, LzText.literal("Swap"), cxM, cbSwY1() + (BTN_H - 8) / 2, hasSel ? Colors.TEXT_WHITE : Colors.TEXT_DISABLED);

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

            if (editing)  LzDraw.fill(ctx, lx1, ry, lx2, ry + ROW_H, Colors.ROW_EDITING);
            else if (sel) LzDraw.fill(ctx, lx1, ry, lx2, ry + ROW_H, swapPending ? Colors.ROW_SWAP_SEL : Colors.ROW_SELECTED);
            else if (hov) {
                int hovCol = swapPending ? Colors.ROW_SWAP_HOVER : Colors.ROW_HOVER;
                LzDraw.fill(ctx, lx1, ry, lx2, ry + ROW_H, hovCol);
            }

            LzDraw.drawText(ctx, LzText.literal(a.name), lx1 + 4, ry + 5, Colors.TEXT_WHITE);
            String tplStr = a.template.length() > 36 ? a.template.substring(0, 34) + "..." : a.template;
            LzDraw.drawText(ctx, LzText.literal(tplStr), lx1 + 112, ry + 5, Colors.TEXT_INACTIVE);

            if (!editing) {
                int dx = lx2 - ACT_W - 2;
                boolean dHov = mouseX >= dx && mouseX < dx + ACT_W && mouseY >= ry + 2 && mouseY < ry + ROW_H - 2;
                boolean dInst = TabListScreen.quickDeleteMode || LzKeys.isCtrlDown();
                int dBg  = dInst ? (dHov ? Colors.BTN_DELETE_INSTANT_HOVER : Colors.BTN_DELETE_INSTANT_BG) : (dHov ? Colors.BTN_DELETE_WARN_HOVER : Colors.BTN_DELETE_WARN_BG);
                int dBdr = dInst ? Colors.BTN_DELETE_INSTANT_BORDER : Colors.BTN_DELETE_WARN_BORDER;
                int dTxt = dInst ? Colors.BTN_DELETE_INSTANT_TEXT : Colors.BTN_DELETE_WARN_TEXT;
                LzDraw.fill(ctx, dx, ry + 2, dx + ACT_W, ry + ROW_H - 2, dBg);
                LzDraw.drawBorder(ctx, dx, ry + 2, dx + ACT_W, ry + ROW_H - 2, dBdr);
                LzDraw.drawCenteredText(ctx, LzText.literal("Del"), dx + ACT_W / 2, ry + (ROW_H - 8) / 2, dTxt);

                int ex = dx - ACT_W - 4;
                boolean eHov = mouseX >= ex && mouseX < ex + ACT_W && mouseY >= ry + 2 && mouseY < ry + ROW_H - 2;
                LzDraw.fill(ctx, ex, ry + 2, ex + ACT_W, ry + ROW_H - 2, eHov ? Colors.BTN_EDIT_HOVER : Colors.BTN_EDIT_BG);
                LzDraw.drawBorder(ctx, ex, ry + 2, ex + ACT_W, ry + ROW_H - 2, Colors.BTN_EDIT_BORDER);
                LzDraw.drawCenteredText(ctx, LzText.literal("Edit"), ex + ACT_W / 2, ry + (ROW_H - 8) / 2, Colors.GOLD);
            }
        }
        LzDraw.disableScissor(ctx);

        if (list.isEmpty()) {
            LzDraw.drawCenteredText(ctx, LzText.literal("No actions yet. Add one below."),
                width / 2, (LIST_TOP + listBottom()) / 2 - 4, Colors.TEXT_DARK_GRAY);
        }

    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean isDoubleClick) {
        double mouseX = click.x(); double mouseY = click.y(); int button = click.button();
        if (super.mouseClicked(click, isDoubleClick)) return true;
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false;

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
                    LzClient.mc().gui.setScreen(new DeleteConfirmScreen(this,
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
                clearWidgets(); init();
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
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        double mouseY = click.y();
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
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        scrollDragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hDelta, double vDelta) {
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int)(vDelta * ROW_H), maxScroll()));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (editingIdx >= 0 && (input.key() == Keys.ENTER || input.key() == Keys.NUMPAD_ENTER)) {
            commit(); return true;
        }
        if (input.key() == Keys.ESCAPE) {
            if (swapPending) { swapPending = false; return true; }
            if (editingIdx >= 0) { editingIdx = -1; clearWidgets(); init(); return true; }
            onClose(); return true;
        }
        if (editingIdx < 0 && selectedIdx >= 0) {
            if (input.key() == Keys.UP)   { move(selectedIdx, true);  return true; }
            if (input.key() == Keys.DOWN) { move(selectedIdx, false); return true; }
        }
        return super.keyPressed(input);
    }

    private void doRemoveAction(List<MultiChatConfig.QuickActionConfig> list, int i) {
        list.remove(i);
        if (editingIdx == i)     editingIdx = -1;
        else if (editingIdx > i) editingIdx--;
        if (selectedIdx > i) selectedIdx--;
        else if (selectedIdx == i) selectedIdx = -1;
        swapPending = false;
        MultichatClient.saveTabsToConfig();
        rebuildWidgets(); init();
    }

    @Override
    protected void beforeNavigate() { MultichatClient.saveTabsToConfig(); }

    @Override
    protected void beforeClose() { MultichatClient.saveTabsToConfig(); }
}
