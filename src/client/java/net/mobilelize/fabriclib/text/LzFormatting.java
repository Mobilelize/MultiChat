package net.mobilelize.fabriclib.text;

import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

/**
 * Stable enum re-exporting MC color/formatting codes.
 * 1.21 (Yarn): net.minecraft.util.Formatting
 * 26.1 (Mojang): net.minecraft.ChatFormatting
 */
public enum LzFormatting {

    BLACK(ChatFormatting.BLACK),
    DARK_BLUE(ChatFormatting.DARK_BLUE),
    DARK_GREEN(ChatFormatting.DARK_GREEN),
    DARK_AQUA(ChatFormatting.DARK_AQUA),
    DARK_RED(ChatFormatting.DARK_RED),
    DARK_PURPLE(ChatFormatting.DARK_PURPLE),
    GOLD(ChatFormatting.GOLD),
    GRAY(ChatFormatting.GRAY),
    DARK_GRAY(ChatFormatting.DARK_GRAY),
    BLUE(ChatFormatting.BLUE),
    GREEN(ChatFormatting.GREEN),
    AQUA(ChatFormatting.AQUA),
    RED(ChatFormatting.RED),
    LIGHT_PURPLE(ChatFormatting.LIGHT_PURPLE),
    YELLOW(ChatFormatting.YELLOW),
    WHITE(ChatFormatting.WHITE),
    OBFUSCATED(ChatFormatting.OBFUSCATED),
    BOLD(ChatFormatting.BOLD),
    STRIKETHROUGH(ChatFormatting.STRIKETHROUGH),
    UNDERLINE(ChatFormatting.UNDERLINE),
    ITALIC(ChatFormatting.ITALIC),
    RESET(ChatFormatting.RESET);

    private final ChatFormatting formatting;

    LzFormatting(ChatFormatting formatting) { this.formatting = formatting; }

    /** Raw MC ChatFormatting — escape hatch. */
    public ChatFormatting mc() { return formatting; }

    /**
     * Applies a single lowercase format code character to the given style.
     * Color codes 0-9 / a-f map to ChatFormatting constants.
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

    // 1.21: Formatting.XXX   26.1: ChatFormatting.XXX
    @Nullable
    private static ChatFormatting fromColorCode(char code) {
        return switch (code) {
            case '0' -> ChatFormatting.BLACK;
            case '1' -> ChatFormatting.DARK_BLUE;
            case '2' -> ChatFormatting.DARK_GREEN;
            case '3' -> ChatFormatting.DARK_AQUA;
            case '4' -> ChatFormatting.DARK_RED;
            case '5' -> ChatFormatting.DARK_PURPLE;
            case '6' -> ChatFormatting.GOLD;
            case '7' -> ChatFormatting.GRAY;
            case '8' -> ChatFormatting.DARK_GRAY;
            case '9' -> ChatFormatting.BLUE;
            case 'a' -> ChatFormatting.GREEN;
            case 'b' -> ChatFormatting.AQUA;
            case 'c' -> ChatFormatting.RED;
            case 'd' -> ChatFormatting.LIGHT_PURPLE;
            case 'e' -> ChatFormatting.YELLOW;
            case 'f' -> ChatFormatting.WHITE;
            default  -> null;
        };
    }
}
