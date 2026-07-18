package net.mobilelize.fabriclib.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzText;

/**
 * Stable base class for custom screens.
 * 1.21 (Yarn): net.minecraft.client.gui.screen.Screen
 * 26.1 (Mojang): net.minecraft.client.gui.screens.Screen
 */
public abstract class LzScreen extends Screen {

    // 1.21: Text.literal(title)   26.1: Component.literal(title)
    protected LzScreen(String title) {
        super(LzText.literal(title).mc());
    }

    // ── Render bridge ─────────────────────────────────────────────────────
    // Subclasses override lzRender / lzRenderBackground instead of the raw
    // MC methods, keeping DrawContext out of subclass imports.

    // 1.21: Screen.render(DrawContext, int, int, float)   26.1: VERIFY signature
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        lzRender(LzDrawContext.wrap(ctx), mouseX, mouseY, delta);
    }

    // 1.21: Screen.renderBackground(DrawContext, int, int, float)   26.1: VERIFY
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        lzRenderBackground(LzDrawContext.wrap(ctx), mouseX, mouseY, delta);
    }

    protected void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx.mc(), mouseX, mouseY, delta);
    }

    protected void lzRenderBackground(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx.mc(), mouseX, mouseY, delta);
    }
}
