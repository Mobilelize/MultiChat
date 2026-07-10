package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.mobilelize.fabriclib.client.*;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.fabriclib.world.LzWorld;
import net.mobilelize.multichat.client.Colors;
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

    private static final int[] SWATCH_COLORS = Colors.SWATCH_COLORS;
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
        String raw = field != null ? field.getValue() : "";
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
            field.setValue(raw != null && !raw.isEmpty() ? raw : e.content().mc().getString());
        }
        addWidget(field);
        setFocused(field);

        int cx = width / 2;
        addRenderableWidget(LzButton.of("Save",   cx - 53, cy() + 86, 50, 18, b -> save()));
        addRenderableWidget(LzButton.of("Cancel", cx + 3,  cy() + 86, 50, 18, b -> onClose()));
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        if (LzWorld.isLoaded()) LzDraw.fill(ctx, 0, 0, width, height, Colors.OVERLAY);
        super.lzRender(ctx, mouseX, mouseY, delta);

        int px1 = px1(), px2 = px2(), cx = width / 2, cy = cy();

        LzDraw.fill(ctx, px1 - 6, cy - 72, px2 + 6, cy + 110, Colors.NOTE_EDIT_BG);
        LzDraw.drawBorder(ctx, px1 - 6, cy - 72, px2 + 6, cy + 110, Colors.BORDER);
        LzDraw.drawCenteredText(ctx, LzText.literal(noteIdx < 0 ? "New Note" : "Edit Note"), cx, cy - 65, Colors.GOLD);

        // Toolbar
        int swX0 = px1 + 36, swY = cy - 53;
        LzDraw.drawText(ctx, LzText.literal("Color:"), px1, swY + 2, Colors.TEXT_MID_GRAY);
        for (int i = 0; i < 16; i++) {
            int sx  = swX0 + i * (SW_W + SW_G);
            boolean hov = mouseX >= sx && mouseX < sx + SW_W && mouseY >= swY && mouseY < swY + SW_H;
            LzDraw.fill(ctx, sx, swY, sx + SW_W, swY + SW_H, SWATCH_COLORS[i]);
            LzDraw.drawBorder(ctx, sx, swY, sx + SW_W, swY + SW_H, hov ? Colors.TEXT_WHITE : Colors.BORDER_DARKEST);
        }
        int fx = fmtX();
        drawFmtBtn(ctx, mouseX, mouseY, fx,                  swY, "R", Colors.FMT_BTN_R);
        drawFmtBtn(ctx, mouseX, mouseY, fx + FMT_W + 2,      swY, "B", Colors.FMT_BTN_B);
        drawFmtBtn(ctx, mouseX, mouseY, fx + (FMT_W + 2) * 2, swY, "I", Colors.FMT_BTN_I);

        LzDraw.drawText(ctx, LzText.literal("Use &X codes or click swatches"), px1, cy - 36, Colors.TEXT_DISABLED);

        field.extractRenderState(ctx.mc(), mouseX, mouseY, delta);

        // Scrollable preview
        List<LzOrderedText> lines = previewLines();
        int maxScroll = prevMaxScroll(lines);
        prevScroll = Math.min(prevScroll, maxScroll);

        int py1 = prevY1(), py2 = prevY2(), pxA = prevX1(), pxB = prevX2();

        LzDraw.fill(ctx, pxA, py1, pxB, py2, Colors.OVERLAY_LIGHT);
        LzDraw.drawBorder(ctx, pxA, py1, pxB, py2, Colors.BORDER_DARKEST);
        LzDraw.drawText(ctx, LzText.format("&8Preview"), pxA + 3, py1 - 9, Colors.TEXT_WHITE);

        LzDraw.enableScissor(ctx, pxA + 2, py1 + 2, pxB - PREV_SB - 2, py2 - 2);
        for (int i = 0; i < lines.size(); i++) {
            int lineY = py1 + 3 + i * LINE_H - prevScroll;
            if (lineY + LINE_H < py1 || lineY > py2) continue;
            LzDraw.drawText(ctx, lines.get(i), pxA + 4, lineY, Colors.TEXT_WHITE);
        }
        LzDraw.disableScissor(ctx);

        // Scrollbar
        if (maxScroll > 0) {
            int sbX    = pxB - PREV_SB - 1;
            int trackH = PREV_H - 4;
            int totalH = lines.size() * LINE_H + 4;
            int thumbH = Math.max(8, trackH * trackH / totalH);
            int thumbY = py1 + 2 + (int)((long) prevScroll * (trackH - thumbH) / maxScroll);
            LzDraw.fill(ctx, sbX, py1 + 2, sbX + PREV_SB, py2 - 2, Colors.SCROLLBAR_TRACK_DIM);
            LzDraw.fill(ctx, sbX + 1, thumbY, sbX + PREV_SB - 1, thumbY + thumbH,
                prevDragging ? Colors.SCROLLBAR_THUMB_DRAG : Colors.SCROLLBAR_THUMB);
        }
    }

    private void drawFmtBtn(LzDrawContext ctx, int mx, int my, int x, int y, String label, int col) {
        boolean hov = mx >= x && mx < x + FMT_W && my >= y && my < y + SW_H;
        LzDraw.fill(ctx, x, y, x + FMT_W, y + SW_H, hov ? Colors.FMT_BTN_HOVER : Colors.FMT_BTN_BG);
        LzDraw.drawBorder(ctx, x, y, x + FMT_W, y + SW_H, Colors.FMT_BTN_BORDER);
        LzDraw.drawCenteredText(ctx, LzText.literal(label), x + FMT_W / 2, y + 2, col);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean isDoubleClick) {
        double mouseX = click.x(); double mouseY = click.y(); int button = click.button();
        if (super.mouseClicked(click, isDoubleClick)) return true;
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
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        double mouseY = click.y();
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
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        prevDragging = false;
        return super.mouseReleased(click);
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
        if (field instanceof EditBox mcf) mcf.insertText(code);
        else field.setValue(field.getValue() + code);
        setFocused(field);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == Keys.ENTER || input.key() == Keys.NUMPAD_ENTER) { save(); return true; }
        if (input.key() == Keys.ESCAPE) { onClose(); return true; }
        return super.keyPressed(input);
    }

    private void save() {
        String text = field.getValue().trim();
        if (noteIdx < 0) {
            NoteTab.NoteEntry e = new NoteTab.NoteEntry(LzText.format(text), 0);
            e.setRawFormat(text);
            noteTab.noteEntries().addFirst(e);
        } else if (noteIdx < noteTab.noteEntries().size()) {
            NoteTab.NoteEntry e = noteTab.noteEntries().get(noteIdx);
            e.setContent(LzText.format(text));
            e.setRawFormat(text);
        }
        noteTab.rebuildMessages();
        MultichatClient.saveTabsToConfig();
        onClose();
    }

    @Override
    public void onClose() { LzClient.mc().setScreen(parent); }
}
