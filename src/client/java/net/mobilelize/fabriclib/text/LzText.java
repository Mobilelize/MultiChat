package net.mobilelize.fabriclib.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Stable wrapper for MC Component.
 * 1.21 (Yarn): net.minecraft.text.Text
 * 26.1 (Mojang): net.minecraft.network.chat.Component
 */
public class LzText {

    protected final Component text;

    public LzText(Component text) { this.text = text; }

    /** Raw MC Component — escape hatch. */
    public Component mc() { return text; }

    // 1.21: text.asOrderedText()   26.1: component.getVisualOrderText()
    public LzOrderedText ordered() { return LzOrderedText.wrap(text.getVisualOrderText()); }

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

    // MC-VERSION-SENSITIVE: ObjectTextContent added MC 1.21.9. Delete this method (and its
    // call sites) outright when porting to an older branch — there's nothing to strip there.
    // Mods that embed an inline "object" in chat (e.g. chat_heads' player-head sprite) make
    // Text.getString() resolve to junk like "<[Bob head]Bob> hi", which breaks filter/search
    // matching. This finds each ObjectTextContent node's own fallback string (its content
    // only, not siblings) and cuts those substrings back out of the fully-resolved string.
    public static String stringSkippingObjects(Component text) {
        String full = text.getString();
        List<String> fallbacks = new ArrayList<>();
        collectObjectFallbacks(text, fallbacks);
        for (String fallback : fallbacks) {
            if (fallback.isEmpty()) continue;
            int idx = full.indexOf(fallback);
            if (idx >= 0) full = full.substring(0, idx) + full.substring(idx + fallback.length());
        }
        return full;
    }

    private static void collectObjectFallbacks(Component text, List<String> out) {
        ComponentContents content = text.getContents();
        if (content instanceof ObjectContents) {
            StringBuilder sb = new StringBuilder();
            content.visit(s -> { sb.append(s); return Optional.empty(); });
            if (!sb.isEmpty()) out.add(sb.toString());
        } else if (content instanceof TranslatableContents translatable) {
            for (Object arg : translatable.getArgs())
                if (arg instanceof Component argText) collectObjectFallbacks(argText, out);
        }
        for (Component sibling : text.getSiblings()) collectObjectFallbacks(sibling, out);
    }

    // MC-VERSION-SENSITIVE: ObjectTextContent added MC 1.21.9. Delete this method (and its call
    // sites) outright when porting to an older branch. For a caller that needs to REBUILD a Text
    // tree from Text.getString() (e.g. HighlightEngine.reformat, which reformats a message into a
    // highlight-colored copy) — unlike stringSkippingObjects, which just removes the junk for
    // matching purposes, this locates each object's own fallback-string range within getString()
    // so the caller can splice the real node back in there instead of literal-copying the junk.
    public record ObjectSpan(int start, int end, Component node) {}

    public static List<ObjectSpan> objectSpans(Component text) {
        String full = text.getString();
        List<Component> nodes = new ArrayList<>();
        collectObjectNodes(text, nodes);
        List<ObjectSpan> spans = new ArrayList<>();
        int searchFrom = 0;
        for (Component node : nodes) {
            StringBuilder sb = new StringBuilder();
            node.getContents().visit(s -> { sb.append(s); return Optional.empty(); });
            String fallback = sb.toString();
            if (fallback.isEmpty()) continue;
            int idx = full.indexOf(fallback, searchFrom);
            if (idx < 0) continue; // shouldn't happen, but never splice at a made-up position
            spans.add(new ObjectSpan(idx, idx + fallback.length(), node));
            searchFrom = idx + fallback.length();
        }
        return spans;
    }

    private static void collectObjectNodes(Component text, List<Component> out) {
        ComponentContents content = text.getContents();
        if (content instanceof ObjectContents) {
            out.add(text);
        } else if (content instanceof TranslatableContents translatable) {
            for (Object arg : translatable.getArgs())
                if (arg instanceof Component argText) collectObjectNodes(argText, out);
        }
        for (Component sibling : text.getSiblings()) collectObjectNodes(sibling, out);
    }

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
    public static LzMutableText literal(String text) { return new LzMutableText(Component.literal(text)); }

    // 1.21: Text.empty()   26.1: Component.empty()
    public static LzMutableText empty() { return new LzMutableText(Component.empty()); }

    // 1.21: Text.translatable(key)   26.1: Component.translatable(key)
    public static LzMutableText translatable(String key) { return new LzMutableText(Component.translatable(key)); }

    // 1.21: Text.translatable(key, args)   26.1: Component.translatable(key, args)
    public static LzMutableText translatable(String key, Object... args) { return new LzMutableText(Component.translatable(key, args)); }

    public static LzMutableText append(LzText a, LzText b) {
        var copy = a.mc().copy();
        copy.append(b.mc());
        return new LzMutableText(copy);
    }

    /** Wraps an existing MC Component — use only at mixin boundaries. */
    public static LzText wrapMc(Component text) { return new LzText(text); }

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
