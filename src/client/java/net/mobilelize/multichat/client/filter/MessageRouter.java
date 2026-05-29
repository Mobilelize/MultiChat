package net.mobilelize.multichat.client.filter;

import net.mobilelize.fabriclib.client.LzChatEntry;
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

    // Queue of raw (pre-mod-modification) LzText captured via Fabric server network message events.
    // Populated by MultichatClient when earlyMessageCapture is enabled.
    private static final java.util.ArrayDeque<LzText> rawQueue = new java.util.ArrayDeque<>();

    // Set before routing an API-injected message to prevent double-adding to the source tab.
    public static ChatTab skipTabOnRoute = null;

    public static void offerRaw(LzText raw) {
        rawQueue.addLast(raw);
    }

    // ------------------------------------------------------------------
    // Real-time routing (called per incoming message)
    // ------------------------------------------------------------------

    public static void routeLines(int tick, LzText content, LzMessageIndicator indicator, List<LzOrderedText> lines) {
        if (MessageReceivedCallback.EVENT.invoker().onMessageReceived(content)) return;

        LzText rawLzText = (MultichatClient.CONFIG.earlyMessageCapture && !rawQueue.isEmpty())
            ? rawQueue.pollFirst() : null;

        String fullText = rawLzText != null ? rawLzText.mc().getString() : content.mc().getString();

        // Collect the active tab for each enabled panel.
        List<ChatTab> panelActiveTabs = new ArrayList<>();
        for (ChatPanel panel : ChatRenderer.PANELS) {
            if (!panel.enabled()) continue;
            ChatTab active = panel.getActiveTab();
            if (active != null) panelActiveTabs.add(active);
        }

        // Difference mode: exactly one active tab has differenceMode=true.
        // That tab only receives messages that no other active tab received.
        ChatTab diffTab = null;
        int diffCount = 0;
        for (ChatTab tab : panelActiveTabs) {
            if (tab.type() == TabType.FILTERED && tab.differenceMode()) { diffTab = tab; diffCount++; }
        }
        if (diffCount != 1) diffTab = null;

        boolean anyOtherActiveReceived = false;

        LzText effectiveLzText = rawLzText != null ? rawLzText : content;

        // One entry per message, shared across ALL tabs that receive it.
        // This lets jumpToAll find the exact message by reference (==) instead of by tick+text,
        // fixing wrong jumps when identical messages share the same tick.
        LzChatEntry sharedEntry = new LzChatEntry(tick, effectiveLzText, indicator);

        for (ChatTab tab : ChatRenderer.TABS) {
            switch (tab.type()) {
                case ALL -> addLines(tab, tick, content, indicator, lines, rawLzText, sharedEntry);
                case VANILLA -> { /* vanilla handles via its own addVisibleMessage */ }
                case FILTERED -> {
                    if (tab == diffTab) break; // handled after first pass
                    if (tab.filterRules().matches(fullText, indicator)) {
                        addLines(tab, tick, content, indicator, lines, rawLzText, sharedEntry);
                        if (diffTab != null && panelActiveTabs.contains(tab)) anyOtherActiveReceived = true;
                    }
                }
                case API -> {
                    if (tab == skipTabOnRoute) break;
                    if (!tab.filterRules().isEmpty() && tab.filterRules().matches(fullText, indicator)) {
                        addLines(tab, tick, content, indicator, lines, rawLzText, sharedEntry);
                        if (diffTab != null && panelActiveTabs.contains(tab)) anyOtherActiveReceived = true;
                    }
                }
                case SEARCH -> {
                    SearchTab st = (SearchTab) tab;
                    if (!st.liveUpdate()) break;
                    if (!st.matchesText(fullText)) break;
                    if (st.sourceTab() != null && !st.sourceTab().filterRules().matches(fullText, indicator)) break;
                    if (!st.matchesTime(tick)) break;
                    if (st.collapseIdentical()) {
                        refilterSearch(st);
                    } else {
                        ChatTab allTab = ChatRenderer.TABS.stream()
                            .filter(t -> t.type() == TabType.ALL).findFirst().orElse(null);
                        if (allTab == null) break;
                        int linesAdded;
                        if (st.showTimestamps()) {
                            List<LzOrderedText> stamped = new ArrayList<>(ChatRenderer.stampedLines(
                                tick, effectiveLzText, indicator, lines));
                            for (int j = stamped.size() - 1; j >= 0; j--)
                                tab.addFirst(LzChatLine.create(
                                    tick, stamped.get(j), indicator, j == stamped.size() - 1, sharedEntry));
                            linesAdded = stamped.size();
                        } else {
                            for (int j = lines.size() - 1; j >= 0; j--)
                                tab.addFirst(allTab.getMessage(j));
                            linesAdded = lines.size();
                        }
                        if (tab.messageCap() > 0)
                            while (tab.messageCount() > tab.messageCap()) tab.removeLast();
                        tab.clearWidthCaches();
                        if (linesAdded > 0) {
                            for (ChatPanel panel : ChatRenderer.PANELS) {
                                int scrolled = panel.getScrolledLines(tab);
                                if (scrolled > 0)
                                    panel.setScrolledLines(tab, scrolled + linesAdded);
                            }
                        }
                    }
                    if (!ChatRenderer.isActiveInAnyPanel(tab)) tab.incrementUnread();
                }
            }
        }

        if (diffTab != null && !anyOtherActiveReceived && diffTab.filterRules().matches(fullText, indicator)) {
            addLines(diffTab, tick, content, indicator, lines, rawLzText, sharedEntry);
        }
    }

    private static void addLines(ChatTab tab, int tick, LzText content, LzMessageIndicator indicator,
                                  List<LzOrderedText> lines, @org.jetbrains.annotations.Nullable LzText rawLzText,
                                  LzChatEntry entry) {
        LzText effectiveText = rawLzText != null ? rawLzText : content;
        String text = effectiveText.mc().getString();
        boolean stamp = tab.showTimestamps();

        int netLinesAdded;
        boolean wasCollapse;

        if (tab.type() != TabType.ALL && tab.collapseRepeating() && text.equals(tab.collapse().lastText)
                && (!tab.collapseMatchFormatting() || effectiveText.mc().equals(tab.collapse().lastContent != null ? tab.collapse().lastContent.mc() : null))
                && tab.collapse().lastLines != null) {
            wasCollapse = true;
            int oldCount = tab.collapse().lineCount;
            for (int i = 0; i < oldCount && tab.hasMessages(); i++)
                tab.removeFirst();

            tab.collapse().repeat++;
            tab.collapse().lastTick  = tick;
            tab.collapse().lastLines = new ArrayList<>(lines);

            List<LzOrderedText> base = stamp
                ? ChatRenderer.stampedLines(tab.collapse().lastTick, effectiveText, indicator, lines)
                : new ArrayList<>(tab.collapse().lastLines);
            String counterStr = " " + MultichatClient.CONFIG.collapseCounterFormat
                .replace("{count}", String.valueOf(tab.collapse().repeat));
            LzOrderedText suffix = LzText.format(counterStr).ordered();
            List<LzOrderedText> updated = new ArrayList<>(base);
            int last = updated.size() - 1;
            updated.set(last, LzOrderedText.concat(updated.get(last), suffix));

            tab.collapse().lineCount = updated.size();
            for (int j = 0; j < updated.size(); j++) {
                boolean isEnd = j == updated.size() - 1;
                tab.addFirst(LzChatLine.create(tab.collapse().lastTick, updated.get(j), indicator, isEnd, entry));
            }
            netLinesAdded = updated.size() - oldCount;
        } else {
            wasCollapse = false;
            tab.collapse().lastText    = text;
            tab.collapse().lastContent = effectiveText;
            tab.collapse().repeat      = 1;
            tab.collapse().lastTick    = tick;
            tab.collapse().lastLines   = new ArrayList<>(lines);

            List<LzOrderedText> active = stamp
                ? ChatRenderer.stampedLines(tick, effectiveText, indicator, lines)
                : lines;
            tab.collapse().lineCount = active.size();
            for (int j = 0; j < active.size(); j++) {
                boolean isEnd = j == active.size() - 1;
                tab.addFirst(LzChatLine.create(tick, active.get(j), indicator, isEnd, entry));
            }
            netLinesAdded = active.size();
        }

        tab.clearWidthCaches();

        if (netLinesAdded != 0) {
            for (ChatPanel panel : ChatRenderer.PANELS) {
                int scrolled = panel.getScrolledLines(tab);
                if (scrolled <= 0) continue;
                int delta;
                if (panel.primary() || wasCollapse) {
                    delta = netLinesAdded;
                } else {
                    delta = ChatRenderer.lineCountAtWidth(effectiveText, indicator, stamp, tick, panel.resolveWidth());
                }
                panel.setScrolledLines(tab, Math.max(0, scrolled + delta));
            }
            ChatSelection.shiftLines(tab, netLinesAdded);
        }

        if (tab.messageCap() > 0) {
            int before = tab.messageCount();
            while (tab.messageCount() > tab.messageCap()) tab.removeLast();
            int removed = before - tab.messageCount();
            if (removed > 0) {
                tab.clearWidthCaches();
                for (ChatPanel panel : ChatRenderer.PANELS) {
                    int scrolled = panel.getScrolledLines(tab);
                    if (scrolled <= 0) continue;
                    panel.setScrolledLines(tab, Math.max(0, scrolled - removed));
                }
                ChatSelection.clampOrClear(tab);
            }
        }
        if (!ChatRenderer.isActiveInAnyPanel(tab)) tab.incrementUnread();
        if (tab.hasMessages())
            MessageAddedToTabCallback.EVENT.invoker().onMessageAdded(tab, tab.getFirstMessage());
    }


    /** Builds one message group for a secondary panel width, in primary-list format (end-of-entry first). */
    private static List<LzChatLine> buildGroupForWidth(int width, boolean stamp, int tick,
                                                        LzText content, LzMessageIndicator indicator,
                                                        LzChatEntry entry) {
        int indicatorW = indicator != null && indicator.hasIcon() ? indicator.iconWidth() + 6 : 0;
        List<LzOrderedText> wrapped = stamp
            ? ChatRenderer.stampedLines(tick, content, indicator, width)
            : net.mobilelize.fabriclib.client.LzChat.breakLines(content, width - indicatorW);
        List<LzChatLine> group = new ArrayList<>(wrapped.size());
        for (int j = 0; j < wrapped.size(); j++)
            group.add(LzChatLine.create(tick, wrapped.get(j), indicator, j == wrapped.size() - 1, entry));
        java.util.Collections.reverse(group); // end-of-entry at front
        return group;
    }

    // ------------------------------------------------------------------
    // Refilter (called on tab create or rule change)
    // ------------------------------------------------------------------

    public static void refilterSearch(SearchTab tab) {
        tab.clearMessages();

        List<LzChatLine> source;
        if (tab.sourceTab() != null) {
            source = tab.sourceTab().messages();
        } else {
            ChatTab allTab = ChatRenderer.TABS.stream()
                .filter(t -> t.type() == TabType.ALL).findFirst().orElse(null);
            if (allTab == null) return;
            source = allTab.messages();
        }
        if (source.isEmpty()) return;

        // Pass 1: collect matched groups in source order (newest→oldest), build dedup key→count
        record Group(int start, int end, int tick, String key) {}
        List<Group> matched = new ArrayList<>();
        java.util.LinkedHashMap<String, Integer> dedupCounts = tab.collapseIdentical()
            ? new java.util.LinkedHashMap<>() : null;

        int i = 0;
        while (i < source.size()) {
            if (!source.get(i).endOfEntry()) { i++; continue; }
            int groupStart = i++;
            while (i < source.size() && !source.get(i).endOfEntry()) i++;
            int groupEnd = i - 1;

            StringBuilder sb = new StringBuilder();
            for (int j = groupEnd; j >= groupStart; j--)
                source.get(j).content().lzAccept((idx2, style, cp) -> { sb.appendCodePoint(cp); return true; });
            String plainText = sb.toString();

            int addedTick = source.get(groupStart).addedTime();
            if (!tab.matchesText(plainText) || !tab.matchesTime(addedTick)) continue;

            matched.add(new Group(groupStart, groupEnd, addedTick, plainText));
            if (dedupCounts != null) dedupCounts.merge(plainText, 1, Integer::sum);
        }

        // Pass 2: emit groups. With collapseIdentical, skip duplicates (keep newest = lowest index in source).
        java.util.Set<String> seen = tab.collapseIdentical() ? new java.util.HashSet<>() : null;

        for (Group g : matched) {
            if (seen != null) {
                if (seen.contains(g.key())) continue;
                seen.add(g.key());
            }

            int count = dedupCounts != null ? dedupCounts.getOrDefault(g.key(), 1) : 1;
            LzChatLine endLine = source.get(g.start());
            LzMessageIndicator lzInd = endLine.indicator();

            LzChatEntry srcEntry = endLine.entry();
            List<LzOrderedText> lines;
            if (tab.showTimestamps() && srcEntry != null) {
                List<LzOrderedText> fallback = new ArrayList<>();
                for (int j = g.end(); j >= g.start(); j--) fallback.add(source.get(j).content());
                lines = new ArrayList<>(ChatRenderer.stampedLines(g.tick(), srcEntry.originalContent, lzInd, fallback));
            } else {
                lines = new ArrayList<>();
                for (int j = g.end(); j >= g.start(); j--) lines.add(source.get(j).content());
            }

            if (count > 1) appendCounter(lines, count);
            addLinesReversed(tab, lines, g.tick(), lzInd, srcEntry);
        }
    }

    private static void appendCounter(List<LzOrderedText> lines, int count) {
        String cStr = " " + MultichatClient.CONFIG.collapseCounterFormat
            .replace("{count}", String.valueOf(count));
        int last = lines.size() - 1;
        lines.set(last, LzOrderedText.concat(lines.get(last), LzText.format(cStr).ordered()));
    }

    private static void addLinesReversed(ChatTab tab, List<LzOrderedText> lines, int tick,
                                          LzMessageIndicator ind, @org.jetbrains.annotations.Nullable LzChatEntry entry) {
        for (int j = lines.size() - 1; j >= 0; j--)
            tab.addLast(LzChatLine.create(tick, lines.get(j), ind, j == lines.size() - 1, entry));
    }

    public static void refilterTab(ChatTab tab) {
        if (tab.type() != TabType.FILTERED) return;
        tab.clearMessages();

        ChatTab allTab = ChatRenderer.TABS.stream()
            .filter(t -> t.type() == TabType.ALL)
            .findFirst().orElse(null);
        if (allTab == null || !allTab.hasMessages()) return;
        List<LzChatLine> all = allTab.messages();

        long diffCount2 = tab.differenceMode()
            ? ChatRenderer.TABS.stream().filter(t -> t.type() == TabType.FILTERED && t.differenceMode()).count()
            : 0;
        boolean effectiveDiff = tab.differenceMode() && diffCount2 == 1;
        List<ChatTab> otherFiltered = effectiveDiff
            ? ChatRenderer.TABS.stream()
                .filter(t -> t.type() == TabType.FILTERED && !t.differenceMode())
                .toList()
            : List.of();

        boolean applySettings = MultichatClient.CONFIG.applySettingsOnRefilter;

        // Local collapse tracking (compare against ALL-tab plain text, not stamped text)
        String collapseLastText   = null;
        LzText collapseLastLzText = null;
        int    collapseRepeat     = 0;
        int    collapseLineCount  = 0;

        int i = 0;
        while (i < all.size()) {
            if (!all.get(i).endOfEntry()) { i++; continue; }
            int start = i++;
            while (i < all.size() && !all.get(i).endOfEntry()) i++;
            int end = i - 1;

            // Plain text extracted from ALL tab (no timestamps) — used for filter AND collapse compare
            StringBuilder sb = new StringBuilder();
            for (int j = end; j >= start; j--)
                all.get(j).content().lzAccept((idx2, style, cp) -> { sb.appendCodePoint(cp); return true; });
            String fullText = sb.toString();
            LzMessageIndicator indicator = all.get(start).indicator();

            boolean include;
            if (effectiveDiff) {
                boolean matchedOther = otherFiltered.stream()
                    .anyMatch(ft -> ft.filterRules().matches(fullText, indicator));
                include = !matchedOther && tab.filterRules().matches(fullText, indicator);
            } else {
                include = tab.filterRules().matches(fullText, indicator);
            }

            if (!include) continue;

            if (!applySettings) {
                // Legacy path: direct copy, preserve LzChatLine references
                for (int j = start; j <= end; j++) tab.addLast(all.get(j));
                continue;
            }

            int         addedTick  = all.get(start).addedTime();
            LzChatEntry srcEntry   = all.get(start).entry();
            LzText      cachedText = srcEntry != null ? srcEntry.originalContent : null;

            // Collapse check: compare fullText (pre-stamp) so timestamps don't affect matching
            boolean doCollapse = tab.collapseRepeating()
                    && fullText.equals(collapseLastText)
                    && (!tab.collapseMatchFormatting() || (cachedText != null && collapseLastLzText != null
                    && cachedText.mc().equals(collapseLastLzText.mc())));

            if (doCollapse) {
                collapseRepeat++;
                for (int k = 0; k < collapseLineCount; k++) tab.removeLast();
            } else {
                collapseLastText   = fullText;
                collapseLastLzText = cachedText;
                collapseRepeat = 1;
            }

            // Build lines in visual order (top→bottom)
            List<LzOrderedText> lines;
            if (tab.showTimestamps() && cachedText != null) {
                List<LzOrderedText> fallback = new ArrayList<>();
                for (int j = end; j >= start; j--) fallback.add(all.get(j).content());
                lines = new ArrayList<>(ChatRenderer.stampedLines(addedTick, cachedText, indicator, fallback));
            } else {
                lines = new ArrayList<>();
                for (int j = end; j >= start; j--) lines.add(all.get(j).content());
            }

            // Append collapse counter to last visual line (bottom)
            if (collapseRepeat > 1) {
                String cStr = " " + MultichatClient.CONFIG.collapseCounterFormat
                    .replace("{count}", String.valueOf(collapseRepeat));
                int last = lines.size() - 1;
                lines.set(last, LzOrderedText.concat(lines.get(last), LzText.format(cStr).ordered()));
            }

            // Store bottom visual line first (endOfEntry=true), then upper lines
            LzMessageIndicator lzInd = all.get(start).indicator();
            for (int j = lines.size() - 1; j >= 0; j--)
                tab.addLast(LzChatLine.create(addedTick, lines.get(j), lzInd, j == lines.size() - 1, srcEntry));
            collapseLineCount = lines.size();
        }

        if (tab.messageCap() > 0) {
            while (tab.messageCount() > tab.messageCap()) tab.removeLast();
        }
    }
}
