package net.mobilelize.fabriclib.render;

import net.minecraft.client.gui.DrawContext;

/**
 * Stable wrapper for MC DrawContext.
 * 1.21 (Yarn): net.minecraft.client.gui.DrawContext
 * 26.1 (Mojang): net.minecraft.client.gui.GuiGraphics  (VERIFY)
 */
public final class LzDrawContext {

    private final DrawContext ctx;

    private LzDrawContext(DrawContext ctx) { this.ctx = ctx; }

    /** Raw MC DrawContext — escape hatch. */
    public DrawContext mc() { return ctx; }

    // 1.21: new LzDrawContext(ctx)   26.1: VERIFY class rename
    public static LzDrawContext wrap(DrawContext ctx) {
        return ctx == null ? null : new LzDrawContext(ctx);
    }
}
