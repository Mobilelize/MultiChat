package net.mobilelize.multichat.client.filter;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.TabType;

import java.util.List;
import java.util.stream.Collectors;

public class MessageRouter {

    static final int CAP_ALL      = 5000;
    static final int CAP_FILTERED = 500;

    // ------------------------------------------------------------------
    // Classification
    // ------------------------------------------------------------------

    public static MessageType classify(String text, ChatHudLine original) {
        if (original.indicator() != null) return MessageType.PLAYER_CHAT;
        return classifyText(text);
    }

    public static MessageType classify(String text, ChatHudLine.Visible firstLine) {
        if (firstLine.indicator() != null) return MessageType.PLAYER_CHAT;
        return classifyText(text);
    }

    private static MessageType classifyText(String text) {
        if (text.contains("joined the game") || text.contains("left the game")) return MessageType.JOIN_LEAVE;
        if (isDeath(text)) return MessageType.DEATH;
        return MessageType.SYSTEM;
    }

    private static boolean isDeath(String text) {
        return text.contains("was slain") || text.contains("died") || text.contains("drowned")
            || text.contains("fell") || text.contains("burned") || text.contains("suffocated")
            || text.contains("was killed") || text.contains("blew up") || text.contains("hit the ground")
            || text.contains("was shot") || text.contains("was blown up") || text.contains("starved");
    }

    // ------------------------------------------------------------------
    // Real-time routing (called per incoming message)
    // ------------------------------------------------------------------

    /**
     * Routes an incoming message to all appropriate tabs in a panel.
     * Mirrors vanilla's addVisibleMessage line ordering (k-reverse loop).
     */
    public static void routeLines(ChatPanel panel, ChatHudLine original, List<OrderedText> lines) {
        String fullText = original.content().getString();
        MessageType msgType = classify(fullText, original);

        boolean matchedFiltered = false;

        for (ChatTab tab : panel.tabs) {
            switch (tab.type) {
                case ALL -> addLines(panel, tab, original, lines);
                case VANILLA -> { /* vanilla handles via its own addVisibleMessage */ }
                case FILTERED -> {
                    if (tab.differenceMode) break;
                    if (tab.filterRules.matches(fullText, msgType)) {
                        addLines(panel, tab, original, lines);
                        matchedFiltered = true;
                    }
                }
            }
        }

        if (!matchedFiltered) {
            for (ChatTab tab : panel.tabs) {
                if (tab.type == TabType.FILTERED && tab.differenceMode) {
                    addLines(panel, tab, original, lines);
                }
            }
        }
    }

    private static void addLines(ChatPanel panel, ChatTab tab, ChatHudLine original, List<OrderedText> lines) {
        for (int j = 0; j < lines.size(); j++) {
            tab.messages.add(0, new ChatHudLine.Visible(
                original.creationTick(), lines.get(j), original.indicator(), j == lines.size() - 1));
        }
        int cap = tab.type == TabType.ALL ? CAP_ALL : CAP_FILTERED;
        while (tab.messages.size() > cap) tab.messages.remove(tab.messages.size() - 1);
        if (panel.tabs.indexOf(tab) != panel.activeTab) tab.unreadCount++;
    }

    // ------------------------------------------------------------------
    // Refilter (called on tab create or rule change)
    // ------------------------------------------------------------------

    /**
     * Clears and repopulates a FILTERED tab from the panel's ALL tab.
     * Groups Visible lines by endOfEntry to reconstruct full message text.
     */
    public static void refilterTab(ChatPanel panel, ChatTab tab) {
        if (tab.type != TabType.FILTERED) return;
        tab.messages.clear();

        List<ChatHudLine.Visible> all = panel.getAllTab().messages;
        if (all.isEmpty()) return;

        List<ChatTab> otherFiltered = tab.differenceMode
            ? panel.tabs.stream()
                .filter(t -> t.type == TabType.FILTERED && !t.differenceMode && t != tab)
                .collect(Collectors.toList())
            : null;

        int i = 0;
        while (i < all.size()) {
            int start = i;
            // Scan to the endOfEntry line (marks last visible line of a message entry)
            while (i < all.size() && !all.get(i).endOfEntry()) i++;
            if (i >= all.size()) break; // incomplete entry — skip
            int end = i++;

            // Concatenate lines to reconstruct full message text
            StringBuilder sb = new StringBuilder();
            for (int j = start; j <= end; j++) sb.append(all.get(j).content().toString());
            String fullText = sb.toString();
            MessageType msgType = classify(fullText, all.get(start));

            boolean include;
            if (tab.differenceMode) {
                boolean matchedOther = otherFiltered.stream()
                    .anyMatch(ft -> ft.filterRules.matches(fullText, msgType));
                include = !matchedOther;
            } else {
                include = tab.filterRules.matches(fullText, msgType);
            }

            if (include) {
                for (int j = start; j <= end; j++) tab.messages.add(tab.messages.size(), all.get(j));
            }
        }

        while (tab.messages.size() > CAP_FILTERED) tab.messages.remove(tab.messages.size() - 1);
    }
}
