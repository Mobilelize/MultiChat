package net.mobilelize.multichat.client;

import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatPanel {

    public enum Anchor { LEFT, RIGHT }

    public final Anchor anchor;
    public final int margin;
    public int activeTab = 0;
    public boolean enabled;
    public boolean showOnlyWhenFocused;
    public boolean showBadges = true;
    public boolean showTabs   = true;
    public boolean tabsAtTop  = false;
    public int tabScrollOffset = 0;
    public int tabLastRendered = -1;
    private final Map<ChatTab, Integer> scrollPositions = new HashMap<>();

    public ChatPanel(Anchor anchor, int margin, boolean enabled, boolean showOnlyWhenFocused) {
        this.anchor = anchor;
        this.margin = margin;
        this.enabled = enabled;
        this.showOnlyWhenFocused = showOnlyWhenFocused;
    }

    public List<ChatTab> getVisibleTabs() {
        return ChatRenderer.TABS.stream()
            .filter(t -> t.visibility == TabVisibility.BOTH
                      || (anchor == Anchor.LEFT  && t.visibility == TabVisibility.LEFT)
                      || (anchor == Anchor.RIGHT && t.visibility == TabVisibility.RIGHT))
            .toList();
    }

    public ChatTab getActiveTab() {
        List<ChatTab> visible = getVisibleTabs();
        if (visible.isEmpty()) return null;
        if (activeTab >= visible.size()) activeTab = 0;
        return visible.get(activeTab);
    }

    public int getScrolledLines(ChatTab tab) {
        return scrollPositions.getOrDefault(tab, 0);
    }

    public void setScrolledLines(ChatTab tab, int lines) {
        scrollPositions.put(tab, lines);
    }

    public void resetScrollPositions() {
        scrollPositions.clear();
    }

    public int computeXOffset(int scaledWindowWidth, float scale, int chatWidth) {
        if (anchor == Anchor.RIGHT) {
            return Math.round(scaledWindowWidth / scale) - chatWidth - 12 - margin;
        }
        return margin;
    }
}
