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
     * Appends the slice of {@code entry}'s original (pre-wrap) text that corresponds to the
     * selection, approximating raw offsets by summing wrapped-line codepoint counts. Word-wrap
     * drops the breaking space, so this can drift by a character per wrap point within the entry —
     * acceptable, and far better than stitching wrapped fragments verbatim.
     */
    private static void appendEntryRange(StringBuilder sb, List<LzChatLine> msgs, LzChatEntry entry,
                                          int groupTop, int groupBottom,
                                          int tl, int tc, int bl, int bc) {
        String raw    = entry.originalContent.mc().getString();
        int    rawLen = raw.codePointCount(0, raw.length());

        int rawStart = (tl > groupTop) ? 0 : codepointsBefore(msgs, groupTop, tl) + tc;
        int rawEnd   = (bl < groupBottom) ? rawLen : codepointsBefore(msgs, groupTop, bl) + bc;

        rawStart = Math.max(0, Math.min(rawStart, rawLen));
        rawEnd   = Math.max(rawStart, Math.min(rawEnd, rawLen));

        sb.append(raw, raw.offsetByCodePoints(0, rawStart), raw.offsetByCodePoints(0, rawEnd));
    }

    /** Sums codepoint lengths of lines with index in (boundaryIdx, groupTop]. */
    private static int codepointsBefore(List<LzChatLine> msgs, int groupTop, int boundaryIdx) {
        int total = 0;
        for (int i = groupTop; i > boundaryIdx; i--) total += codepointLength(msgs.get(i).content());
        return total;
    }

    private static int codepointLength(LzOrderedText text) {
        int[] count = {0};
        text.lzAccept((idx, style, cp) -> { count[0]++; return true; });
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
        text.lzAccept((idx, style, cp) -> { list.add(cp); return true; });
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    public static int charIndexAtX(LzOrderedText text, float targetX) {
        if (targetX <= 0f) return 0;
        int[]   count = {0};
        float[] cumX  = {0f};
        text.lzAccept((idx, style, cp) -> {
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
        text.lzAccept((idx, style, cp) -> {
            if (count[0] >= charIdx) return false;
            x[0] += glyphWidth(style, cp);
            count[0]++;
            return true;
        });
        return x[0];
    }

    private static void appendChars(StringBuilder sb, LzOrderedText text, int start, int end) {
        int[] count = {0};
        text.lzAccept((idx, style, cp) -> {
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
}
