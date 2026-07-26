package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.text.LzMutableText;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzStyle;
import net.mobilelize.fabriclib.text.LzText;

import java.util.List;

public class ChatSelection {

    private static int       anchorLine = -1, anchorChar = -1;
    private static int       cursorLine = -1, cursorChar = -1;
    private static ChatPanel selPanel   = null;
    private static boolean   selecting  = false;

    // -----------------------------------------------------------------------
    // State mutation
    // -----------------------------------------------------------------------

    public static void start(ChatPanel panel, int line, int charIdx) {
        selPanel   = panel;
        anchorLine = line;
        anchorChar = charIdx;
        cursorLine = line;
        cursorChar = charIdx;
        selecting  = true;
    }

    public static void extend(ChatPanel panel, int line, int charIdx) {
        if (!selecting || panel != selPanel) return;
        cursorLine = line;
        cursorChar = charIdx;
    }

    public static void endDrag()  { selecting = false; }
    public static void clear()    { anchorLine = -1; cursorLine = -1; selecting = false; selPanel = null; }

    public static void shiftLines(ChatTab tab, int delta) {
        if (selPanel == null || selPanel.getActiveTab() != tab || delta == 0) return;
        anchorLine += delta;
        cursorLine += delta;
    }

    public static void clampOrClear(ChatTab tab) {
        if (selPanel == null || selPanel.getActiveTab() != tab) return;
        List<LzChatLine> _l = selPanel.getTabLines(tab);
        int max = (_l != null ? _l.size() : 0) - 1;
        if (anchorLine > max || cursorLine > max || anchorLine < 0 || cursorLine < 0)
            clear();
    }

    public static boolean hasSelection() {
        return anchorLine >= 0 && cursorLine >= 0
            && (anchorLine != cursorLine || anchorChar != cursorChar);
    }

    public static boolean isSelecting() { return selecting; }
    public static ChatPanel getPanel()  { return selPanel; }

    // -----------------------------------------------------------------------
    // Per-line highlight rect (called from renderAt, already in chat matrix)
    // -----------------------------------------------------------------------

    public static int[] lineHighlight(LzChatLine line, int lineIdx, ChatPanel panel) {
        if (!hasSelection() || selPanel != panel) return null;
        int[] n = norm();
        int tl = n[0], bl = n[1], tc = n[2], bc = n[3];
        if (lineIdx < bl || lineIdx > tl) return null;

        LzOrderedText content = line.content();
        int x0, x1;
        if (tl == bl) {
            x0 = (int) xOfChar(content, tc);
            x1 = (int) xOfChar(content, bc);
        } else if (lineIdx == tl) {
            x0 = (int) xOfChar(content, tc);
            x1 = LzDraw.textWidth(content);
        } else if (lineIdx == bl) {
            x0 = 0;
            x1 = (int) xOfChar(content, bc);
        } else {
            x0 = 0;
            x1 = LzDraw.textWidth(content);
        }

        if (x1 <= x0) return null;
        return new int[]{x0, x1};
    }

    // -----------------------------------------------------------------------
    // Text extraction
    // -----------------------------------------------------------------------

    public static String getSelectedText() {
        if (!hasSelection() || selPanel == null) return "";
        ChatTab tab = selPanel.getActiveTab();
        if (tab == null) return "";
        List<LzChatLine> msgs = selPanel.getTabLines(tab);
        if (msgs == null) return "";
        int[] n = norm();
        int tl = n[0], bl = n[1], tc = n[2], bc = n[3];

        boolean useOriginal = MultichatClient.CONFIG.selectionUsesOriginalText;

        StringBuilder sb = new StringBuilder();
        int s = tl;
        while (s >= bl) {
            if (s < 0 || s >= msgs.size()) { s--; continue; }
            LzChatLine  line  = msgs.get(s);
            LzChatEntry entry = line.entry();

            if (useOriginal && entry != null) {
                int groupTop = s;
                while (groupTop + 1 < msgs.size() && msgs.get(groupTop + 1).entry() == entry) groupTop++;
                int groupBottom = s;
                while (groupBottom - 1 >= 0 && msgs.get(groupBottom - 1).entry() == entry) groupBottom--;

                appendEntryRange(sb, msgs, entry, groupTop, groupBottom, tl, tc, bl, bc);

                if (bl < groupBottom) sb.append('\n');
                s = groupBottom - 1;
                continue;
            }

            int cStart = (s == tl) ? tc : 0;
            int cEnd   = (s == bl) ? bc : Integer.MAX_VALUE;
            appendChars(sb, line.content(), cStart, cEnd);
            if (s > bl) sb.append('\n');
            s--;
        }
        return sb.toString();
    }

    /**
     * Appends the slice of {@code entry}'s logical text (timestamp prefix + original pre-wrap
     * text + collapse-counter suffix, in that order) that corresponds to the selection,
     * approximating offsets by summing wrapped-line codepoint counts. Word-wrap drops the
     * breaking space, so this can drift by a character per wrap point within the entry —
     * acceptable, and far better than stitching wrapped fragments verbatim.
     * <p>
     * The timestamp/collapse pieces aren't stored anywhere retrievable (and can't be
     * reconstructed from current config — the format may have changed since this message was
     * stamped, or a tab's "show timestamps" setting may have been toggled after the fact), so
     * prefix/suffix are found structurally: {@code raw} is located inside the actual rendered
     * group text by matching, and whatever sits before/after it is the prefix/suffix — no
     * assumptions about their content.
     */
    private static void appendEntryRange(StringBuilder sb, List<LzChatLine> msgs, LzChatEntry entry,
                                          int groupTop, int groupBottom,
                                          int tl, int tc, int bl, int bc) {
        int[] raw   = stripSectionPairs(entry.originalContent.mc().getString().codePoints().toArray());
        int[] group = groupCodepoints(msgs, groupTop, groupBottom);

        int prefixLen = findRawStart(group, raw);
        int suffixLen = Math.max(0, group.length - prefixLen - raw.length);

        int fullLen = prefixLen + raw.length + suffixLen;

        int start = (tl > groupTop) ? 0 : codepointsBefore(msgs, groupTop, tl) + tc;
        int end   = (bl < groupBottom) ? fullLen : codepointsBefore(msgs, groupTop, bl) + bc;

        start = Math.max(0, Math.min(start, fullLen));
        end   = Math.max(start, Math.min(end, fullLen));

        for (int i = start; i < end; i++) {
            // prefix/suffix regions read straight from `group` (their real on-screen text);
            // the middle region substitutes `raw` to avoid wrap-dropped-space artifacts.
            if (i >= prefixLen && i < prefixLen + raw.length) sb.appendCodePoint(raw[i - prefixLen]);
            else sb.appendCodePoint(group[i]);
        }
    }

    /** Concatenates rendered codepoints of every wrapped line in the entry's group, top to bottom. */
    private static int[] groupCodepoints(List<LzChatLine> msgs, int groupTop, int groupBottom) {
        int[][] perLine = new int[groupTop - groupBottom + 1][];
        int total = 0;
        for (int i = groupTop; i >= groupBottom; i--) {
            int[] cps = collectCodepoints(msgs.get(i).content());
            perLine[groupTop - i] = cps;
            total += cps.length;
        }
        int[] out = new int[total];
        int pos = 0;
        for (int[] cps : perLine) { System.arraycopy(cps, 0, out, pos, cps.length); pos += cps.length; }
        return out;
    }

    /**
     * Finds the offset in {@code group} where {@code raw} begins, by matching a short anchor
     * (raw's own first codepoints) against every position. Returns 0 (no prefix) if no match is
     * found — e.g. the message predates timestamps being enabled.
     */
    private static int findRawStart(int[] group, int[] raw) {
        if (raw.length == 0) return 0;
        int anchor = Math.min(raw.length, 8);
        outer:
        for (int i = 0; i <= group.length - anchor; i++) {
            for (int k = 0; k < anchor; k++)
                if (group[i + k] != raw[k]) continue outer;
            return i;
        }
        return 0;
    }

    /** Sums codepoint lengths of lines with index in (boundaryIdx, groupTop]. */
    private static int codepointsBefore(List<LzChatLine> msgs, int groupTop, int boundaryIdx) {
        int total = 0;
        for (int i = groupTop; i > boundaryIdx; i--) total += codepointLength(msgs.get(i).content());
        return total;
    }

    private static int codepointLength(LzOrderedText text) {
        int[] count = {0};
        visitVisible(text, (idx, style, cp) -> { count[0]++; return true; });
        return count[0];
    }

    // -----------------------------------------------------------------------
    // Hit testing — mouse → line index and char index
    // -----------------------------------------------------------------------

    public static int hitLine(ChatPanel panel, double mouseX, double mouseY,
                               int visibleLineCount, float scale, int lineHeight,
                               int windowHeight, int chatWidth, int xOffset) {
        double localX = mouseX / scale - 4.0 - xOffset;
        if (localX < -4.0 || localX > chatWidth) return -1;
        int topY = panel.resolveTopY(windowHeight);
        double chatY = (topY * (double) scale - mouseY) / (scale * lineHeight);
        if (chatY < 0.0) return -1;
        ChatTab active = panel.getActiveTab();
        if (active == null) return -1;
        int scrolled = panel.getScrolledLines(active);
        int idx = LzMath.floor(chatY + scrolled);
        List<LzChatLine> panelMsgs = panel.getTabLines(active);
        if (panelMsgs == null || idx < 0 || idx >= panelMsgs.size()) return -1;
        if (idx >= visibleLineCount + scrolled) return -1;
        return idx;
    }

    // -----------------------------------------------------------------------
    // Word selection
    // -----------------------------------------------------------------------

    /**
     * Sets selection to the word at charIdx on lineIdx.
     * nameSelect=true: word chars are [a-zA-Z0-9_] (browser default).
     * nameSelect=false: word chars are any non-whitespace (token mode).
     */
    public static void selectWord(ChatPanel panel, int lineIdx, int charIdx, boolean nameSelect) {
        ChatTab tab = panel.getActiveTab();
        if (tab == null) return;
        List<LzChatLine> msgs = panel.getTabLines(tab);
        if (msgs == null || lineIdx < 0 || lineIdx >= msgs.size()) return;
        LzOrderedText content = msgs.get(lineIdx).content();
        int[] bounds = wordBoundaries(content, charIdx, nameSelect);
        if (bounds[0] == bounds[1]) return;
        selPanel   = panel;
        anchorLine = lineIdx;
        anchorChar = bounds[0];
        cursorLine = lineIdx;
        cursorChar = bounds[1];
        selecting  = false;
    }

    /** Returns {wordStart, wordEnd} char indices for the word containing charIdx. */
    public static int[] wordBoundaries(LzOrderedText text, int charIdx, boolean nameSelect) {
        int[] codepoints = collectCodepoints(text);
        if (codepoints.length == 0) return new int[]{0, 0};
        int idx = Math.min(charIdx, codepoints.length - 1);

        if (!isWordChar(codepoints[idx], nameSelect)) return new int[]{charIdx, charIdx};

        int start = idx;
        while (start > 0 && isWordChar(codepoints[start - 1], nameSelect)) start--;

        int end = idx;
        while (end < codepoints.length && isWordChar(codepoints[end], nameSelect)) end++;

        return new int[]{start, end};
    }

    private static boolean isWordChar(int cp, boolean nameSelect) {
        if (nameSelect) return Character.isLetterOrDigit(cp) || cp == '_';
        return !Character.isWhitespace(cp);
    }

    private static int[] collectCodepoints(LzOrderedText text) {
        java.util.List<Integer> list = new java.util.ArrayList<>();
        visitVisible(text, (idx, style, cp) -> { list.add(cp); return true; });
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    /** Style at a given local x on a line — same glyph-walk geometry as charIndexAtX/xOfChar. */
    public static LzStyle styleAtX(LzOrderedText text, float targetX) {
        if (targetX < 0f) return null;
        float[]  cumX  = {0f};
        LzStyle[] found = {null};
        text.lzAccept((idx, style, cp) -> {
            float w = glyphWidth(style, cp);
            if (targetX < cumX[0] + w) {
                found[0] = style;
                return false;
            }
            cumX[0] += w;
            return true;
        });
        return found[0];
    }

    public static int charIndexAtX(LzOrderedText text, float targetX) {
        if (targetX <= 0f) return 0;
        int[]   count = {0};
        float[] cumX  = {0f};
        visitVisible(text, (idx, style, cp) -> {
            float w = glyphWidth(style, cp);
            if (cumX[0] + w * 0.5f >= targetX) return false;
            cumX[0] += w;
            count[0]++;
            return true;
        });
        return count[0];
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static int[] norm() {
        if (anchorLine == cursorLine) {
            return new int[]{ anchorLine, anchorLine,
                Math.min(anchorChar, cursorChar), Math.max(anchorChar, cursorChar) };
        } else if (anchorLine > cursorLine) {
            return new int[]{ anchorLine, cursorLine, anchorChar, cursorChar };
        } else {
            return new int[]{ cursorLine, anchorLine, cursorChar, anchorChar };
        }
    }

    private static float xOfChar(LzOrderedText text, int charIdx) {
        if (charIdx <= 0) return 0f;
        float[] x     = {0f};
        int[]   count = {0};
        visitVisible(text, (idx, style, cp) -> {
            if (count[0] >= charIdx) return false;
            x[0] += glyphWidth(style, cp);
            count[0]++;
            return true;
        });
        return x[0];
    }

    private static void appendChars(StringBuilder sb, LzOrderedText text, int start, int end) {
        int[] count = {0};
        visitVisible(text, (idx, style, cp) -> {
            if (count[0] >= end) return false;
            if (count[0] >= start) sb.appendCodePoint(cp);
            count[0]++;
            return true;
        });
    }

    private static float glyphWidth(LzStyle style, int cp) {
        LzMutableText t = LzText.literal(new String(Character.toChars(cp))).withStyle(style);
        return LzDraw.textWidth(t);
    }

    // Legacy "§" formatting codes that leak through as literal characters (rather than real
    // Style, e.g. a plugin-built prefix like "§b[MVP§b+]") render as nothing on screen. "§"
    // itself NEVER renders — MC's font never draws section-sign, valid code or not — while the
    // character right after it only ALSO disappears when it's one of MC's real format codes
    // (an invalid one like "§y" leaves the "y" literal and visible). Our own codepoint walk
    // doesn't know either half of that, so "§" always drifts hit-testing/selection by at least
    // 1 index and leaks into copied text, plus 1 more when it's swallowing a valid code letter.
    private static final int SECTION_SIGN = 0x00A7;

    private static boolean isFormatCode(int cp) {
        return (cp >= '0' && cp <= '9')
            || (cp >= 'a' && cp <= 'f') || (cp >= 'A' && cp <= 'F')
            || (cp >= 'k' && cp <= 'o') || (cp >= 'K' && cp <= 'O')
            || cp == 'r' || cp == 'R'
            || cp == 'x' || cp == 'X';
    }

    // Buffers first (needs to peek one codepoint ahead of "§" to know whether to skip it),
    // then replays the filtered sequence through the real visitor with correct running indices.
    private static boolean visitVisible(LzOrderedText text, LzOrderedText.LzCharacterVisitor visitor) {
        List<Integer> cps    = new java.util.ArrayList<>();
        List<LzStyle> styles = new java.util.ArrayList<>();
        text.lzAccept((i, style, cp) -> { cps.add(cp); styles.add(style); return true; });

        int outIdx = 0;
        for (int i = 0; i < cps.size(); i++) {
            int cp = cps.get(i);
            if (cp == SECTION_SIGN) {
                if (i + 1 < cps.size() && isFormatCode(cps.get(i + 1))) i++; // swallow the code letter too
                continue; // "§" itself is always invisible
            }
            if (!visitor.accept(outIdx++, styles.get(i), cp)) return false;
        }
        return true;
    }

    private static int[] stripSectionPairs(int[] codepoints) {
        java.util.List<Integer> out = new java.util.ArrayList<>(codepoints.length);
        for (int i = 0; i < codepoints.length; i++) {
            int cp = codepoints[i];
            if (cp == SECTION_SIGN) {
                if (i + 1 < codepoints.length && isFormatCode(codepoints[i + 1])) i++;
                continue;
            }
            out.add(cp);
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }
}
