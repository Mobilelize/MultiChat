package net.mobilelize.multichat.client;

import java.util.ArrayList;
import java.util.List;

public class ChatPanel {

    public enum Anchor { LEFT, RIGHT }

    public final Anchor anchor;
    public final int margin;
    public final List<ChatTab> tabs = new ArrayList<>();
    public int activeTab = 0;
    public boolean enabled;
    public boolean showOnlyWhenFocused;

    public ChatPanel(Anchor anchor, int margin, boolean enabled, boolean showOnlyWhenFocused) {
        this.anchor = anchor;
        this.margin = margin;
        this.enabled = enabled;
        this.showOnlyWhenFocused = showOnlyWhenFocused;

        tabs.add(new ChatTab("All", TabType.ALL));
        if (anchor == Anchor.LEFT) {
            tabs.add(new ChatTab("Vanilla", TabType.VANILLA));
        }
    }

    public ChatTab getActiveTab() {
        return tabs.get(activeTab);
    }

    /** ALL tab is always at index 0. */
    public ChatTab getAllTab() {
        return tabs.get(0);
    }

    public int computeXOffset(int scaledWindowWidth, float scale, int chatWidth) {
        if (anchor == Anchor.RIGHT) {
            return Math.round(scaledWindowWidth / scale) - chatWidth - 12 - margin;
        }
        return margin;
    }
}
