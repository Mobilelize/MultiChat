package net.mobilelize.multichat.client;

import net.minecraft.text.Style;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.multichat.client.render.ChatRenderer;
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
        int max = ChatRenderer.getMessages(selPanel, tab).size() - 1;
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
        List<LzChatLine> msgs = ChatRenderer.getMessages(selPanel, tab);
        int[] n = norm();
        int tl = n[0], bl = n[1], tc = n[2], bc = n[3];

        StringBuilder sb = new StringBuilder();
        for (int s = tl; s >= bl; s--) {
            if (s < 0 || s >= msgs.size()) continue;
            int cStart = (s == tl) ? tc : 0;
            int cEnd   = (s == bl) ? bc : Integer.MAX_VALUE;
            appendChars(sb, msgs.get(s).content(), cStart, cEnd);
            if (s > bl) sb.append('\n');
        }
        return sb.toString();
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
        List<LzChatLine> panelMsgs = ChatRenderer.getMessages(panel, active);
        if (idx < 0 || idx >= panelMsgs.size()) return -1;
        if (idx >= visibleLineCount + scrolled) return -1;
        return idx;
    }

    public static int charIndexAtX(LzOrderedText text, float targetX) {
        if (targetX <= 0f) return 0;
        int[]   count = {0};
        float[] cumX  = {0f};
        text.accept((idx, style, cp) -> {
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
        text.accept((idx, style, cp) -> {
            if (count[0] >= charIdx) return false;
            x[0] += glyphWidth(style, cp);
            count[0]++;
            return true;
        });
        return x[0];
    }

    private static void appendChars(StringBuilder sb, LzOrderedText text, int start, int end) {
        int[] count = {0};
        text.accept((idx, style, cp) -> {
            if (count[0] >= end) return false;
            if (count[0] >= start) sb.appendCodePoint(cp);
            count[0]++;
            return true;
        });
    }

    private static float glyphWidth(Style style, int cp) {
        LzMutableText t = LzText.literal(new String(Character.toChars(cp))).withStyle(new LzStyle(style));
        return LzDraw.textWidth(t);
    }
}
