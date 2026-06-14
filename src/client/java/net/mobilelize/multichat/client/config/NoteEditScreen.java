package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.client.LzTextField;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.fabriclib.world.LzWorld;
import net.mobilelize.multichat.client.Keys;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.NoteTab;

public class NoteEditScreen extends LzScreen {

    private static final int PANEL_W = 340;
    private static final int SW_W    = 12;
    private static final int SW_H    = 12;
    private static final int SW_G    = 2;
    private static final int FMT_W   = 17;

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

    public NoteEditScreen(Screen parent, NoteTab noteTab, int noteIdx) {
        super("Edit Note");
        this.parent  = parent;
        this.noteTab = noteTab;
        this.noteIdx = noteIdx;
    }

    private int px1() { return width  / 2 - PANEL_W / 2; }
    private int px2() { return width  / 2 + PANEL_W / 2; }
    private int cy()  { return height / 2; }

    // X of first format button (R/B/I), placed right of swatches
    private int fmtX() { return px1() + 36 + 16 * (SW_W + SW_G) + 4; }

    @Override
    protected void init() {
        field = new LzTextField(px1(), cy() - 14, PANEL_W, 20);
        field.setMaxLength(512);
        if (noteIdx >= 0 && noteIdx < noteTab.noteEntries().size())
            field.setText(noteTab.noteEntries().get(noteIdx).content().mc().getString());
        addSelectableChild(field);
        setFocused(field);

        int cx = width / 2;
        addDrawableChild(LzButton.of("Save",   cx - 53, cy() + 42, 50, 18, b -> save()));
        addDrawableChild(LzButton.of("Cancel", cx + 3,  cy() + 42, 50, 18, b -> close()));
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        if (LzWorld.isLoaded()) LzDraw.fill(ctx, 0, 0, width, height, 0x55000000);
        super.lzRender(ctx, mouseX, mouseY, delta);

        int px1 = px1(), px2 = px2(), cx = width / 2, cy = cy();

        LzDraw.fill(ctx, px1 - 6, cy - 60, px2 + 6, cy + 66, 0xAA111111);
        LzDraw.drawBorder(ctx, px1 - 6, cy - 60, px2 + 6, cy + 66, 0xFF555555);
        LzDraw.drawCenteredText(ctx, LzText.literal("Edit Note"), cx, cy - 54, 0xFFFFCC44);

        // Toolbar row
        int swX0 = px1 + 36, swY = cy - 43;
        LzDraw.drawText(ctx, LzText.literal("Color:"), px1, cy - 41, 0xFF777777);

        for (int i = 0; i < 16; i++) {
            int sx  = swX0 + i * (SW_W + SW_G);
            boolean hov = mouseX >= sx && mouseX < sx + SW_W && mouseY >= swY && mouseY < swY + SW_H;
            LzDraw.fill(ctx, sx, swY, sx + SW_W, swY + SW_H, 0xFF000000 | SWATCH_COLORS[i]);
            LzDraw.drawBorder(ctx, sx, swY, sx + SW_W, swY + SW_H, hov ? 0xFFFFFFFF : 0xFF333333);
        }

        int fx = fmtX();
        drawFmtBtn(ctx, mouseX, mouseY, fx,            swY, "R", 0xFFAAAAAA);
        drawFmtBtn(ctx, mouseX, mouseY, fx + FMT_W + 2, swY, "B", 0xFFFFFFFF);
        drawFmtBtn(ctx, mouseX, mouseY, fx + (FMT_W + 2) * 2, swY, "I", 0xFFCCCCFF);

        // Hint
        LzDraw.drawText(ctx, LzText.literal("Use &X codes or click swatches"), px1, cy - 27, 0xFF555555);

        // Field
        field.render(ctx.mc(), mouseX, mouseY, delta);

        // Preview
        int prevY    = cy + 10;
        int labelW   = LzDraw.textWidth("Preview: ");
        int prevTextX = px1 + 4 + labelW;
        LzDraw.fill(ctx, px1, prevY, px2, prevY + 24, 0x33000000);
        LzDraw.drawBorder(ctx, px1, prevY, px2, prevY + 24, 0xFF333333);
        LzDraw.drawText(ctx, LzText.format("&8Preview: "), px1 + 4, prevY + 8, 0xFFFFFFFF);
        LzDraw.enableScissor(ctx, prevTextX, prevY + 2, px2 - 2, prevY + 22);
        String raw = field.getText();
        LzDraw.drawText(ctx,
            raw.isEmpty() ? LzText.format("&8…") : LzText.format(raw),
            prevTextX, prevY + 8, 0xFFFFFFFF);
        LzDraw.disableScissor(ctx);
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

        int swX0 = px1() + 36, swY = cy() - 43;

        for (int i = 0; i < 16; i++) {
            int sx = swX0 + i * (SW_W + SW_G);
            if (mouseX >= sx && mouseX < sx + SW_W && mouseY >= swY && mouseY < swY + SW_H) {
                insertAtCursor("&" + SWATCH_CHARS[i]);
                return true;
            }
        }

        int fx = fmtX();
        if (mouseY >= swY && mouseY < swY + SW_H) {
            if (mouseX >= fx                     && mouseX < fx + FMT_W)             { insertAtCursor("&r"); return true; }
            if (mouseX >= fx + FMT_W + 2         && mouseX < fx + (FMT_W + 2) + FMT_W) { insertAtCursor("&l"); return true; }
            if (mouseX >= fx + (FMT_W + 2) * 2  && mouseX < fx + (FMT_W + 2) * 2 + FMT_W) { insertAtCursor("&o"); return true; }
        }

        return false;
    }

    private void insertAtCursor(String code) {
        if (field instanceof TextFieldWidget mcf) {
            mcf.write(code);
        } else {
            field.setText(field.getText() + code);
        }
        setFocused(field);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Keys.ENTER || keyCode == Keys.NUMPAD_ENTER) { save(); return true; }
        if (keyCode == Keys.ESCAPE) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void save() {
        if (noteIdx < 0 || noteIdx >= noteTab.noteEntries().size()) { close(); return; }
        String text = field.getText().trim();
        if (!text.isEmpty()) {
            noteTab.noteEntries().get(noteIdx).setContent(LzText.format(text));
            noteTab.rebuildMessages();
            MultichatClient.saveTabsToConfig();
        }
        close();
    }

    @Override
    public void close() { LzClient.mc().setScreen(parent); }
}
