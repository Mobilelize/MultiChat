package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.client.LzTextField;
import net.mobilelize.fabriclib.input.LzKeys;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzFormatting;
import net.mobilelize.fabriclib.text.LzStyle;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.fabriclib.world.LzWorld;
import net.mobilelize.multichat.client.Keys;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.NoteTab;

import java.util.Collections;
import java.util.List;

public class NoteEditorScreen extends LzScreen {

    private static final int LIST_TOP = 36;
    private static final int ROW_H    = 22;
    private static final int SB_W     = 6;
    private static final int ACT_W    = 36;
    private static final int BTN_H    = 18;
    private static final int BTN_W    = 40;
    private static final int BTN_GAP  = 3;

    private final Screen  parent;
    private final NoteTab noteTab;

    private int     scrollOffset       = 0;
    private boolean scrollDragging     = false;
    private int     scrollDragStartY   = 0;
    private int     scrollDragStartOff = 0;
    private int     editingIdx         = -1;
    private int     selectedIdx        = -1;
    private boolean swapPending        = false;

    private LzTextField editField;

    public NoteEditorScreen(Screen parent, NoteTab noteTab) {
        super("Notes — " + noteTab.name());
        this.parent  = parent;
        this.noteTab = noteTab;
    }

    private List<NoteTab.NoteEntry> notes() { return noteTab.noteEntries(); }
    private int listBottom() { return editingIdx >= 0 ? height - 80 : height - 36; }
    private int maxScroll()  { return Math.max(0, notes().size() * ROW_H - (listBottom() - LIST_TOP)); }
    private int lx1()        { return width / 2 - 200; }
    private int lx2()        { return width / 2 + 200; }

    private int cbX1()   { return lx1() - BTN_W - 4; }
    private int cbX2()   { return lx1() - 4; }
    private int cbCx()   { return (cbX1() + cbX2()) / 2; }
    private int cbUpY1() { int cy = (LIST_TOP + listBottom()) / 2; return cy - BTN_H - BTN_GAP / 2; }
    private int cbUpY2() { return cbUpY1() + BTN_H; }
    private int cbDnY1() { return cbUpY2() + BTN_GAP; }
    private int cbDnY2() { return cbDnY1() + BTN_H; }
    private int cbSwY1() { return cbDnY2() + BTN_GAP; }
    private int cbSwY2() { return cbSwY1() + BTN_H; }

    @Override
    protected void init() {
        scrollOffset = Math.min(scrollOffset, maxScroll());
        if (editingIdx >= notes().size()) editingIdx = -1;
        if (selectedIdx >= notes().size()) selectedIdx = -1;

        if (editingIdx >= 0) {
            int fieldW = (lx2() - 88) - (lx1() + 2);
            editField = new LzTextField(lx1() + 2, height - 74, fieldW, 20);
            editField.setPlaceholder(LzText.literal("Note text...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
            editField.setText(notes().get(editingIdx).content().mc().getString());
            editField.setMaxLength(1024);
            addSelectableChild(editField);
            setFocused(editField);

            addDrawableChild(LzButton.of("Save",   lx2() - 86, height - 74, 40, 20, b -> commitEdit()));
            addDrawableChild(LzButton.of("Cancel", lx2() - 44, height - 74, 40, 20, b -> cancelEdit()));
        }

        addDrawableChild(LzButton.of("Done", width / 2 - 25, height - 26, 50, 18, b -> close()));
    }

    private void cancelEdit() {
        editingIdx  = -1;
        swapPending = false;
        clearChildren();
        init();
    }

    private void commitEdit() {
        if (editingIdx < 0 || editingIdx >= notes().size()) { cancelEdit(); return; }
        String text = editField.getText().trim();
        if (!text.isEmpty()) {
            notes().get(editingIdx).setContent(LzText.literal(text));
            noteTab.rebuildMessages();
            MultichatClient.saveTabsToConfig();
        }
        editingIdx = -1;
        clearChildren();
        init();
    }

    private void move(int idx, boolean up) {
        List<NoteTab.NoteEntry> list = notes();
        int target = up ? idx - 1 : idx + 1;
        if (target < 0 || target >= list.size()) return;
        Collections.swap(list, idx, target);
        selectedIdx = target;
        noteTab.rebuildMessages();
        MultichatClient.saveTabsToConfig();
        scrollToSelected();
    }

    private void scrollToSelected() {
        if (selectedIdx < 0) return;
        int rowTop = LIST_TOP + selectedIdx * ROW_H;
        int rowBot = rowTop + ROW_H;
        if (rowTop - scrollOffset < LIST_TOP)          scrollOffset = rowTop - LIST_TOP;
        else if (rowBot - scrollOffset > listBottom())  scrollOffset = rowBot - listBottom();
        scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset));
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        if (LzWorld.isLoaded()) LzDraw.fill(ctx, 0, 0, width, height, 0x55000000);
        super.lzRender(ctx, mouseX, mouseY, delta);

        LzDraw.drawCenteredText(ctx, LzText.literal("Notes — " + noteTab.name()), width / 2, 10, 0xFFFFCC44);
        if (editingIdx >= 0)
            LzDraw.drawCenteredText(ctx, LzText.format("&fEnter &7to save  ·  &fEsc &7to cancel"), width / 2, 23, 0xFFFFFFFF);
        else if (swapPending)
            LzDraw.drawCenteredText(ctx, LzText.format("&bSwap mode&7: click target row  ·  &fEsc &7to cancel"), width / 2, 23, 0xFFFFFFFF);
        else
            LzDraw.drawCenteredText(ctx, LzText.format("&7Click row to select  ·  &f↑↓ &7to move  ·  Swap &7to reorder"), width / 2, 23, 0xFFFFFFFF);

        if (editingIdx >= 0)
            editField.render(ctx.mc(), mouseX, mouseY, delta);

        int lx1 = lx1(), lx2 = lx2();
        LzDraw.fill(ctx, lx1, LIST_TOP, lx2, listBottom(), 0x44000000);
        LzDraw.drawBorder(ctx, lx1, LIST_TOP, lx2, listBottom(), 0xFF444444);

        // Scrollbar
        int max = maxScroll();
        if (max > 0) {
            int sbX    = lx2 + 5;
            int trackH = listBottom() - LIST_TOP;
            int totalPx = notes().size() * ROW_H;
            int thumbH = Math.max(10, trackH * trackH / totalPx);
            int thumbY = LIST_TOP + (int)((long) scrollOffset * (trackH - thumbH) / max);
            LzDraw.fill(ctx, sbX, LIST_TOP, sbX + SB_W, listBottom(), 0x44FFFFFF);
            LzDraw.drawBorder(ctx, sbX, LIST_TOP, sbX + SB_W, listBottom(), 0x88666666);
            LzDraw.fill(ctx, sbX + 1, thumbY, sbX + SB_W - 1, thumbY + thumbH,
                scrollDragging ? 0xFFDDDDDD : 0xBBAAAAAA);
        }

        // Sidebar ↑↓
        boolean hasSel  = selectedIdx >= 0 && selectedIdx < notes().size() && editingIdx < 0;
        boolean canUp   = hasSel && selectedIdx > 0;
        boolean canDown = hasSel && selectedIdx < notes().size() - 1;

        int cx1 = cbX1(), cx2 = cbX2(), cxM = cbCx();
        boolean hUp = mouseX >= cx1 && mouseX < cx2 && mouseY >= cbUpY1() && mouseY < cbUpY2();
        boolean hDn = mouseX >= cx1 && mouseX < cx2 && mouseY >= cbDnY1() && mouseY < cbDnY2();

        LzDraw.fill(ctx, cx1, cbUpY1(), cx2, cbUpY2(), canUp   ? (hUp ? 0xAA888888 : 0x66555555) : 0x33333333);
        LzDraw.drawBorder(ctx, cx1, cbUpY1(), cx2, cbUpY2(), 0x88666666);
        LzDraw.drawCenteredText(ctx, LzText.literal("↑"), cxM, cbUpY1() + (BTN_H - 8) / 2, canUp ? 0xFFFFFF : 0x555555);

        LzDraw.fill(ctx, cx1, cbDnY1(), cx2, cbDnY2(), canDown ? (hDn ? 0xAA888888 : 0x66555555) : 0x33333333);
        LzDraw.drawBorder(ctx, cx1, cbDnY1(), cx2, cbDnY2(), 0x88666666);
        LzDraw.drawCenteredText(ctx, LzText.literal("↓"), cxM, cbDnY1() + (BTN_H - 8) / 2, canDown ? 0xFFFFFF : 0x555555);

        boolean hSw   = mouseX >= cx1 && mouseX < cx2 && mouseY >= cbSwY1() && mouseY < cbSwY2();
        int swapBg = swapPending ? 0xAA4455FF : (hasSel ? (hSw ? 0xAA888888 : 0x66555555) : 0x33333333);
        LzDraw.fill(ctx, cx1, cbSwY1(), cx2, cbSwY2(), swapBg);
        LzDraw.drawBorder(ctx, cx1, cbSwY1(), cx2, cbSwY2(), 0x88666666);
        LzDraw.drawCenteredText(ctx, LzText.literal("Swap"), cxM, cbSwY1() + (BTN_H - 8) / 2, hasSel ? 0xFFFFFF : 0x555555);

        // Rows
        List<NoteTab.NoteEntry> list = notes();
        LzDraw.enableScissor(ctx, lx1, LIST_TOP, lx2, listBottom());
        for (int i = 0; i < list.size(); i++) {
            int ry = LIST_TOP + i * ROW_H - scrollOffset;
            if (ry + ROW_H <= LIST_TOP || ry >= listBottom()) continue;

            NoteTab.NoteEntry note = list.get(i);
            boolean editing = editingIdx == i;
            boolean sel     = selectedIdx == i && !editing;
            boolean hov     = !editing && !sel && mouseX >= lx1 && mouseX < lx2 && mouseY >= ry && mouseY < ry + ROW_H;

            if (editing)  LzDraw.fill(ctx, lx1, ry, lx2, ry + ROW_H, 0x55FFCC44);
            else if (sel) LzDraw.fill(ctx, lx1, ry, lx2, ry + ROW_H, swapPending ? 0x553366FF : 0x55FFFF44);
            else if (hov) LzDraw.fill(ctx, lx1, ry, lx2, ry + ROW_H, swapPending ? 0x3300FFAA : 0x22FFFFFF);

            String raw     = note.content().mc().getString();
            String preview = raw.length() > 52 ? raw.substring(0, 50) + "…" : raw;
            LzDraw.drawText(ctx, LzText.literal(preview), lx1 + 4, ry + (ROW_H - 8) / 2,
                editing ? 0xFFFFCC44 : 0xFFFFFFFF);

            if (!editing) {
                int dx    = lx2 - ACT_W - 2;
                boolean dInst = TabListScreen.quickDeleteMode || LzKeys.isCtrlDown();
                boolean dHov  = mouseX >= dx && mouseX < dx + ACT_W && mouseY >= ry + 2 && mouseY < ry + ROW_H - 2;
                int dBg  = dInst ? (dHov ? 0xAAFF3333 : 0x44FF3333) : (dHov ? 0xAAFF8800 : 0x44884400);
                int dBdr = dInst ? 0x88FF5555 : 0x88FFAA00;
                int dTxt = dInst ? 0xFFFF8888 : 0xFFFFAA44;
                LzDraw.fill(ctx, dx, ry + 2, dx + ACT_W, ry + ROW_H - 2, dBg);
                LzDraw.drawBorder(ctx, dx, ry + 2, dx + ACT_W, ry + ROW_H - 2, dBdr);
                LzDraw.drawCenteredText(ctx, LzText.literal("Del"), dx + ACT_W / 2, ry + (ROW_H - 8) / 2, dTxt);

                int ex   = dx - ACT_W - 4;
                boolean eHov = mouseX >= ex && mouseX < ex + ACT_W && mouseY >= ry + 2 && mouseY < ry + ROW_H - 2;
                LzDraw.fill(ctx, ex, ry + 2, ex + ACT_W, ry + ROW_H - 2, eHov ? 0xAAFFCC44 : 0x44FFCC44);
                LzDraw.drawBorder(ctx, ex, ry + 2, ex + ACT_W, ry + ROW_H - 2, 0x88FFCC44);
                LzDraw.drawCenteredText(ctx, LzText.literal("Edit"), ex + ACT_W / 2, ry + (ROW_H - 8) / 2, 0xFFFFCC44);
            }
        }
        LzDraw.disableScissor(ctx);

        if (list.isEmpty())
            LzDraw.drawCenteredText(ctx, LzText.literal("No notes yet."),
                width / 2, (LIST_TOP + listBottom()) / 2 - 4, 0xFF666666);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        // Scrollbar
        int sbX = lx2() + 5, max = maxScroll();
        if (max > 0 && mouseX >= sbX && mouseX < sbX + SB_W && mouseY >= LIST_TOP && mouseY < listBottom()) {
            int trackH  = listBottom() - LIST_TOP;
            int totalPx = notes().size() * ROW_H;
            int thumbH  = Math.max(10, trackH * trackH / totalPx);
            int thumbY  = LIST_TOP + (int)((long) scrollOffset * (trackH - thumbH) / max);
            if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                scrollDragging = true; scrollDragStartY = (int) mouseY; scrollDragStartOff = scrollOffset;
            } else {
                int travel = trackH - thumbH;
                if (travel > 0)
                    scrollOffset = Math.max(0, Math.min(max,
                        (int)((mouseY - LIST_TOP - thumbH / 2.0) * max / travel)));
            }
            return true;
        }

        // Sidebar ↑↓ Swap
        if (mouseX >= cbX1() && mouseX < cbX2() && editingIdx < 0) {
            boolean hasSel = selectedIdx >= 0 && selectedIdx < notes().size();
            if (mouseY >= cbUpY1() && mouseY < cbUpY2()) { if (hasSel) move(selectedIdx, true);  return true; }
            if (mouseY >= cbDnY1() && mouseY < cbDnY2()) { if (hasSel) move(selectedIdx, false); return true; }
            if (mouseY >= cbSwY1() && mouseY < cbSwY2()) { if (hasSel) swapPending = !swapPending; return true; }
        }

        // List rows
        int lx1 = lx1(), lx2 = lx2();
        List<NoteTab.NoteEntry> list = notes();
        for (int i = 0; i < list.size(); i++) {
            if (editingIdx == i) continue;
            int ry = LIST_TOP + i * ROW_H - scrollOffset;
            if (ry + ROW_H <= LIST_TOP || ry >= listBottom()) continue;

            int dx = lx2 - ACT_W - 2;
            if (mouseX >= dx && mouseX < dx + ACT_W && mouseY >= ry + 2 && mouseY < ry + ROW_H - 2) {
                if (TabListScreen.quickDeleteMode || LzKeys.isCtrlDown()) {
                    doRemove(i);
                } else {
                    int capturedI = i;
                    String label  = list.get(i).content().mc().getString();
                    if (label.length() > 20) label = label.substring(0, 18) + "…";
                    LzClient.mc().setScreen(new DeleteConfirmScreen(this,
                        "Delete note: \"" + label + "\"?",
                        () -> doRemove(capturedI)));
                }
                return true;
            }

            int ex = dx - ACT_W - 4;
            if (mouseX >= ex && mouseX < ex + ACT_W && mouseY >= ry + 2 && mouseY < ry + ROW_H - 2) {
                editingIdx = i;
                clearChildren();
                init();
                return true;
            }

            if (mouseX >= lx1 && mouseX < lx2 && mouseY >= ry && mouseY < ry + ROW_H && editingIdx < 0) {
                if (swapPending && i != selectedIdx) {
                    Collections.swap(notes(), selectedIdx, i);
                    selectedIdx = i;
                    swapPending = false;
                    noteTab.rebuildMessages();
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
            int max     = maxScroll();
            int trackH  = listBottom() - LIST_TOP;
            int totalPx = notes().size() * ROW_H;
            int thumbH  = Math.max(10, trackH * trackH / totalPx);
            int travel  = trackH - thumbH;
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
        if (mouseY >= LIST_TOP && mouseY < listBottom()) {
            scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - (int)(vDelta * ROW_H)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hDelta, vDelta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingIdx >= 0 && (keyCode == Keys.ENTER || keyCode == Keys.NUMPAD_ENTER)) {
            commitEdit(); return true;
        }
        if (keyCode == Keys.ESCAPE) {
            if (swapPending) { swapPending = false; return true; }
            if (editingIdx >= 0) { cancelEdit(); return true; }
            close(); return true;
        }
        if (editingIdx < 0 && selectedIdx >= 0) {
            if (keyCode == Keys.UP)   { move(selectedIdx, true);  return true; }
            if (keyCode == Keys.DOWN) { move(selectedIdx, false); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void doRemove(int i) {
        noteTab.removeNote(i);
        if (editingIdx == i)       editingIdx = -1;
        else if (editingIdx > i)   editingIdx--;
        if (selectedIdx > i)       selectedIdx--;
        else if (selectedIdx == i) selectedIdx = -1;
        MultichatClient.saveTabsToConfig();
        clearChildren();
        init();
    }

    @Override
    public void close() { LzClient.mc().setScreen(parent); }
}
