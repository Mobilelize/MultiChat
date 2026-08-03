package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.List;

/**
 * Scrollbar thumb drag-to-scroll state — one active drag at a time, mirrors ChatSelection's pattern
 * (a static "current drag" holder, polled once per render frame rather than via mouseDragged/Released,
 * since this codebase drives all chat-area dragging that way — see ChatScreenMixin.renderOverlays).
 */
public final class ChatScrollbarDrag {

    private ChatScrollbarDrag() {}

    private static ChatPanel draggingPanel = null;

    public static boolean isDragging() { return draggingPanel != null; }
    public static ChatPanel panel()     { return draggingPanel; }
    public static void end()            { draggingPanel = null; }

    public static void start(ChatPanel panel, double mouseY, int windowHeight) {
        draggingPanel = panel;
        updateFromMouse(panel, mouseY, windowHeight);
    }

    /**
     * True when mouseX/mouseY sits in the scrollbar's clickable column — a few pixels wider than the
     * thumb itself, not a pixel-precise hit on it. Shared by the click hit-test (ChatScreenMixin) and
     * the hover-glow render check (ChatRenderer), so both always agree on exactly the same region.
     */
    public static boolean isMouseOverRegion(ChatPanel panel, double mouseX, double mouseY, int windowWidth, int windowHeight) {
        if (!panel.isEnabled() || panel.getActiveTab() == null) return false;

        float scale      = panel.resolveScale();
        int   chatWidth  = panel.resolveWidth();
        int   lineH      = panel.resolveLineHeight();
        int   visLines   = panel.resolveLines();
        int   topY       = panel.resolveTopY(windowHeight);

        boolean inMessageArea;
        if (!panel.showTabs()) {
            inMessageArea = mouseY < topY * (double) scale;
        } else {
            int tabY = ChatRenderer.getTabBarY(panel, topY, visLines, lineH);
            if (panel.tabsAtTop()) {
                double tabBarBot = (tabY + ChatRenderer.TAB_HEIGHT) * (double) scale;
                inMessageArea = mouseY > tabBarBot && mouseY < topY * (double) scale;
            } else {
                inMessageArea = mouseY < tabY * (double) scale;
            }
        }
        if (!inMessageArea) return false;

        int xOff = panel.computeXOffset(windowWidth);
        double localChatX = mouseX / (double) scale - 4.0 - xOff;
        return localChatX >= chatWidth + 2 && localChatX <= chatWidth + 8;
    }

    /** Jumps scroll to the proportional position mouseY sits at within the panel's message area. */
    public static void updateFromMouse(ChatPanel panel, double mouseY, int windowHeight) {
        ChatTab active = panel.getActiveTab();
        if (active == null) return;

        float scale      = panel.resolveScale();
        int   lines      = panel.resolveLines();
        int   lineHeight = panel.resolveLineHeight();
        int   topY       = panel.resolveTopY(windowHeight);

        double areaTop    = (topY - lines * lineHeight) * (double) scale;
        double areaBottom = topY * (double) scale;
        double frac = (mouseY - areaTop) / Math.max(1.0, areaBottom - areaTop);
        frac = Math.max(0.0, Math.min(1.0, frac));

        List<LzChatLine> panelLines = panel.getTabLines(active);
        int max = Math.max(0, (panelLines != null ? panelLines.size() : 0) - lines);
        int target = (int) Math.round((1.0 - frac) * max); // top of track = fully scrolled back, bottom = newest
        panel.setScrolledLines(active, Math.max(0, Math.min(max, target)));
    }
}
