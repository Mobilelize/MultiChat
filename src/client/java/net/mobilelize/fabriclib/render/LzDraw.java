package net.mobilelize.fabriclib.render;

import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzText;

/**
 * Draw utilities for custom MC screens.
 * 1.21 (Yarn): DrawContext / TextRenderer APIs
 * 26.1 (Mojang): VERIFY class/method renames
 */
public final class LzDraw {

    private LzDraw() {}

    // 1.21: client.textRenderer   26.1: client.font
    private static Font tr() { return LzClient.mc().font; }

    // ── Solid fills ───────────────────────────────────────────────────────

    // unchanged: ctx.fill(x1, y1, x2, y2, colour)
    public static void fill(LzDrawContext ctx, int x1, int y1, int x2, int y2, int colour) {
        ctx.mc().fill(x1, y1, x2, y2, colour);
    }

    // unchanged: ctx.fillGradient(x1, y1, x2, y2, top, bottom)
    public static void fillGradientV(LzDrawContext ctx, int x1, int y1, int x2, int y2,
                                     int colourTop, int colourBottom) {
        ctx.mc().fillGradient(x1, y1, x2, y2, colourTop, colourBottom);
    }

    // 1.21.6: z-indexed fill removed — z ignored, use LzHudOverlay for layering
    public static void fill(LzDrawContext ctx, int x1, int y1, int x2, int y2, int z, int colour) {
        ctx.mc().fill(x1, y1, x2, y2, colour);
    }

    // ── Borders & dividers ────────────────────────────────────────────────

    public static void drawBorder(LzDrawContext ctx, int x1, int y1, int x2, int y2, int colour) {
        drawBorder(ctx, x1, y1, x2, y2, 1, colour);
    }

    public static void drawBorder(LzDrawContext ctx, int x1, int y1, int x2, int y2,
                                  int thickness, int colour) {
        fill(ctx, x1,             y1,             x2,             y1 + thickness, colour);
        fill(ctx, x1,             y2 - thickness, x2,             y2,             colour);
        fill(ctx, x1,             y1,             x1 + thickness, y2,             colour);
        fill(ctx, x2 - thickness, y1,             x2,             y2,             colour);
    }

    public static void drawHDivider(LzDrawContext ctx, int x, int y, int w, int colour) {
        fill(ctx, x, y, x + w, y + 1, colour);
    }

    public static void drawVDivider(LzDrawContext ctx, int x, int y, int h, int colour) {
        fill(ctx, x, y, x + 1, y + h, colour);
    }

    // ── Scissor ───────────────────────────────────────────────────────────

    // unchanged: ctx.enableScissor(x1, y1, x2, y2)
    public static void enableScissor(LzDrawContext ctx, int x1, int y1, int x2, int y2) {
        ctx.mc().enableScissor(x1, y1, x2, y2);
    }

    // unchanged: ctx.disableScissor()
    public static void disableScissor(LzDrawContext ctx) { ctx.mc().disableScissor(); }

    // ── Matrix transforms ─────────────────────────────────────────────────

    // 1.21.6: MatrixStack -> Matrix3x2fStack, push/pop renamed
    public static void pushMatrix(LzDrawContext ctx) { ctx.mc().pose().pushMatrix(); }

    // 1.21.6: MatrixStack -> Matrix3x2fStack, push/pop renamed
    public static void popMatrix(LzDrawContext ctx) { ctx.mc().pose().popMatrix(); }

    // 1.21.6: Matrix3x2f is 2D-only — no z component
    public static void translate(LzDrawContext ctx, float x, float y) { ctx.mc().pose().translate(x, y); }

    // 1.21.6: z ignored — Matrix3x2f has no z axis
    public static void translate(LzDrawContext ctx, float x, float y, float z) { ctx.mc().pose().translate(x, y); }

    // 1.21.6: ctx.getMatrices().scale(sx, sy)
    public static void scale(LzDrawContext ctx, float sx, float sy) { ctx.mc().pose().scale(sx, sy); }

    // 1.21.6: Matrix3x2fStack.rotate(radians) — screen space is Y-down, so a positive angle here
    // reads as visually clockwise, not the counterclockwise you'd expect from plain math convention.
    public static void rotate(LzDrawContext ctx, float radians) { ctx.mc().pose().rotate(radians); }

    // ── Text ──────────────────────────────────────────────────────────────

    // 1.21: ctx.drawTextWithShadow(tr, text, x, y, colour)   26.1: VERIFY name
    public static void drawText(LzDrawContext ctx, String text, int x, int y, int colour) {
        ctx.mc().drawString(tr(), text, x, y, colour);
    }

    public static void drawText(LzDrawContext ctx, LzText text, int x, int y, int colour) {
        ctx.mc().drawString(tr(), text.mc(), x, y, colour);
    }

    // 1.21: ctx.drawTextWithShadow(tr, OrderedText, x, y, colour)   26.1: VERIFY
    public static void drawText(LzDrawContext ctx, FormattedCharSequence text, int x, int y, int colour) {
        ctx.mc().drawString(tr(), text, x, y, colour);
    }

    public static void drawText(LzDrawContext ctx, LzOrderedText text, int x, int y, int colour) {
        ctx.mc().drawString(tr(), text.mc(), x, y, colour);
    }

    // 1.21: ctx.drawText(tr, text, x, y, colour, false) — no shadow
    public static void drawTextNoShadow(LzDrawContext ctx, String text, int x, int y, int colour) {
        ctx.mc().drawString(tr(), text, x, y, colour, false);
    }

    // 1.21: ctx.drawCenteredTextWithShadow(tr, text, cx, y, colour)   26.1: VERIFY
    public static void drawCenteredText(LzDrawContext ctx, String text, int centerX, int y, int colour) {
        ctx.mc().drawCenteredString(tr(), text, centerX, y, colour);
    }

    public static void drawCenteredText(LzDrawContext ctx, LzText text, int centerX, int y, int colour) {
        ctx.mc().drawCenteredString(tr(), text.mc(), centerX, y, colour);
    }

    public static void drawScaledText(LzDrawContext ctx, String text, int x, int y,
                                      float scale, int colour) {
        pushMatrix(ctx);
        translate(ctx, x, y);
        scale(ctx, scale, scale);
        ctx.mc().drawString(tr(), text, 0, 0, colour);
        popMatrix(ctx);
    }

    // 1.21: TextRenderer.getWidth(text)   26.1: VERIFY
    public static int textWidth(String text)         { return tr().width(text); }
    public static int textWidth(LzText text)         { return tr().width(text.mc()); }
    public static int textWidth(FormattedCharSequence text)    { return tr().width(text); }
    public static int textWidth(LzOrderedText text)  { return tr().width(text.mc()); }

    // 1.21: client.textRenderer.getTextHandler()   26.1: client.font.getSplitter() (VERIFY)
    public static StringSplitter textHandler() { return tr().getSplitter(); }

    // ── Window size from DrawContext ──────────────────────────────────────

    // 1.21: ctx.getScaledWindowWidth()   26.1: VERIFY
    public static int scaledWidth(LzDrawContext ctx)  { return ctx.mc().guiWidth(); }

    // 1.21: ctx.getScaledWindowHeight()   26.1: VERIFY
    public static int scaledHeight(LzDrawContext ctx) { return ctx.mc().guiHeight(); }
}
