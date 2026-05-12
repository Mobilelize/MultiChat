package net.mobilelize.multichat.client.filter;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.List;

public class MessageRouter {


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
        String text = original.content().getString();

        if (tab.type != TabType.ALL && tab.collapseRepeating && text.equals(tab.collapseLastText) && tab.collapseLastLines != null) {
            // Remove previous collapsed message
            for (int i = 0; i < tab.collapseLineCount && !tab.messages.isEmpty(); i++)
                tab.messages.remove(0);

            tab.collapseRepeat++;
            tab.collapseLastTick = original.creationTick();

            // Re-add original lines with updated count suffix on last line
            OrderedText suffix = Text.literal(" (x" + tab.collapseRepeat + ")").formatted(net.minecraft.util.Formatting.DARK_GRAY).asOrderedText();
            List<OrderedText> updated = new ArrayList<>(tab.collapseLastLines);
            int last = updated.size() - 1;
            updated.set(last, OrderedText.concat(updated.get(last), suffix));

            for (int j = 0; j < updated.size(); j++)
                tab.messages.add(0, new ChatHudLine.Visible(
                    tab.collapseLastTick, updated.get(j), original.indicator(), j == updated.size() - 1));
        } else {
            tab.collapseLastText  = text;
            tab.collapseRepeat    = 1;
            tab.collapseLineCount = lines.size();
            tab.collapseLastTick  = original.creationTick();
            tab.collapseLastLines = new ArrayList<>(lines);

            for (int j = 0; j < lines.size(); j++)
                tab.messages.add(0, new ChatHudLine.Visible(
                    original.creationTick(), lines.get(j), original.indicator(), j == lines.size() - 1));
        }

        if (tab.messageCap > 0) {
            while (tab.messages.size() > tab.messageCap) tab.messages.remove(tab.messages.size() - 1);
        }
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
        tab.resetCollapse();

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

        if (tab.messageCap > 0) {
            while (tab.messages.size() > tab.messageCap) tab.messages.remove(tab.messages.size() - 1);
        }
    }
}
