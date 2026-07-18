package net.mobilelize.fabriclib.render;

import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
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
    private static TextRenderer tr() { return LzClient.mc().textRenderer; }

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

    // 1.21: ctx.fill(x1, y1, x2, y2, z, colour)   26.1: VERIFY (z-indexed fill for layering)
    public static void fill(LzDrawContext ctx, int x1, int y1, int x2, int y2, int z, int colour) {
        ctx.mc().fill(x1, y1, x2, y2, z, colour);
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

    // 1.21: ctx.getMatrices().push()   26.1: VERIFY MatrixStack method name
    public static void pushMatrix(LzDrawContext ctx) { ctx.mc().getMatrices().push(); }

    // 1.21: ctx.getMatrices().pop()   26.1: VERIFY MatrixStack method name
    public static void popMatrix(LzDrawContext ctx) { ctx.mc().getMatrices().pop(); }

    // 1.21: ctx.getMatrices().translate(x, y, 0)   26.1: VERIFY signature
    public static void translate(LzDrawContext ctx, float x, float y) { ctx.mc().getMatrices().translate(x, y, 0); }

    // 1.21: ctx.getMatrices().translate(x, y, z)   26.1: VERIFY signature
    public static void translate(LzDrawContext ctx, float x, float y, float z) { ctx.mc().getMatrices().translate(x, y, z); }

    // 1.21: ctx.getMatrices().scale(sx, sy, 1)   26.1: VERIFY signature
    public static void scale(LzDrawContext ctx, float sx, float sy) { ctx.mc().getMatrices().scale(sx, sy, 1); }

    // ── Text ──────────────────────────────────────────────────────────────

    // 1.21: ctx.drawTextWithShadow(tr, text, x, y, colour)   26.1: VERIFY name
    public static void drawText(LzDrawContext ctx, String text, int x, int y, int colour) {
        ctx.mc().drawTextWithShadow(tr(), text, x, y, colour);
    }

    public static void drawText(LzDrawContext ctx, LzText text, int x, int y, int colour) {
        ctx.mc().drawTextWithShadow(tr(), text.mc(), x, y, colour);
    }

    // 1.21: ctx.drawTextWithShadow(tr, OrderedText, x, y, colour)   26.1: VERIFY
    public static void drawText(LzDrawContext ctx, OrderedText text, int x, int y, int colour) {
        ctx.mc().drawTextWithShadow(tr(), text, x, y, colour);
    }

    public static void drawText(LzDrawContext ctx, LzOrderedText text, int x, int y, int colour) {
        ctx.mc().drawTextWithShadow(tr(), text.mc(), x, y, colour);
    }

    // 1.21: ctx.drawText(tr, text, x, y, colour, false) — no shadow
    public static void drawTextNoShadow(LzDrawContext ctx, String text, int x, int y, int colour) {
        ctx.mc().drawText(tr(), text, x, y, colour, false);
    }

    // 1.21: ctx.drawCenteredTextWithShadow(tr, text, cx, y, colour)   26.1: VERIFY
    public static void drawCenteredText(LzDrawContext ctx, String text, int centerX, int y, int colour) {
        ctx.mc().drawCenteredTextWithShadow(tr(), text, centerX, y, colour);
    }

    public static void drawCenteredText(LzDrawContext ctx, LzText text, int centerX, int y, int colour) {
        ctx.mc().drawCenteredTextWithShadow(tr(), text.mc(), centerX, y, colour);
    }

    public static void drawScaledText(LzDrawContext ctx, String text, int x, int y,
                                      float scale, int colour) {
        pushMatrix(ctx);
        translate(ctx, x, y);
        scale(ctx, scale, scale);
        ctx.mc().drawTextWithShadow(tr(), text, 0, 0, colour);
        popMatrix(ctx);
    }

    // 1.21: TextRenderer.getWidth(text)   26.1: VERIFY
    public static int textWidth(String text)         { return tr().getWidth(text); }
    public static int textWidth(LzText text)         { return tr().getWidth(text.mc()); }
    public static int textWidth(OrderedText text)    { return tr().getWidth(text); }
    public static int textWidth(LzOrderedText text)  { return tr().getWidth(text.mc()); }

    // 1.21: client.textRenderer.getTextHandler()   26.1: client.font.getSplitter() (VERIFY)
    public static TextHandler textHandler() { return tr().getTextHandler(); }

    // ── Window size from DrawContext ──────────────────────────────────────

    // 1.21: ctx.getScaledWindowWidth()   26.1: VERIFY
    public static int scaledWidth(LzDrawContext ctx)  { return ctx.mc().getScaledWindowWidth(); }

    // 1.21: ctx.getScaledWindowHeight()   26.1: VERIFY
    public static int scaledHeight(LzDrawContext ctx) { return ctx.mc().getScaledWindowHeight(); }
}
