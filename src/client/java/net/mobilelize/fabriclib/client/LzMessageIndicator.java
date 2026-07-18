package net.mobilelize.fabriclib.client;

import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.mobilelize.fabriclib.render.LzDrawContext;
import org.jetbrains.annotations.Nullable;

/**
 * Wrapper for MC GuiMessageTag (read receipt / system icons in chat).
 * 1.21 (Yarn): net.minecraft.client.gui.hud.MessageIndicator
 * 26.1 (Mojang): net.minecraft.client.GuiMessageTag  (VERIFY)
 */
public final class LzMessageIndicator {

    private final GuiMessageTag indicator;

    private LzMessageIndicator(GuiMessageTag indicator) { this.indicator = indicator; }

    /** Wraps an existing MC GuiMessageTag — use only at mixin boundaries. */
    public static LzMessageIndicator wrap(GuiMessageTag indicator) {
        return new LzMessageIndicator(indicator);
    }

    @Nullable
    public static LzMessageIndicator wrapNullable(@Nullable GuiMessageTag indicator) {
        return indicator == null ? null : new LzMessageIndicator(indicator);
    }

    /** Raw MC GuiMessageTag — escape hatch. */
    public GuiMessageTag mc() { return indicator; }

    // 1.21: indicator.indicatorColor()   26.1: VERIFY
    public int indicatorColor() { return indicator.indicatorColor(); }

    // 1.21: indicator.icon()   26.1: VERIFY
    @Nullable
    public GuiMessageTag.Icon icon() { return indicator.icon(); }

    // 1.21: indicator.icon().width   26.1: VERIFY
    public int iconWidth() {
        var icon = indicator.icon();
        return icon != null ? icon.width : 0;
    }

    // 1.21: indicator.icon().height   26.1: VERIFY
    public int iconHeight() {
        var icon = indicator.icon();
        return icon != null ? icon.height : 0;
    }

    // 1.21: indicator.icon().draw(ctx, x, y)   26.1: VERIFY
    public void drawIcon(LzDrawContext ctx, int x, int y) {
        var icon = indicator.icon();
        if (icon != null) icon.extractRenderState(ctx.mc(), x, y);
    }

    public boolean hasIcon() { return indicator.icon() != null; }
}
