package net.mobilelize.fabriclib.text;

import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Stable enum re-exporting MC color/formatting codes.
 * 1.21 (Yarn): net.minecraft.util.Formatting
 * 26.1 (Mojang): net.minecraft.ChatFormatting
 */
public enum LzFormatting {

    BLACK(Formatting.BLACK),
    DARK_BLUE(Formatting.DARK_BLUE),
    DARK_GREEN(Formatting.DARK_GREEN),
    DARK_AQUA(Formatting.DARK_AQUA),
    DARK_RED(Formatting.DARK_RED),
    DARK_PURPLE(Formatting.DARK_PURPLE),
    GOLD(Formatting.GOLD),
    GRAY(Formatting.GRAY),
    DARK_GRAY(Formatting.DARK_GRAY),
    BLUE(Formatting.BLUE),
    GREEN(Formatting.GREEN),
    AQUA(Formatting.AQUA),
    RED(Formatting.RED),
    LIGHT_PURPLE(Formatting.LIGHT_PURPLE),
    YELLOW(Formatting.YELLOW),
    WHITE(Formatting.WHITE),
    OBFUSCATED(Formatting.OBFUSCATED),
    BOLD(Formatting.BOLD),
    STRIKETHROUGH(Formatting.STRIKETHROUGH),
    UNDERLINE(Formatting.UNDERLINE),
    ITALIC(Formatting.ITALIC),
    RESET(Formatting.RESET);

    private final Formatting formatting;

    LzFormatting(Formatting formatting) { this.formatting = formatting; }

    /** Raw MC Formatting — escape hatch. */
    public Formatting mc() { return formatting; }

    /**
     * Applies a single lowercase format code character to the given style.
     * Color codes 0-9 / a-f map to Formatting constants.
     * Style codes k-o / r are applied directly.
     * 1.21: Formatting constants — 26.1: ChatFormatting  (VERIFY)
     */
    public static LzStyle applyCode(LzStyle current, char code) {
        var f = fromColorCode(code);
        if (f != null) return current.withFormatting(f);
        return switch (code) {
            case 'k' -> current.withObfuscated(true);
            case 'l' -> current.withBold(true);
            case 'm' -> current.withStrikethrough(true);
            case 'n' -> current.withUnderline(true);
            case 'o' -> current.withItalic(true);
            case 'r' -> LzStyle.EMPTY;
            default  -> current;
        };
    }

    /** RGB for a legacy color code (0-9/a-f, case-insensitive), or null if the code isn't a color
     *  (e.g. style codes k-o/r have no RGB). */
    @Nullable
    public static Integer colorRgbForCode(char code) {
        Formatting f = fromColorCode(Character.toLowerCase(code));
        return f != null ? f.getColorValue() : null;
    }

    // 1.21: Formatting.XXX   26.1: ChatFormatting.XXX
    @Nullable
    private static Formatting fromColorCode(char code) {
        return switch (code) {
            case '0' -> Formatting.BLACK;
            case '1' -> Formatting.DARK_BLUE;
            case '2' -> Formatting.DARK_GREEN;
            case '3' -> Formatting.DARK_AQUA;
            case '4' -> Formatting.DARK_RED;
            case '5' -> Formatting.DARK_PURPLE;
            case '6' -> Formatting.GOLD;
            case '7' -> Formatting.GRAY;
            case '8' -> Formatting.DARK_GRAY;
            case '9' -> Formatting.BLUE;
            case 'a' -> Formatting.GREEN;
            case 'b' -> Formatting.AQUA;
            case 'c' -> Formatting.RED;
            case 'd' -> Formatting.LIGHT_PURPLE;
            case 'e' -> Formatting.YELLOW;
            case 'f' -> Formatting.WHITE;
            default  -> null;
        };
    }
}
