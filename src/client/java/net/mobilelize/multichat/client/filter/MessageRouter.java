package net.mobilelize.multichat.client.filter;

import net.mobilelize.fabriclib.client.LzChat;
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

        // Collect the active tab for each enabled panel (deduplicated — same tab on two panels must not double-count).
        java.util.Set<ChatTab> panelActiveTabs = new java.util.LinkedHashSet<>();
        for (ChatPanel panel : ChatRenderer.PANELS) {
            if (!panel.enabled()) continue;
            ChatTab active = panel.getActiveTab();
            if (active != null) panelActiveTabs.add(active);
        }

        // Difference pipeline: one active tab per enabled panel. If that tab has diffMode + no exception,
        // it enters the pipeline. Highest layer = highest priority. First layer with any match claims it.
        java.util.TreeMap<Integer, List<ChatTab>> diffByLayer = new java.util.TreeMap<>(java.util.Comparator.reverseOrder());
        for (ChatTab tab : panelActiveTabs) {
            if (tab.type() == TabType.FILTERED && tab.differenceMode() && !tab.differenceException())
                diffByLayer.computeIfAbsent(tab.differenceLayerIndex(), k -> new ArrayList<>()).add(tab);
        }

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
                    if (tab.differenceMode() && !tab.differenceException()) break; // handled by diff pipeline below
                    if (tab.filterRules().matches(fullText, indicator))
                        addLines(tab, tick, content, indicator, lines, rawLzText, sharedEntry);
                }
                case API -> {
                    if (tab == skipTabOnRoute) break;
                    if (!tab.filterRules().isEmpty() && tab.filterRules().matches(fullText, indicator))
                        addLines(tab, tick, content, indicator, lines, rawLzText, sharedEntry);
                }
                case SEARCH -> {
                    SearchTab st = (SearchTab) tab;
                    if (!st.liveUpdate()) break;
                    if (!st.matchesText(fullText)) break;
                    if (st.sourceTab() != null && !st.sourceTab().filterRules().matches(fullText, indicator)) break;
                    if (!st.matchesTime(tick)) break;
                    if (st.collapseIdentical()) {
                        // Save scroll positions — refilterSearch resets them to 0
                        java.util.Map<ChatPanel, Integer> savedScrolls = new java.util.HashMap<>();
                        for (ChatPanel p : ChatRenderer.PANELS) savedScrolls.put(p, p.getScrolledLines(st));
                        refilterSearch(st);
                        int maxScroll = Math.max(0, st.messageCount() - 1);
                        for (ChatPanel p : ChatRenderer.PANELS)
                            p.setScrolledLines(st, Math.min(savedScrolls.getOrDefault(p, 0), maxScroll));
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
                            ChatSelection.shiftLines(tab, linesAdded);
                        }
                    }
                    if (!ChatRenderer.isActiveInAnyPanel(tab)) tab.incrementUnread();
                }
            }
        }

        // Step 1: if any active non-diff FILTERED tab matched, diff pipeline is suppressed entirely.
        // API tabs are excluded — they receive messages independently and must not affect the pipeline.
        boolean activeNonDiffMatched = false;
        for (ChatTab tab : panelActiveTabs) {
            if (tab.type() == TabType.FILTERED && !tab.differenceMode() && !tab.differenceException()
                    && tab.filterRules().matches(fullText, indicator)) {
                activeNonDiffMatched = true; break;
            }
        }

        // Step 2: walk diff layers highest-to-lowest. First layer with any match claims the message;
        // all matching tabs at that layer receive it, then pipeline stops.
        if (!activeNonDiffMatched) {
            for (List<ChatTab> layerTabs : diffByLayer.values()) {
                boolean anyMatched = false;
                for (ChatTab tab : layerTabs) {
                    if (tab.filterRules().matches(fullText, indicator)) {
                        addLines(tab, tick, content, indicator, lines, rawLzText, sharedEntry);
                        anyMatched = true;
                    }
                }
                if (anyMatched) break;
            }
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

        ChatTab.CollapseState cs = tab.collapse();

        if (tab.type() != TabType.ALL && tab.collapseRepeating() && text.equals(cs.lastText())
                && (!tab.collapseMatchFormatting() || effectiveText.mc().equals(cs.lastContent() != null ? cs.lastContent().mc() : null))
                && cs.hasLastLines()) {
            wasCollapse = true;
            int oldCount = cs.lineCount();
            for (int i = 0; i < oldCount && tab.hasMessages(); i++)
                tab.removeFirst();

            cs.incrementRepeat();
            cs.setLastTick(tick);
            cs.setLastLines(new ArrayList<>(lines));

            List<LzOrderedText> base = stamp
                ? ChatRenderer.stampedLines(cs.lastTick(), effectiveText, indicator, lines)
                : new ArrayList<>(cs.lastLines());
            String counterStr = " " + MultichatClient.CONFIG.collapseCounterFormat
                .replace("{count}", String.valueOf(cs.repeat()));
            LzOrderedText suffix = LzText.format(counterStr).ordered();
            List<LzOrderedText> updated = new ArrayList<>(base);
            int last = updated.size() - 1;
            updated.set(last, LzOrderedText.concat(updated.get(last), suffix));

            cs.setLineCount(updated.size());
            for (int j = 0; j < updated.size(); j++) {
                boolean isEnd = j == updated.size() - 1;
                tab.addFirst(LzChatLine.create(cs.lastTick(), updated.get(j), indicator, isEnd, entry));
            }
            netLinesAdded = updated.size() - oldCount;
        } else {
            wasCollapse = false;
            cs.setLastText(text);
            cs.setLastContent(effectiveText);
            cs.setRepeat(1);
            cs.setLastTick(tick);
            cs.setLastLines(new ArrayList<>(lines));

            // ALL tab display always uses content (mixin path); other tabs use rawLzText when available
            LzText displayText = (tab.type() == TabType.ALL) ? content : effectiveText;
            List<LzOrderedText> active = stamp
                ? ChatRenderer.stampedLines(tick, displayText, indicator, lines)
                : lines;
            cs.setLineCount(active.size());
            for (int j = 0; j < active.size(); j++) {
                boolean isEnd = j == active.size() - 1;
                tab.addFirst(LzChatLine.create(tick, active.get(j), indicator, isEnd, entry));
            }
            netLinesAdded = active.size();
        }

        // Populate raw list for ALL tab only when a rawLzText (pre-Minecraft) version exists.
        // Break from rawLzText specifically so content differs from the display list.
        if (tab.type() == TabType.ALL && tab.allRawList() && rawLzText != null) {
            int indicatorW = indicator != null && indicator.hasIcon() ? indicator.iconWidth() + 6 : 0;
            int rawWidth = (int) Math.floor((double) LzChat.chatWidth() / LzChat.chatScale()) - indicatorW;
            List<LzOrderedText> rawLines = LzChat.breakLines(rawLzText, rawWidth);
            for (int j = 0; j < rawLines.size(); j++) {
                boolean isEnd = j == rawLines.size() - 1;
                tab.rawMessages().addFirst(LzChatLine.create(tick, rawLines.get(j), indicator, isEnd, entry));
            }
        }

        tab.clearWidthCaches();

        if (netLinesAdded != 0) {
            boolean freezing = MultichatClient.CONFIG.selectionFreezeChat
                && ChatSelection.isSelecting();
            ChatPanel selP = ChatSelection.getPanel();
            ChatPanel freezePanel = freezing ? selP : null;

            for (ChatPanel panel : ChatRenderer.PANELS) {
                int scrolled = panel.getScrolledLines(tab);
                boolean shouldFreeze = freezePanel == panel && panel.getActiveTab() == tab;
                if (scrolled <= 0 && !shouldFreeze) continue;
                int delta;
                if (panel.primary() || wasCollapse) {
                    delta = netLinesAdded;
                } else {
                    delta = ChatRenderer.lineCountAtWidth(effectiveText, indicator, stamp, tick, panel.resolveWidth());
                }
                panel.setScrolledLines(tab, Math.max(0, scrolled + delta));
            }
            // Secondary panels wrap at different widths → line indices grow at a different rate than
            // netLinesAdded (primary count). Use the selection panel's own delta to avoid drift.
            int shiftDelta = (selP != null && !selP.primary() && !wasCollapse && selP.getActiveTab() == tab)
                ? ChatRenderer.lineCountAtWidth(effectiveText, indicator, stamp, tick, selP.resolveWidth())
                : netLinesAdded;
            ChatSelection.shiftLines(tab, shiftDelta);
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
                // Sync raw list: remove entries no longer present in the display list
                if (tab.type() == TabType.ALL && tab.allRawList()) {
                    java.util.IdentityHashMap<net.mobilelize.fabriclib.client.LzChatEntry, Boolean> alive =
                        new java.util.IdentityHashMap<>();
                    for (LzChatLine l : tab.messages()) if (l.entry() != null) alive.put(l.entry(), Boolean.TRUE);
                    tab.rawMessages().removeIf(l -> l.entry() != null && !alive.containsKey(l.entry()));
                }
            }
        }
        if (!ChatRenderer.isActiveInAnyPanel(tab)) tab.incrementUnread();
        if (tab.hasMessages())
            MessageAddedToTabCallback.EVENT.invoker().onMessageAdded(tab, tab.getFirstMessage());
    }


    // ------------------------------------------------------------------
    // Refilter (called on tab create or rule change)
    // ------------------------------------------------------------------

    public static void refilterSearch(SearchTab tab) {
        tab.clearMessages();
        tab.clearCollapseCounts();
        for (ChatPanel panel : ChatRenderer.PANELS) panel.setScrolledLines(tab, 0);

        List<LzChatLine> source;
        if (tab.sourceTab() != null) {
            source = tab.sourceTab().messages();
        } else {
            ChatTab allTab = ChatRenderer.TABS.stream()
                .filter(t -> t.type() == TabType.ALL).findFirst().orElse(null);
            if (allTab == null) return;
            source = (allTab.allRawList() && allTab.allSearchRaw() && !allTab.rawMessages().isEmpty())
                ? allTab.rawMessages() : allTab.messages();
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

            if (count > 1) {
                appendCounter(lines, count);
                if (srcEntry != null) tab.collapseCounts().put(srcEntry, count);
            }
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
        for (ChatPanel panel : ChatRenderer.PANELS) panel.setScrolledLines(tab, 0);

        ChatTab allTab = ChatRenderer.TABS.stream()
            .filter(t -> t.type() == TabType.ALL)
            .findFirst().orElse(null);
        if (allTab == null || !allTab.hasMessages()) return;
        List<LzChatLine> all = (allTab.allRawList() && allTab.allRefilterRaw() && !allTab.rawMessages().isEmpty())
            ? allTab.rawMessages() : allTab.messages();

        boolean effectiveDiff = tab.differenceMode() && !tab.differenceException();
        // Higher-layer diff tabs (exception tabs excluded) that claim messages before this tab.
        List<ChatTab> higherLayerDiffTabs = effectiveDiff
            ? ChatRenderer.TABS.stream()
                .filter(t -> t != tab && t.type() == TabType.FILTERED && t.differenceMode()
                          && !t.differenceException()
                          && t.differenceLayerIndex() > tab.differenceLayerIndex())
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
                boolean claimedByHigherLayer = higherLayerDiffTabs.stream()
                    .anyMatch(ft -> ft.filterRules().matches(fullText, indicator));
                include = !claimedByHigherLayer && tab.filterRules().matches(fullText, indicator);
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
