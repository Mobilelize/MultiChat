package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChatLine;

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
