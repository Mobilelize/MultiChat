package net.mobilelize.multichat.client.filter;

import net.mobilelize.fabriclib.text.LzFormatting;
import org.jetbrains.annotations.Nullable;

/** The 16 vanilla legacy chat colors, as a plain enum instead of a raw RGB int or a legacy code character —
 *  picked from a dropdown like any other enum condition. NONE means "don't check color at all." */
public enum StyleColor {
    NONE(null),
    BLACK(LzFormatting.BLACK), DARK_BLUE(LzFormatting.DARK_BLUE), DARK_GREEN(LzFormatting.DARK_GREEN),
    DARK_AQUA(LzFormatting.DARK_AQUA), DARK_RED(LzFormatting.DARK_RED), DARK_PURPLE(LzFormatting.DARK_PURPLE),
    GOLD(LzFormatting.GOLD), GRAY(LzFormatting.GRAY), DARK_GRAY(LzFormatting.DARK_GRAY),
    BLUE(LzFormatting.BLUE), GREEN(LzFormatting.GREEN), AQUA(LzFormatting.AQUA),
    RED(LzFormatting.RED), LIGHT_PURPLE(LzFormatting.LIGHT_PURPLE), YELLOW(LzFormatting.YELLOW), WHITE(LzFormatting.WHITE);

    @Nullable private final LzFormatting formatting;

    StyleColor(@Nullable LzFormatting formatting) { this.formatting = formatting; }

    /** RGB for this color, or null for NONE. */
    @Nullable
    public Integer rgb() {
        return formatting != null ? formatting.mc().getColorValue() : null;
    }
}
