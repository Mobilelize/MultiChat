package net.mobilelize.multichat.client.filter;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzMessageIndicator;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.api.MessageAddedToTabCallback;
import net.mobilelize.multichat.api.MessageReceivedCallback;
import net.mobilelize.multichat.client.*;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.ArrayList;
import java.util.List;

public class MessageRouter {


    // ------------------------------------------------------------------
    // Real-time routing (called per incoming message)
    // ------------------------------------------------------------------

    public static void routeLines(ChatHudLine original, List<LzOrderedText> lines) {
        if (MessageReceivedCallback.EVENT.invoker().onMessageReceived(LzText.wrapMc(original.content()))) return;

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
                case API -> {
                    if (!tab.filterRules.isEmpty() && tab.filterRules.matches(fullText, indicator))
                        addLines(tab, original, lines);
                }
                case SEARCH -> {
                    SearchTab st = (SearchTab) tab;
                    if (!st.liveUpdate) break;
                    if (!st.matchesText(fullText)) break;
                    if (st.sourceTab != null && !st.sourceTab.filterRules.matches(fullText, indicator)) break;
                    if (!st.matchesTime(original.creationTick())) break;
                    ChatTab allTab = ChatRenderer.TABS.stream()
                        .filter(t -> t.type == TabType.ALL).findFirst().orElse(null);
                    if (allTab == null) break;
                    for (int j = lines.size() - 1; j >= 0; j--)
                        tab.messages.addFirst(allTab.messages.get(j));
                    if (tab.messageCap > 0)
                        while (tab.messages.size() > tab.messageCap) tab.messages.removeLast();
                    if (!isActiveInAnyPanel(tab)) tab.unreadCount++;
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

    private static void addLines(ChatTab tab, ChatHudLine original, List<LzOrderedText> lines) {
        String text = original.content().getString();
        boolean stamp = tab.showTimestamps;
        LzMessageIndicator indicator = LzMessageIndicator.wrapNullable(original.indicator());

        int netLinesAdded;
        if (tab.type != TabType.ALL && tab.collapseRepeating && text.equals(tab.collapseLastText)
                && (!tab.collapseMatchFormatting || original.content().equals(tab.collapseLastContent))
                && tab.collapseLastLines != null) {
            int oldCount = tab.collapseLineCount;
            for (int i = 0; i < oldCount && !tab.messages.isEmpty(); i++)
                tab.messages.removeFirst();

            tab.collapseRepeat++;
            tab.collapseLastTick  = original.creationTick();
            tab.collapseLastLines = new ArrayList<>(lines);

            List<LzOrderedText> base = stamp
                ? ChatRenderer.stampedLines(tab.collapseLastTick, LzText.wrapMc(original.content()), original.indicator(), lines)
                : new ArrayList<>(tab.collapseLastLines);
            String counterStr = " " + MultichatClient.CONFIG.collapseCounterFormat
                .replace("{count}", String.valueOf(tab.collapseRepeat));
            LzOrderedText suffix = LzText.format(counterStr).ordered();
            List<LzOrderedText> updated = new ArrayList<>(base);
            int last = updated.size() - 1;
            updated.set(last, LzOrderedText.concat(updated.get(last), suffix));

            tab.collapseLineCount = updated.size();
            for (int j = 0; j < updated.size(); j++)
                tab.messages.addFirst(LzChatLine.create(
                    tab.collapseLastTick, updated.get(j), indicator, j == updated.size() - 1));
            netLinesAdded = updated.size() - oldCount;
        } else {
            tab.collapseLastText    = text;
            tab.collapseLastContent = original.content();
            tab.collapseRepeat      = 1;
            tab.collapseLastTick  = original.creationTick();
            tab.collapseLastLines = new ArrayList<>(lines);

            List<LzOrderedText> active = stamp
                ? ChatRenderer.stampedLines(original.creationTick(), LzText.wrapMc(original.content()), original.indicator(), lines)
                : lines;
            tab.collapseLineCount = active.size();
            for (int j = 0; j < active.size(); j++)
                tab.messages.addFirst(LzChatLine.create(
                    original.creationTick(), active.get(j), indicator, j == active.size() - 1));
            netLinesAdded = active.size();
        }

        if (netLinesAdded != 0) {
            for (ChatPanel panel : List.of(ChatRenderer.LEFT, ChatRenderer.RIGHT)) {
                int scrolled = panel.getScrolledLines(tab);
                if (scrolled > 0) panel.setScrolledLines(tab, scrolled + netLinesAdded);
            }
            ChatSelection.shiftLines(tab, netLinesAdded);
        }

        if (tab.messageCap > 0) {
            int before = tab.messages.size();
            while (tab.messages.size() > tab.messageCap) tab.messages.removeLast();
            int removed = before - tab.messages.size();
            if (removed > 0) {
                for (ChatPanel panel : List.of(ChatRenderer.LEFT, ChatRenderer.RIGHT)) {
                    int scrolled = panel.getScrolledLines(tab);
                    if (scrolled > 0)
                        panel.setScrolledLines(tab, Math.max(0, scrolled - removed));
                }
                ChatSelection.clampOrClear(tab);
            }
        }
        if (!isActiveInAnyPanel(tab)) tab.unreadCount++;
        if (!tab.messages.isEmpty())
            MessageAddedToTabCallback.EVENT.invoker().onMessageAdded(tab, tab.messages.getFirst());
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

    public static void refilterSearch(SearchTab tab) {
        tab.messages.clear();
        tab.resetCollapse();

        List<LzChatLine> source;
        if (tab.sourceTab != null) {
            source = tab.sourceTab.messages;
        } else {
            ChatTab allTab = ChatRenderer.TABS.stream()
                .filter(t -> t.type == TabType.ALL).findFirst().orElse(null);
            if (allTab == null) return;
            source = allTab.messages;
        }
        if (source.isEmpty()) return;

        int i = 0;
        while (i < source.size()) {
            if (!source.get(i).endOfEntry()) { i++; continue; }
            int groupStart = i++;
            while (i < source.size() && !source.get(i).endOfEntry()) i++;
            int groupEnd = i - 1;

            StringBuilder sb = new StringBuilder();
            for (int j = groupEnd; j >= groupStart; j--)
                source.get(j).content().accept((idx2, style, cp) -> { sb.appendCodePoint(cp); return true; });

            int addedTick = source.get(groupStart).addedTime();

            if (tab.matchesText(sb.toString()) && tab.matchesTime(addedTick)) {
                for (int j = groupStart; j <= groupEnd; j++)
                    tab.messages.add(tab.messages.size(), source.get(j));
            }
        }
    }

    public static void refilterTab(ChatTab tab) {
        if (tab.type != TabType.FILTERED && tab.type != TabType.API) return;
        if (tab.type == TabType.API) return;
        tab.messages.clear();
        tab.resetCollapse();

        ChatTab allTab = ChatRenderer.TABS.stream()
            .filter(t -> t.type == TabType.ALL)
            .findFirst().orElse(null);
        if (allTab == null || allTab.messages.isEmpty()) return;
        List<LzChatLine> all = allTab.messages;

        List<ChatTab> otherFiltered = tab.differenceMode
            ? ChatRenderer.TABS.stream()
                .filter(t -> t.type == TabType.FILTERED && !t.differenceMode)
                .toList()
            : List.of();

        int i = 0;
        while (i < all.size()) {
            int start = i;
            while (i < all.size() && !all.get(i).endOfEntry()) i++;
            if (i >= all.size()) break;
            int end = i++;

            StringBuilder sb = new StringBuilder();
            for (int j = start; j <= end; j++) sb.append(all.get(j).content().mc().toString());
            String fullText = sb.toString();
            MessageIndicator indicator = all.get(start).indicator() != null
                ? all.get(start).indicator().mc() : null;

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
            while (tab.messages.size() > tab.messageCap) tab.messages.removeLast();
        }
    }
}
