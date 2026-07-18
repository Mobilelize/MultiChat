package net.mobilelize.fabriclib.text;

import net.minecraft.text.Text;

/**
 * Stable wrapper for MC Text.
 * 1.21 (Yarn): net.minecraft.text.Text
 * 26.1 (Mojang): net.minecraft.network.chat.Component
 */
public class LzText {

    protected final Text text;

    public LzText(Text text) { this.text = text; }

    /** Raw MC Text — escape hatch. */
    public Text mc() { return text; }

    // 1.21: text.asOrderedText()   26.1: component.getVisualOrderText() (VERIFY)
    public LzOrderedText ordered() { return LzOrderedText.wrap(text.asOrderedText()); }

    public LzMutableText append(LzText other) {
        // 1.21: text.copy()   26.1: component.copy()
        var copy = text.copy();
        // unchanged: text.append(other)
        copy.append(other.mc());
        return new LzMutableText(copy);
    }

    public LzMutableText append(String other) {
        var copy = text.copy();
        copy.append(other);
        return new LzMutableText(copy);
    }

    // 1.21: Text.copy()   26.1: Component.copy()
    public LzMutableText copy() { return new LzMutableText(text.copy()); }

    public String string() { return text.getString(); }

    // ── Static factories ────────────────────────────────────────────────────

    /**
     * Converts UPPER_CASE enum name to Title Case.
     * e.g. "VERY_GOOD" → "Very Good"
     */
    public static String enumName(String name) {
        var parts = name.split("_");
        var sb = new StringBuilder();
        for (var part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)))
              .append(part.substring(1).toLowerCase())
              .append(" ");
        }
        return sb.toString().trim();
    }

    // 1.21: Text.literal(text)   26.1: Component.literal(text)
    public static LzMutableText literal(String text) { return new LzMutableText(Text.literal(text)); }

    // 1.21: Text.empty()   26.1: Component.empty()
    public static LzMutableText empty() { return new LzMutableText(Text.empty()); }

    // 1.21: Text.translatable(key)   26.1: Component.translatable(key)
    public static LzMutableText translatable(String key) { return new LzMutableText(Text.translatable(key)); }

    // 1.21: Text.translatable(key, args)   26.1: Component.translatable(key, args)
    public static LzMutableText translatable(String key, Object... args) { return new LzMutableText(Text.translatable(key, args)); }

    public static LzMutableText append(LzText a, LzText b) {
        var copy = a.mc().copy();
        copy.append(b.mc());
        return new LzMutableText(copy);
    }

    /** Wraps an existing MC Text — use only at mixin boundaries. */
    public static LzText wrapMc(Text text) { return new LzText(text); }

    // ── Format-code parser ──────────────────────────────────────────────────

    /**
     * Parses Minecraft-style color/format codes (&a, &l, &#RRGGBB, etc.)
     * into a styled LzMutableText.
     */
    public static LzMutableText format(String input) {
        var finalText = LzText.empty();
        var currentStyle = LzStyle.EMPTY;
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < input.length() && input.charAt(i + 1) == '#') {
                if (i + 8 <= input.length()) {
                    var hexPart = input.substring(i + 2, i + 8);
                    try {
                        int color = Integer.parseInt(hexPart, 16);
                        currentStyle = currentStyle.withColor(color);
                        i += 8;
                        continue;
                    } catch (NumberFormatException e) {
                        finalText.append(LzText.literal("&#").withStyle(currentStyle));
                        i += 2;
                        continue;
                    }
                } else {
                    finalText.append(LzText.literal("&#").withStyle(currentStyle));
                    i += 2;
                    continue;
                }
            } else if (c == '&' && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                currentStyle = LzFormatting.applyCode(currentStyle, code);
                i += 2;
                continue;
            }
            finalText.append(LzText.literal(String.valueOf(c)).withStyle(currentStyle));
            i++;
        }
        return finalText;
    }
}
