package net.mobilelize.fabriclib.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Stable wrapper for MC GuiGraphics.
 * 1.21 (Yarn): net.minecraft.client.gui.DrawContext
 * 26.1 (Mojang): net.minecraft.client.gui.GuiGraphics
 */
public final class LzDrawContext {

    private final GuiGraphicsExtractor ctx;

    private LzDrawContext(GuiGraphicsExtractor ctx) { this.ctx = ctx; }

    /** Raw MC GuiGraphics — escape hatch. */
    public GuiGraphicsExtractor mc() { return ctx; }

    // 1.21: new LzDrawContext(DrawContext)   26.1: wraps GuiGraphics
    public static LzDrawContext wrap(GuiGraphicsExtractor ctx) {
        return ctx == null ? null : new LzDrawContext(ctx);
    }
}
