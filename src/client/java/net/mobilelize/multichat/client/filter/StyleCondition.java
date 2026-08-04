package net.mobilelize.multichat.client.filter;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzStyle;
import net.mobilelize.fabriclib.text.LzText;

import java.util.ArrayList;
import java.util.List;

/**
 * A single "does this location/word render with this style" check — deliberately its own thing, not a
 * FilterRule variant. FilterRule matches plain text; this matches the actual rendered style, which needs
 * the original styled Text, not the flattened string every FilterRule works from. See StyleFilterSet for
 * how a tab combines several of these (as separate whitelist/blacklist lists, mirroring FilterRuleSet).
 *
 * @param target       WORD, START_OF_MESSAGE, or END_OF_MESSAGE.
 * @param word         the word/substring to find, case-insensitive. Used for WORD; ignored otherwise.
 *                     For WORD + START/END/INDEX, leave blank to check whatever word happens to be there
 *                     without caring what it is, or set it to require that specific word be there too.
 *                     For WORD + ANYWHERE, leave blank to check for the style's existence anywhere in the
 *                     message (at least one character) instead of searching for a specific word — useful
 *                     for things like HAS_CLICK, where only a small part of the message (a link, a name)
 *                     is ever actually styled, not the whole thing.
 * @param wordLocation only meaningful when target == WORD — where the word is allowed to be found.
 * @param wordIndex    only meaningful when wordLocation == INDEX — 1-based (1 = first word, 2 = second, etc.).
 * @param flags        every flag here must be present (AND) — e.g. [BOLD, ITALIC] requires both at once.
 *                     Can be empty if only color matters. Formatting flags (BOLD, ITALIC, etc.) must hold
 *                     uniformly across the whole checked range — the entire word/location, not just part of
 *                     it. Interaction flags (HAS_CLICK, HAS_HOVER, and the CLICK_/HOVER_ specific ones)
 *                     instead only need to be true for SOME character in range, since a clickable name is
 *                     often a substring of the tokenized word (e.g. "&lt;Steve&gt;" — only "Steve" carries
 *                     the ClickEvent, not the brackets); requiring the whole token to be uniformly clickable
 *                     would rarely match.
 * @param colors       each character in range must be one of these named colors OR hexColors (not
 *                     necessarily the SAME one across the whole range) — specifically so a multi-colored
 *                     word (different color per character) can still match as long as every color it uses
 *                     is somewhere in colors/hexColors. Both empty = don't check color at all.
 * @param hexColors    raw RGB values, for colors outside the 16 named ones. Combined with colors (a
 *                     character matches if it's in either list).
 */
public record StyleCondition(StyleTarget target, String word, WordLocation wordLocation, int wordIndex,
                              List<StyleFlag> flags, List<StyleColor> colors, List<Integer> hexColors) {

    public boolean matches(LzText text) {
        if (text == null) return false;
        if (flags.isEmpty() && colors.isEmpty() && hexColors.isEmpty()) return false; // nothing to check — never matches

        StringBuilder plain = new StringBuilder();
        List<LzStyle> styles = new ArrayList<>();
        LzOrderedText.of(text).lzAccept((idx, style, cp) -> {
            plain.appendCodePoint(cp);
            styles.add(style);
            return true;
        });
        String s = plain.toString();
        if (s.isEmpty()) return false;

        // Words are whitespace-*run* delimited, not single-space — chat messages aren't guaranteed to
        // have exactly one space between words (leading/trailing/doubled spaces are all real cases).
        List<int[]> words = findWords(s);

        // WORD + ANYWHERE + blank word has no anchor to pin a range to — "anywhere" with nothing to
        // search for reads most naturally as an *existence* check (does this style appear anywhere in
        // the message at all), not a "does the whole message uniformly have it" check. Every other case
        // below checks a specific, bounded span (a word, or the message's first/last word) and requires
        // EVERY character in that span to satisfy the condition — that's correct there since the span is
        // a deliberate, precise target. Existence is different on purpose: for something like a link or a
        // name in the middle of a longer message, requiring the *entire* message to be uniformly styled
        // would almost never match.
        WordLocation loc = wordLocation != null ? wordLocation : WordLocation.ANYWHERE;
        if (target == StyleTarget.WORD && loc == WordLocation.ANYWHERE && (word == null || word.isEmpty())) {
            for (LzStyle style : styles) if (characterMatches(style)) return true;
            return false;
        }

        int start, end; // char range to check, [start, end)
        switch (target) {
            case START_OF_MESSAGE -> {
                if (words.isEmpty()) return false;
                int[] w = words.getFirst(); // whole first word, not just its first character
                start = w[0];
                end = w[1];
            }
            case END_OF_MESSAGE -> {
                if (words.isEmpty()) return false;
                int[] w = words.getLast(); // whole last word, not just its last character
                start = w[0];
                end = w[1];
            }
            case WORD -> {
                int[] range = findWordRange(s, words);
                if (range == null) return false;
                start = range[0];
                end = range[1];
            }
            default -> { return false; }
        }
        if (start < 0 || end > styles.size() || start >= end) return false;

        // Formatting/color must hold uniformly across the whole range — that's what makes the spoof-guard
        // use case work (an entire "[S]" prefix must genuinely render italic, not just one character of it).
        for (int i = start; i < end; i++) {
            if (!characterMatchesFormat(styles.get(i))) return false;
        }
        // Interaction flags (click/hover) are checked differently: existence within the range, not
        // uniformity. A clickable name is often a *substring* of the tokenized word (e.g. "<Steve>" —
        // only "Steve" carries the ClickEvent, not the brackets), so requiring every character in the
        // whole word to be clickable would almost never match.
        for (StyleFlag f : flags) {
            if (!isInteractionFlag(f)) continue;
            boolean found = false;
            for (int i = start; i < end; i++) if (hasFlag(styles.get(i), f)) { found = true; break; }
            if (!found) return false;
        }
        return true;
    }

    /** Does this one character's style satisfy every non-interaction flag (AND) and — if either color list
     *  is non-empty — is its color in colors OR hexColors. Interaction flags (click/hover) are handled
     *  separately since they're checked for existence within a range, not uniformity — see matches(). */
    private boolean characterMatchesFormat(LzStyle style) {
        for (StyleFlag f : flags) {
            if (isInteractionFlag(f)) continue;
            if (!hasFlag(style, f)) return false;
        }
        if (!colors.isEmpty() || !hexColors.isEmpty()) {
            var mc = style.mc().getColor();
            if (mc == null) return false;
            boolean inList = false;
            for (StyleColor c : colors) {
                Integer rgb = c.rgb();
                if (rgb != null && mc.getRgb() == rgb) { inList = true; break; }
            }
            if (!inList) for (int hex : hexColors) {
                if (mc.getRgb() == (hex & 0xFFFFFF)) { inList = true; break; }
            }
            if (!inList) return false;
        }
        return true;
    }

    /** Does this one character's style satisfy every flag (AND, interaction flags included) and — if either
     *  color list is non-empty — is its color in colors OR hexColors. Used for the WORD + ANYWHERE + blank
     *  "existence anywhere in the message" case, where there's no range to check uniformity vs. existence
     *  separately — one character just needs to satisfy everything at once. */
    private boolean characterMatches(LzStyle style) {
        for (StyleFlag f : flags) if (!hasFlag(style, f)) return false;
        return characterMatchesFormat(style);
    }

    private static boolean isInteractionFlag(StyleFlag f) {
        return switch (f) {
            case HAS_CLICK, HAS_HOVER, CLICK_RUN_COMMAND, CLICK_SUGGEST_COMMAND,
                 CLICK_OPEN_URL, CLICK_COPY_TO_CLIPBOARD, HOVER_SHOW_TEXT -> true;
            default -> false;
        };
    }

    /** Splits into [start, end) ranges of maximal non-whitespace runs — any amount/kind of whitespace
     *  between, before, or after words is treated as a delimiter, never as part of a word. */
    private static List<int[]> findWords(String s) {
        List<int[]> words = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(s.charAt(i))) i++;
            if (i >= n) break;
            int start = i;
            while (i < n && !Character.isWhitespace(s.charAt(i))) i++;
            words.add(new int[]{start, i});
        }
        return words;
    }

    /** Resolves target==WORD to a [start, end) char range, or null if it doesn't match. */
    private int[] findWordRange(String s, List<int[]> words) {
        WordLocation loc = wordLocation != null ? wordLocation : WordLocation.ANYWHERE;
        return switch (loc) {
            case START -> {
                if (words.isEmpty()) yield null;
                int[] w = words.getFirst();
                if (word != null && !word.isEmpty() && !s.substring(w[0], w[1]).equalsIgnoreCase(word)) yield null;
                yield w;
            }
            case END -> {
                if (words.isEmpty()) yield null;
                int[] w = words.getLast();
                if (word != null && !word.isEmpty() && !s.substring(w[0], w[1]).equalsIgnoreCase(word)) yield null;
                yield w;
            }
            case INDEX -> {
                if (wordIndex < 1 || wordIndex > words.size()) yield null;
                int[] w = words.get(wordIndex - 1);
                if (word != null && !word.isEmpty() && !s.substring(w[0], w[1]).equalsIgnoreCase(word)) yield null;
                yield w;
            }
            case ANYWHERE -> {
                // blank word here is handled earlier in matches() as an existence check — never reaches
                // this branch with an empty word.
                int idx = s.toLowerCase().indexOf(word.toLowerCase());
                if (idx < 0) yield null;
                yield new int[]{idx, idx + word.length()};
            }
        };
    }

    private boolean hasFlag(LzStyle style, StyleFlag f) {
        var mc = style.mc();
        return switch (f) {
            case BOLD          -> mc.isBold();
            case ITALIC        -> mc.isItalic();
            case UNDERLINE     -> mc.isUnderlined();
            case STRIKETHROUGH -> mc.isStrikethrough();
            case OBFUSCATED    -> mc.isObfuscated();
            case HAS_CLICK              -> mc.getClickEvent() != null;
            case HAS_HOVER              -> mc.getHoverEvent() != null;
            case CLICK_RUN_COMMAND      -> mc.getClickEvent() instanceof ClickEvent.RunCommand;
            case CLICK_SUGGEST_COMMAND  -> mc.getClickEvent() instanceof ClickEvent.SuggestCommand;
            case CLICK_OPEN_URL         -> mc.getClickEvent() instanceof ClickEvent.OpenUrl;
            case CLICK_COPY_TO_CLIPBOARD -> mc.getClickEvent() instanceof ClickEvent.CopyToClipboard;
            case HOVER_SHOW_TEXT        -> mc.getHoverEvent() instanceof HoverEvent.ShowText;
        };
    }
}
