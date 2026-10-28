package net.mobilelize.fabriclib.render;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Stable wrapper for MC DrawContext.
 * 1.21 (Yarn): net.minecraft.client.gui.DrawContext
 * 26.1 (Mojang): net.minecraft.client.gui.GuiGraphics  (VERIFY)
 */
public final class LzDrawContext {

    private final GuiGraphics ctx;

    private LzDrawContext(GuiGraphics ctx) { this.ctx = ctx; }

    /** Raw MC DrawContext — escape hatch. */
    public GuiGraphics mc() { return ctx; }

    // 1.21: new LzDrawContext(ctx)   26.1: VERIFY class rename
    public static LzDrawContext wrap(GuiGraphics ctx) {
        return ctx == null ? null : new LzDrawContext(ctx);
    }
}
