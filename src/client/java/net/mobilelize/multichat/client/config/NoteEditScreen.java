package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.client.LzTextField;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.fabriclib.world.LzWorld;
import net.mobilelize.multichat.client.Keys;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.NoteTab;

import java.util.List;

public class NoteEditScreen extends LzScreen {

    private static final int PANEL_W = 340;
    private static final int SW_W    = 12;
    private static final int SW_H    = 12;
    private static final int SW_G    = 2;
    private static final int FMT_W   = 17;
    private static final int PREV_H  = 64;
    private static final int LINE_H  = 10;
    private static final int PREV_SB = 6;

    private static final int[] SWATCH_COLORS = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
        0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
        0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    };
    private static final char[] SWATCH_CHARS = {
        '0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'
    };

    private final Screen  parent;
    private final NoteTab noteTab;
    private final int     noteIdx;

    private LzTextField field;

    private int     prevScroll    = 0;
    private boolean prevDragging  = false;
    private int     prevDragY     = 0;
    private int     prevDragStart = 0;

    public NoteEditScreen(Screen parent, NoteTab noteTab, int noteIdx) {
        super(noteIdx < 0 ? "New Note" : "Edit Note");
        this.parent  = parent;
        this.noteTab = noteTab;
        this.noteIdx = noteIdx;
    }

    private int px1()  { return width  / 2 - PANEL_W / 2; }
    private int px2()  { return width  / 2 + PANEL_W / 2; }
    private int cy()   { return height / 2; }
    private int fmtX() { return px1() + 36 + 16 * (SW_W + SW_G) + 4; }

    // Preview bounds
    private int prevY1() { return cy() + 10; }
    private int prevY2() { return prevY1() + PREV_H; }
    private int prevX1() { return px1(); }
    private int prevX2() { return px2(); }

    private int prevTextW() { return prevX2() - prevX1() - PREV_SB - 8; }

    private List<LzOrderedText> previewLines() {
        String raw = field != null ? field.getText() : "";
        if (raw.isEmpty()) return List.of(LzText.format("&8(preview)").ordered());
        return LzChat.breakLines(LzText.format(raw), prevTextW());
    }

    private int prevMaxScroll(List<LzOrderedText> lines) {
        return Math.max(0, lines.size() * LINE_H + 4 - PREV_H);
    }

    @Override
    protected void init() {
        field = new LzTextField(px1(), cy() - 23, PANEL_W, 20);
        field.setMaxLength(512);
        if (noteIdx >= 0 && noteIdx < noteTab.noteEntries().size()) {
            NoteTab.NoteEntry e = noteTab.noteEntries().get(noteIdx);
            String raw = e.rawFormat();
            field.setText(raw != null && !raw.isEmpty() ? raw : e.content().mc().getString());
        }
        addSelectableChild(field);
        setFocused(field);

        int cx = width / 2;
        addDrawableChild(LzButton.of("Save",   cx - 53, cy() + 86, 50, 18, b -> save()));
        addDrawableChild(LzButton.of("Cancel", cx + 3,  cy() + 86, 50, 18, b -> close()));
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        if (LzWorld.isLoaded()) LzDraw.fill(ctx, 0, 0, width, height, 0x55000000);
        super.lzRender(ctx, mouseX, mouseY, delta);

        int px1 = px1(), px2 = px2(), cx = width / 2, cy = cy();

        LzDraw.fill(ctx, px1 - 6, cy - 72, px2 + 6, cy + 110, 0xAA111111);
        LzDraw.drawBorder(ctx, px1 - 6, cy - 72, px2 + 6, cy + 110, 0xFF555555);
        LzDraw.drawCenteredText(ctx, LzText.literal(noteIdx < 0 ? "New Note" : "Edit Note"), cx, cy - 65, 0xFFFFCC44);

        // Toolbar
        int swX0 = px1 + 36, swY = cy - 53;
        LzDraw.drawText(ctx, LzText.literal("Color:"), px1, swY + 2, 0xFF777777);
        for (int i = 0; i < 16; i++) {
            int sx  = swX0 + i * (SW_W + SW_G);
            boolean hov = mouseX >= sx && mouseX < sx + SW_W && mouseY >= swY && mouseY < swY + SW_H;
            LzDraw.fill(ctx, sx, swY, sx + SW_W, swY + SW_H, 0xFF000000 | SWATCH_COLORS[i]);
            LzDraw.drawBorder(ctx, sx, swY, sx + SW_W, swY + SW_H, hov ? 0xFFFFFFFF : 0xFF333333);
        }
        int fx = fmtX();
        drawFmtBtn(ctx, mouseX, mouseY, fx,                  swY, "R", 0xFFAAAAAA);
        drawFmtBtn(ctx, mouseX, mouseY, fx + FMT_W + 2,      swY, "B", 0xFFFFFFFF);
        drawFmtBtn(ctx, mouseX, mouseY, fx + (FMT_W + 2) * 2, swY, "I", 0xFFCCCCFF);

        LzDraw.drawText(ctx, LzText.literal("Use &X codes or click swatches"), px1, cy - 36, 0xFF555555);

        field.render(ctx.mc(), mouseX, mouseY, delta);

        // Scrollable preview
        List<LzOrderedText> lines = previewLines();
        int maxScroll = prevMaxScroll(lines);
        prevScroll = Math.min(prevScroll, maxScroll);

        int py1 = prevY1(), py2 = prevY2(), pxA = prevX1(), pxB = prevX2();

        LzDraw.fill(ctx, pxA, py1, pxB, py2, 0x44000000);
        LzDraw.drawBorder(ctx, pxA, py1, pxB, py2, 0xFF333333);
        LzDraw.drawText(ctx, LzText.format("&8Preview"), pxA + 3, py1 - 9, 0xFFFFFFFF);

        LzDraw.enableScissor(ctx, pxA + 2, py1 + 2, pxB - PREV_SB - 2, py2 - 2);
        for (int i = 0; i < lines.size(); i++) {
            int lineY = py1 + 3 + i * LINE_H - prevScroll;
            if (lineY + LINE_H < py1 || lineY > py2) continue;
            LzDraw.drawText(ctx, lines.get(i), pxA + 4, lineY, 0xFFFFFFFF);
        }
        LzDraw.disableScissor(ctx);

        // Scrollbar
        if (maxScroll > 0) {
            int sbX    = pxB - PREV_SB - 1;
            int trackH = PREV_H - 4;
            int totalH = lines.size() * LINE_H + 4;
            int thumbH = Math.max(8, trackH * trackH / totalH);
            int thumbY = py1 + 2 + (int)((long) prevScroll * (trackH - thumbH) / maxScroll);
            LzDraw.fill(ctx, sbX, py1 + 2, sbX + PREV_SB, py2 - 2, 0x33FFFFFF);
            LzDraw.fill(ctx, sbX + 1, thumbY, sbX + PREV_SB - 1, thumbY + thumbH,
                prevDragging ? 0xFFDDDDDD : 0xBBAAAAAA);
        }
    }

    private void drawFmtBtn(LzDrawContext ctx, int mx, int my, int x, int y, String label, int col) {
        boolean hov = mx >= x && mx < x + FMT_W && my >= y && my < y + SW_H;
        LzDraw.fill(ctx, x, y, x + FMT_W, y + SW_H, hov ? 0xAA555555 : 0x44333333);
        LzDraw.drawBorder(ctx, x, y, x + FMT_W, y + SW_H, 0xFF444444);
        LzDraw.drawCenteredText(ctx, LzText.literal(label), x + FMT_W / 2, y + 2, col);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        int swX0 = px1() + 36, swY = cy() - 53;

        for (int i = 0; i < 16; i++) {
            int sx = swX0 + i * (SW_W + SW_G);
            if (mouseX >= sx && mouseX < sx + SW_W && mouseY >= swY && mouseY < swY + SW_H) {
                insertAtCursor("&" + SWATCH_CHARS[i]); return true;
            }
        }

        int fx = fmtX();
        if (mouseY >= swY && mouseY < swY + SW_H) {
            if (mouseX >= fx                      && mouseX < fx + FMT_W)                 { insertAtCursor("&r"); return true; }
            if (mouseX >= fx + FMT_W + 2          && mouseX < fx + (FMT_W + 2) + FMT_W)  { insertAtCursor("&l"); return true; }
            if (mouseX >= fx + (FMT_W + 2) * 2   && mouseX < fx + (FMT_W + 2) * 2 + FMT_W) { insertAtCursor("&o"); return true; }
        }

        // Preview scrollbar
        List<LzOrderedText> lines = previewLines();
        int maxScroll = prevMaxScroll(lines);
        int py1 = prevY1(), py2 = prevY2(), sbX = prevX2() - PREV_SB - 1;
        if (maxScroll > 0 && mouseX >= sbX && mouseX < sbX + PREV_SB && mouseY >= py1 && mouseY < py2) {
            int trackH = PREV_H - 4;
            int totalH = lines.size() * LINE_H + 4;
            int thumbH = Math.max(8, trackH * trackH / totalH);
            int thumbY = py1 + 2 + (int)((long) prevScroll * (trackH - thumbH) / maxScroll);
            if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                prevDragging = true; prevDragY = (int) mouseY; prevDragStart = prevScroll;
            } else {
                int travel = trackH - thumbH;
                if (travel > 0)
                    prevScroll = Math.max(0, Math.min(maxScroll,
                        (int)((mouseY - py1 - 2 - thumbH / 2.0) * maxScroll / travel)));
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (prevDragging) {
            List<LzOrderedText> lines = previewLines();
            int maxScroll = prevMaxScroll(lines);
            int trackH = PREV_H - 4;
            int totalH = lines.size() * LINE_H + 4;
            int thumbH = Math.max(8, trackH * trackH / totalH);
            int travel = trackH - thumbH;
            if (travel > 0)
                prevScroll = Math.max(0, Math.min(maxScroll,
                    prevDragStart + (int)((mouseY - prevDragY) * maxScroll / travel)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        prevDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hDelta, double vDelta) {
        if (mouseY >= prevY1() && mouseY < prevY2()) {
            List<LzOrderedText> lines = previewLines();
            prevScroll = Math.max(0, Math.min(prevMaxScroll(lines), prevScroll - (int)(vDelta * LINE_H)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hDelta, vDelta);
    }

    private void insertAtCursor(String code) {
        if (field instanceof TextFieldWidget mcf) mcf.write(code);
        else field.setText(field.getText() + code);
        setFocused(field);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Keys.ENTER || keyCode == Keys.NUMPAD_ENTER) { save(); return true; }
        if (keyCode == Keys.ESCAPE) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void save() {
        String text = field.getText().trim();
        if (noteIdx < 0) {
            NoteTab.NoteEntry e = new NoteTab.NoteEntry(LzText.format(text), 0);
            e.setRawFormat(text);
            noteTab.noteEntries().add(0, e);
        } else if (noteIdx < noteTab.noteEntries().size()) {
            NoteTab.NoteEntry e = noteTab.noteEntries().get(noteIdx);
            e.setContent(LzText.format(text));
            e.setRawFormat(text);
        }
        noteTab.rebuildMessages();
        MultichatClient.saveTabsToConfig();
        close();
    }

    @Override
    public void close() { LzClient.mc().setScreen(parent); }
}
