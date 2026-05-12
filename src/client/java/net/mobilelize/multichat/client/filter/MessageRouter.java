package net.mobilelize.multichat.client.filter;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.OrderedText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.List;

public class MessageRouter {

    static final int CAP_ALL      = 5000;
    static final int CAP_FILTERED = 500;

    // ------------------------------------------------------------------
    // Real-time routing (called per incoming message)
    // ------------------------------------------------------------------

    public static void routeLines(ChatHudLine original, List<OrderedText> lines) {
        String fullText = original.content().getString();
        MessageIndicator indicator = original.indicator();

        boolean matchedFiltered = false;

        ChatTab leftActive = ChatRenderer.LEFT.getActiveTab();
        ChatTab rightActive = ChatRenderer.RIGHT.getActiveTab();
        boolean leftDiff = leftActive != null && leftActive.type == TabType.FILTERED && leftActive.differenceMode;
        boolean rightDiff = rightActive != null && rightActive.type == TabType.FILTERED && rightActive.differenceMode;
        boolean activeDifference = leftDiff ^ rightDiff;

        for (ChatTab tab : ChatRenderer.TABS) {
            if (tab.visibility == TabVisibility.NONE) continue;
            switch (tab.type) {
                case ALL -> addLines(tab, original, lines);
                case VANILLA -> { /* vanilla handles via its own addVisibleMessage */ }
                case FILTERED -> {
                    if (activeDifference && tab.differenceMode && (tab == leftActive || tab == rightActive)) break;
                    if (tab.filterRules.matches(fullText, indicator)) {
                        addLines(tab, original, lines);
                        matchedFiltered = true;
                    }
                }
            }
        }

        if (!matchedFiltered) {
            if (!activeDifference) return;
            if (leftActive != null && leftActive.filterRules.matches(fullText, indicator)) {
                addLines(leftActive, original, lines);
            }
            if (rightActive != null && rightActive.filterRules.matches(fullText, indicator)) {
                addLines(rightActive, original, lines);
            }
        }
    }

    private static void addLines(ChatTab tab, ChatHudLine original, List<OrderedText> lines) {
        for (int j = 0; j < lines.size(); j++) {
            tab.messages.add(0, new ChatHudLine.Visible(
                original.creationTick(), lines.get(j), original.indicator(), j == lines.size() - 1));
        }
        int cap = tab.type == TabType.ALL ? CAP_ALL : CAP_FILTERED;
        while (tab.messages.size() > cap) tab.messages.remove(tab.messages.size() - 1);
        if (!isActiveInAnyPanel(tab)) tab.unreadCount++;
    }

    private static boolean isActiveInAnyPanel(ChatTab tab) {
        for (ChatPanel panel : List.of(ChatRenderer.LEFT, ChatRenderer.RIGHT)) {
            List<ChatTab> visible = panel.getVisibleTabs();
            int idx = visible.indexOf(tab);
            if (idx >= 0 && panel.activeTab == idx) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Refilter (called on tab create or rule change)
    // ------------------------------------------------------------------

    public static void refilterTab(ChatTab tab) {
        if (tab.type != TabType.FILTERED) return;
        tab.messages.clear();

        ChatTab allTab = ChatRenderer.TABS.stream()
            .filter(t -> t.type == TabType.ALL)
            .findFirst().orElse(null);
        if (allTab == null || allTab.messages.isEmpty()) return;
        List<ChatHudLine.Visible> all = allTab.messages;

        List<ChatTab> otherFiltered = tab.differenceMode
            ? ChatRenderer.TABS.stream()
                .filter(t -> t.type == TabType.FILTERED && !t.differenceMode)
                .toList()
            : null;

        int i = 0;
        while (i < all.size()) {
            int start = i;
            while (i < all.size() && !all.get(i).endOfEntry()) i++;
            if (i >= all.size()) break;
            int end = i++;

            StringBuilder sb = new StringBuilder();
            for (int j = start; j <= end; j++) sb.append(all.get(j).content().toString());
            String fullText = sb.toString();
            MessageIndicator indicator = all.get(start).indicator();

            boolean include;
            if (tab.differenceMode) {
                boolean matchedOther = otherFiltered.stream()
                    .anyMatch(ft -> ft.filterRules.matches(fullText, indicator));
                include = !matchedOther;
            } else {
                include = tab.filterRules.matches(fullText, indicator);
            }

            if (include) {
                for (int j = start; j <= end; j++) tab.messages.add(tab.messages.size(), all.get(j));
            }
        }

        while (tab.messages.size() > CAP_FILTERED) tab.messages.remove(tab.messages.size() - 1);
    }
}
