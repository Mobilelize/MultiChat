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

    // Cache of original LzText keyed by the ALL-tab endOfEntry LzChatLine.
    // Used by refilterSearch to call stampedLines() with the original content.
    private static final java.util.WeakHashMap<LzChatLine, LzText> allTabTextCache = new java.util.WeakHashMap<>();

    // Queue of raw (pre-mod-modification) LzText captured via Fabric API events.
    // Populated by MultichatClient when earlyMessageCapture is enabled.
    private static final java.util.ArrayDeque<LzText> rawQueue = new java.util.ArrayDeque<>();

    public static void offerRaw(LzText raw) {
        rawQueue.addLast(raw);
    }

    // ------------------------------------------------------------------
    // Real-time routing (called per incoming message)
    // ------------------------------------------------------------------

    public static void routeLines(ChatHudLine original, List<LzOrderedText> lines) {
        if (MessageReceivedCallback.EVENT.invoker().onMessageReceived(LzText.wrapMc(original.content()))) return;

        LzText rawLzText = (MultichatClient.CONFIG.earlyMessageCapture && !rawQueue.isEmpty())
            ? rawQueue.pollFirst() : null;

        String fullText = rawLzText != null ? rawLzText.mc().getString() : original.content().getString();
        MessageIndicator indicator = original.indicator();

        ChatTab leftActive = ChatRenderer.LEFT.getActiveTab();
        ChatTab rightActive = ChatRenderer.RIGHT.getActiveTab();
        boolean leftDiff = leftActive != null && leftActive.type == TabType.FILTERED && leftActive.differenceMode;
        boolean rightDiff = rightActive != null && rightActive.type == TabType.FILTERED && rightActive.differenceMode;
        boolean activeDifference = false;
        ChatTab diffActiveTab  = null;
        ChatTab nonDiffActive  = null;
        if (leftDiff ^ rightDiff) {
            ChatTab candidate = leftDiff ? rightActive : leftActive;
            if (candidate != null && !candidate.filterRules.whitelist.isEmpty()) {
                activeDifference = true;
                diffActiveTab    = leftDiff ? leftActive : rightActive;
                nonDiffActive    = candidate;
            }
        }
        boolean nonDiffReceived = false;

        LzText effectiveLzText = rawLzText != null ? rawLzText : LzText.wrapMc(original.content());

        for (ChatTab tab : ChatRenderer.TABS) {
            switch (tab.type) {
                case ALL -> addLines(tab, original, lines, rawLzText);
                case VANILLA -> { /* vanilla handles via its own addVisibleMessage */ }
                case FILTERED -> {
                    if (activeDifference && tab == diffActiveTab) break;
                    if (tab.filterRules.matches(fullText, indicator)) {
                        addLines(tab, original, lines, rawLzText);
                        if (activeDifference && tab == nonDiffActive) nonDiffReceived = true;
                    }
                }
                case API -> {
                    if (!tab.filterRules.isEmpty() && tab.filterRules.matches(fullText, indicator))
                        addLines(tab, original, lines, rawLzText);
                }
                case SEARCH -> {
                    SearchTab st = (SearchTab) tab;
                    if (!st.liveUpdate) break;
                    if (!st.matchesText(fullText)) break;
                    if (st.sourceTab != null && !st.sourceTab.filterRules.matches(fullText, indicator)) break;
                    if (!st.matchesTime(original.creationTick())) break;
                    if (st.collapseIdentical) {
                        refilterSearch(st);
                    } else {
                        ChatTab allTab = ChatRenderer.TABS.stream()
                            .filter(t -> t.type == TabType.ALL).findFirst().orElse(null);
                        if (allTab == null) break;
                        if (st.showTimestamps) {
                            List<LzOrderedText> stamped = new ArrayList<>(ChatRenderer.stampedLines(
                                original.creationTick(), effectiveLzText, original.indicator(), lines));
                            LzMessageIndicator ind = LzMessageIndicator.wrapNullable(original.indicator());
                            for (int j = stamped.size() - 1; j >= 0; j--)
                                tab.messages.addFirst(LzChatLine.create(
                                    original.creationTick(), stamped.get(j), ind, j == stamped.size() - 1));
                        } else {
                            for (int j = lines.size() - 1; j >= 0; j--)
                                tab.messages.addFirst(allTab.messages.get(j));
                        }
                        if (tab.messageCap > 0)
                            while (tab.messages.size() > tab.messageCap) tab.messages.removeLast();
                    }
                    if (!isActiveInAnyPanel(tab)) tab.unreadCount++;
                }
            }
        }

        if (activeDifference && !nonDiffReceived) {
            addLines(diffActiveTab, original, lines, rawLzText);
        }
    }

    private static void addLines(ChatTab tab, ChatHudLine original, List<LzOrderedText> lines,
                                  @org.jetbrains.annotations.Nullable LzText rawLzText) {
        LzText effectiveText = rawLzText != null ? rawLzText : LzText.wrapMc(original.content());
        String text = effectiveText.mc().getString();
        boolean stamp = tab.showTimestamps;
        LzMessageIndicator indicator = LzMessageIndicator.wrapNullable(original.indicator());

        int netLinesAdded;
        if (tab.type != TabType.ALL && tab.collapseRepeating && text.equals(tab.collapseLastText)
                && (!tab.collapseMatchFormatting || effectiveText.mc().equals(tab.collapseLastContent))
                && tab.collapseLastLines != null) {
            int oldCount = tab.collapseLineCount;
            for (int i = 0; i < oldCount && !tab.messages.isEmpty(); i++)
                tab.messages.removeFirst();

            tab.collapseRepeat++;
            tab.collapseLastTick  = original.creationTick();
            tab.collapseLastLines = new ArrayList<>(lines);

            List<LzOrderedText> base = stamp
                ? ChatRenderer.stampedLines(tab.collapseLastTick, effectiveText, original.indicator(), lines)
                : new ArrayList<>(tab.collapseLastLines);
            String counterStr = " " + MultichatClient.CONFIG.collapseCounterFormat
                .replace("{count}", String.valueOf(tab.collapseRepeat));
            LzOrderedText suffix = LzText.format(counterStr).ordered();
            List<LzOrderedText> updated = new ArrayList<>(base);
            int last = updated.size() - 1;
            updated.set(last, LzOrderedText.concat(updated.get(last), suffix));

            tab.collapseLineCount = updated.size();
            for (int j = 0; j < updated.size(); j++) {
                boolean isEnd = j == updated.size() - 1;
                LzChatLine line = LzChatLine.create(tab.collapseLastTick, updated.get(j), indicator, isEnd);
                tab.messages.addFirst(line);
            }
            netLinesAdded = updated.size() - oldCount;
        } else {
            tab.collapseLastText    = text;
            tab.collapseLastContent = effectiveText.mc();
            tab.collapseRepeat      = 1;
            tab.collapseLastTick  = original.creationTick();
            tab.collapseLastLines = new ArrayList<>(lines);

            List<LzOrderedText> active = stamp
                ? ChatRenderer.stampedLines(original.creationTick(), effectiveText, original.indicator(), lines)
                : lines;
            tab.collapseLineCount = active.size();
            for (int j = 0; j < active.size(); j++) {
                boolean isEnd = j == active.size() - 1;
                LzChatLine line = LzChatLine.create(original.creationTick(), active.get(j), indicator, isEnd);
                tab.messages.addFirst(line);
                if (tab.type == TabType.ALL && isEnd)
                    allTabTextCache.put(line, effectiveText);
            }
            netLinesAdded = active.size();
        }

        if (netLinesAdded != 0) {
            for (ChatPanel panel : ChatRenderer.PANELS) {
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
                for (ChatPanel panel : ChatRenderer.PANELS) {
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
        for (ChatPanel panel : ChatRenderer.PANELS) {
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

        // Pass 1: collect matched groups in source order (newest→oldest), build dedup key→count
        record Group(int start, int end, int tick, String key) {}
        List<Group> matched = new ArrayList<>();
        java.util.LinkedHashMap<String, Integer> dedupCounts = tab.collapseIdentical
            ? new java.util.LinkedHashMap<>() : null;

        int i = 0;
        while (i < source.size()) {
            if (!source.get(i).endOfEntry()) { i++; continue; }
            int groupStart = i++;
            while (i < source.size() && !source.get(i).endOfEntry()) i++;
            int groupEnd = i - 1;

            StringBuilder sb = new StringBuilder();
            for (int j = groupEnd; j >= groupStart; j--)
                source.get(j).content().accept((idx2, style, cp) -> { sb.appendCodePoint(cp); return true; });
            String plainText = sb.toString();

            int addedTick = source.get(groupStart).addedTime();
            if (!tab.matchesText(plainText) || !tab.matchesTime(addedTick)) continue;

            matched.add(new Group(groupStart, groupEnd, addedTick, plainText));
            if (dedupCounts != null) dedupCounts.merge(plainText, 1, Integer::sum);
        }

        // Pass 2: emit groups. With collapseIdentical, skip duplicates (keep newest = lowest index in source).
        java.util.Set<String> seen = tab.collapseIdentical ? new java.util.HashSet<>() : null;

        for (Group g : matched) {
            if (seen != null) {
                if (seen.contains(g.key())) continue;
                seen.add(g.key());
            }

            int count = dedupCounts != null ? dedupCounts.getOrDefault(g.key(), 1) : 1;
            LzChatLine endLine = source.get(g.start());
            LzMessageIndicator lzInd = endLine.indicator();
            MessageIndicator ind = lzInd != null ? lzInd.mc() : null;

            List<LzOrderedText> lines;
            if (tab.showTimestamps) {
                LzText originalText = allTabTextCache.get(endLine);
                if (originalText != null) {
                    List<LzOrderedText> fallback = new ArrayList<>();
                    for (int j = g.end(); j >= g.start(); j--) fallback.add(source.get(j).content());
                    lines = new ArrayList<>(ChatRenderer.stampedLines(g.tick(), originalText, ind, fallback));
                } else {
                    lines = new ArrayList<>();
                    for (int j = g.end(); j >= g.start(); j--) lines.add(source.get(j).content());
                }
            } else {
                lines = new ArrayList<>();
                for (int j = g.end(); j >= g.start(); j--) lines.add(source.get(j).content());
            }

            if (count > 1) appendCounter(lines, count);
            addLinesReversed(tab, lines, g.tick(), lzInd);
        }
    }

    private static void appendCounter(List<LzOrderedText> lines, int count) {
        String cStr = " " + MultichatClient.CONFIG.collapseCounterFormat
            .replace("{count}", String.valueOf(count));
        int last = lines.size() - 1;
        lines.set(last, LzOrderedText.concat(lines.get(last), LzText.format(cStr).ordered()));
    }

    private static void addLinesReversed(ChatTab tab, List<LzOrderedText> lines, int tick, LzMessageIndicator ind) {
        for (int j = lines.size() - 1; j >= 0; j--)
            tab.messages.add(tab.messages.size(),
                LzChatLine.create(tick, lines.get(j), ind, j == lines.size() - 1));
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
                all.get(j).content().accept((idx2, style, cp) -> { sb.appendCodePoint(cp); return true; });
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

            if (!include) continue;

            if (!applySettings) {
                // Legacy path: direct copy, preserve LzChatLine references
                for (int j = start; j <= end; j++) tab.messages.add(tab.messages.size(), all.get(j));
                continue;
            }

            int    addedTick  = all.get(start).addedTime();
            LzText cachedText = allTabTextCache.get(all.get(start));

            // Collapse check: compare fullText (pre-stamp) so timestamps don't affect matching
            boolean doCollapse = tab.collapseRepeating
                && collapseLastText != null
                && fullText.equals(collapseLastText)
                && (!tab.collapseMatchFormatting || (cachedText != null && collapseLastLzText != null
                    && cachedText.mc().equals(collapseLastLzText.mc())));

            if (doCollapse) {
                collapseRepeat++;
                for (int k = 0; k < collapseLineCount; k++) tab.messages.removeLast();
            } else {
                collapseLastText   = fullText;
                collapseLastLzText = cachedText;
                collapseRepeat = 1;
            }

            // Build lines in visual order (top→bottom)
            List<LzOrderedText> lines;
            if (tab.showTimestamps && cachedText != null) {
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
                tab.messages.add(tab.messages.size(),
                    LzChatLine.create(addedTick, lines.get(j), lzInd, j == lines.size() - 1));
            collapseLineCount = lines.size();
        }

        if (tab.messageCap > 0) {
            while (tab.messages.size() > tab.messageCap) tab.messages.removeLast();
        }
    }
}
