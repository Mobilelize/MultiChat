package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzMessageIndicator;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.api.MessageAddedToTabCallback;
import net.mobilelize.multichat.client.filter.FilterRule;
import net.mobilelize.multichat.client.filter.FilterRuleSet;
import net.mobilelize.multichat.client.filter.StyleFilterSet;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChatManager {

    // ── App state ─────────────────────────────────────────────────────────────
    public static ChatTab   contextTab    = null;
    public static NoteTab   activeNoteTab = null;
    public static ChatPanel focusedPanel  = null;

    // ── Lists ──────────────────────────────────────────────────────────────────
    private static final List<ChatTab> tabs = new ArrayList<>(List.of(
        new ChatTab("All",     TabType.ALL,     List.of(0, 1)),
        new ChatTab("Vanilla", TabType.VANILLA, List.of(0))
    ));
    private static final List<ChatPanel> panels = new ArrayList<>();

    // ── Batched message-cap trimming (see addNewMessage) ─────────────────────────
    // Tabs currently over their cap but under the hard slack ceiling, mapped to the tick their most
    // recent message arrived — tickPendingTrims trims one once it's been quiet long enough. Weak so a
    // deleted tab (removed from ChatManager entirely) doesn't keep the debounce state alive.
    private static final Map<ChatTab, Integer> pendingTrim = new WeakHashMap<>();
    private static final int TRIM_DEBOUNCE_TICKS = 10; // ~0.5s of no new messages before trimming early

    // ── Time tracking ──────────────────────────────────────────────────────────
    private static long wallTimeStart = -1;
    private static int  tickStart     = 0;

    public static void initWallTime(int currentTick) {
        if (wallTimeStart < 0) { wallTimeStart = System.currentTimeMillis(); tickStart = currentTick; }
    }

    // ------------------------------------------------------------------
    // Real-time message processing
    // ------------------------------------------------------------------

    public static void processMessage(ChatTab tab, int tick, LzText content, LzMessageIndicator indicator, LzText rawLzText, LzChatEntry entry) {
        final String text = rawLzText.mc().getString();
        final ChatTab.CollapseState cs = tab.collapseState();
        ChatTab.MessageEntry prev = null;
        LzText rendered;

        if (tab.type() != TabType.ALL &&
                tab.collapseRepeating() &&
                text.equals(cs.lastText()) &&
                (!tab.collapseMatchFormatting() || rawLzText.mc().equals(cs.lastContent() != null ? cs.lastContent().mc() : null))) {
            prev = tab.messages().isEmpty() ? null : tab.messages().getFirst();
            cs.incrementRepeat();
            cs.setLastTick(tick);
            LzText base = tab.showTimestamps() ? stampedText(cs.lastTick(), rawLzText, indicator) : rawLzText;
            rendered = LzText.wrapMc(base.mc().copy().append(LzText.format(" " + MultichatClient.CONFIG.collapseCounterFormat.replace("{count}", String.valueOf(cs.repeat()))).mc()));
        } else {
            cs.setLastText(text);
            cs.setLastContent(rawLzText);
            cs.setRepeat(1);
            cs.setLastTick(tick);
            LzText base = (tab.type() == TabType.ALL) ? content : rawLzText;
            rendered = tab.showTimestamps() ? stampedText(tick, base, indicator) : base;
        }

        addNewMessage(tab, new ChatTab.MessageEntry(rendered, content, entry), prev);
    }

    public static void addNewMessage(ChatTab tab, ChatTab.MessageEntry entry) {
        addNewMessage(tab, entry, null);
    }

    public static void addNewMessage(ChatTab tab, ChatTab.MessageEntry entry, @Nullable ChatTab.MessageEntry prevEntry) {
        if (prevEntry != null) {
            tab.removeFirst();
            for (ChatPanel panel : getAllActivePanels())
                panel.removeTabEntry(tab, prevEntry);
        }

        tab.addFirst(entry);
        ChatPanel selP = ChatSelection.getPanel();
        int selDelta = 0;
        for (ChatPanel panel : getAllActivePanels()) {
            int before = panel.tabLineCount(tab);
            panel.addTabMessage(tab, entry);
            int delta = panel.tabLineCount(tab) - before;
            panel.shiftScrollForAdd(tab, delta);
            if (panel == selP) selDelta = delta;
        }
        ChatSelection.shiftLines(tab, selDelta);

        // The All tab isn't user-configurable per-tab (no messageCap UI for it), so it always reports
        // 0 (unlimited) there — the global cap is what actually bounds it. -1 (or 0) means unlimited.
        int effectiveCap = (tab.type() == TabType.ALL && MultichatClient.CONFIG.globalMessageCap > 0)
            ? MultichatClient.CONFIG.globalMessageCap
            : tab.messageCap();
        if (effectiveCap > 0 && tab.messageCount() > effectiveCap) {
            // Trimming is an O(n) scan (removeTabEntry/removeIf below) over every panel showing this
            // tab. Below the threshold that's already instant, so stay exact/immediate — this is the
            // behavior per-tab caps have always used, since they're normally small enough to never
            // cross it. It's not that per-tab caps work differently; they just never hit this branch.
            if (!MultichatClient.CONFIG.optimizedDeletingEnabled
                    || tab.messageCount() <= MultichatClient.CONFIG.optimizedDeletingThreshold) {
                pendingTrim.remove(tab);
                trimTabToCap(tab, effectiveCap);
            } else {
                // Large tab: batch instead of paying that O(n) cost every message during a burst. Let
                // it overshoot by a slack margin — trimmed the instant that margin's hit (hard ceiling,
                // bounds memory even through a sustained burst that never lets up), or sooner, the next
                // tick after messages stop arriving for a moment (debounce — see tickPendingTrims).
                int trimSlack = Math.max(1, effectiveCap / 20); // ~5% overshoot allowed
                if (tab.messageCount() > effectiveCap + trimSlack) {
                    pendingTrim.remove(tab);
                    trimTabToCap(tab, effectiveCap);
                } else {
                    pendingTrim.put(tab, LzChat.ticks());
                }
            }
        }

        if (!isTabActiveInAnyPanel(tab)) tab.incrementUnread();
        if (tab.hasMessages()) MessageAddedToTabCallback.EVENT.invoker().onMessageAdded(tab, tab.getFirstMessage());
    }

    /** Trims the oldest messages off {@code tab} until it's back at {@code cap}, removing the same
     *  entries from every active panel's line cache and fixing up scroll offsets. Shared by the
     *  immediate and batched paths in addNewMessage, and by tickPendingTrims's debounce trim. */
    // Guards trimFeedbackEnabled's own chat message from re-triggering itself: that message routes
    // back through the normal chat pipeline (ChatHudMixin -> MessageRouter -> addNewMessage), which
    // can push the same tab over its cap again and call trimTabToCap right back from inside this
    // method's own call stack. Suppressing the feedback send (not the trim itself) while one is
    // already in flight breaks the cycle after one message instead of recursing forever.
    private static boolean sendingTrimFeedback = false;

    private static void trimTabToCap(ChatTab tab, int cap) {
        long startNanos = System.nanoTime();
        IdentityHashMap<LzChatEntry, Boolean> toRemove = new IdentityHashMap<>();
        while (tab.messageCount() > cap) {
            toRemove.put(tab.messages().getLast().entry(), Boolean.TRUE);
            tab.removeLast();
        }
        if (toRemove.isEmpty()) return;
        for (ChatPanel panel : getAllActivePanels()) {
            List<LzChatLine> lines = panel.getTabLines(tab);
            if (lines == null) continue;
            // The oldest entries are always a contiguous block at the very tail of a panel's line
            // list — wrapEntryInto only ever prepends, so line order mirrors tab.messages()'s
            // newest-first order exactly, one entry's lines together, same invariant replaceEntryLines
            // relies on elsewhere. Trimming is therefore a tail walk, not a full-list removeIf — cost
            // scales with how many lines are actually being cut, not with the whole tab's size. This
            // is what made a batched trim on a large tab expensive: a full O(n) scan on every trim
            // regardless of how little of it was actually being removed.
            int i = lines.size();
            while (i > 0) {
                LzChatEntry e = lines.get(i - 1).entry();
                if (e == null || !toRemove.containsKey(e)) break;
                i--;
            }
            int linesRemoved = lines.size() - i;
            if (linesRemoved == 0) continue;
            lines.subList(i, lines.size()).clear();
            int scrolled = panel.getScrolledLines(tab);
            if (scrolled > 0) panel.setScrolledLines(tab, Math.max(0, scrolled - linesRemoved));
        }
        ChatSelection.clampOrClear(tab);

        // Debug-only perf visibility into the trimming this whole cap system does — prefixed so it can
        // be routed to its own tab with a whitelist rule. Goes through the normal chat pipeline (not a
        // direct tab insert) so it's filterable like any other message. Gated on trimFeedbackEnabled,
        // separate from debugCommandsEnabled, since this isn't something a regular user needs spammed
        // on every trim.
        if (MultichatClient.CONFIG.trimFeedbackEnabled && !sendingTrimFeedback && LzChat.chatHud() != null) {
            double elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0;
            LzText msg = LzText.literal(String.format("[MultiChat-Trim] Deleted %d message(s) from '%s' in %.2fms",
                toRemove.size(), tab.name(), elapsedMs));
            sendingTrimFeedback = true;
            try {
                LzChat.chatHud().addMessage(msg.mc(), null, null);
            } finally {
                sendingTrimFeedback = false;
            }
        }
    }

    /** Called once per client tick (see MultichatClient). Trims any tab that's over its cap but under
     *  the hard slack ceiling once it's gone TRIM_DEBOUNCE_TICKS without a new message — catches a
     *  natural lull so the tab doesn't sit at its full overshoot margin until the next burst pushes it
     *  over the hard ceiling. Cheap: this map is normally empty, and only ever holds tabs that are
     *  both over-cap and Optimized Deleting-eligible, which is a rare combination in practice. */
    public static void tickPendingTrims(int currentTick) {
        if (pendingTrim.isEmpty()) return;
        Iterator<Map.Entry<ChatTab, Integer>> it = pendingTrim.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ChatTab, Integer> e = it.next();
            if (currentTick - e.getValue() < TRIM_DEBOUNCE_TICKS) continue;
            ChatTab tab = e.getKey();
            it.remove();
            int cap = (tab.type() == TabType.ALL && MultichatClient.CONFIG.globalMessageCap > 0)
                ? MultichatClient.CONFIG.globalMessageCap
                : tab.messageCap();
            if (cap > 0) trimTabToCap(tab, cap);
        }
    }

    // ------------------------------------------------------------------
    // Refilter
    // ------------------------------------------------------------------

    /** Bits of tab/global config the pure compute functions below need — snapshotted once at submit
     *  time (main thread) instead of read live off the background thread, per the "snapshot inputs"
     *  rule used everywhere else this codebase does background work (see WrapCoordinator). This
     *  covers every genuinely-mutable bit the matching loop touches — including filter rules, which
     *  earlier only snapshotted the primitives here and read filterRules() live: TabEditScreen's
     *  Save mutates a FilterRuleSet's whitelist/blacklist ArrayLists in place (clear() + repeated
     *  add()), completely unsynchronized, so a background scan reading those same live lists while a
     *  save is touching them races on plain ArrayLists with no protection at all — not just a
     *  stale-value risk but a genuinely corrupted-iteration one, which is exactly the kind of
     *  "sometimes 2 messages pass a blacklist-everything rule, sometimes more, nothing else changed"
     *  symptom that showed up. tab.matchesText/matchesTime (SearchTab, all-final fields) are still
     *  read live — those fields are actually immutable by construction, no snapshot needed. */
    /** A tab's filterRules() + styleRules(), both snapshotted, matched together — MessageRouter gates
     *  every FILTERED-tab check on {@code filterRules().matches(...) && styleRules().matches(raw)}
     *  (see its FILTERED/diff-mode/routeToSubTabs branches); refilterTab only ever checked filterRules
     *  and silently ignored styleRules entirely, so a refilter could disagree with what live routing
     *  would have put there. This pairs them so every place refilterTab checks a tab's rules checks
     *  both, the same way MessageRouter does. */
    private record TabRuleSnapshot(FilterRuleSet.Snapshot filterRules, StyleFilterSet.Snapshot styleRules) {
        static TabRuleSnapshot of(ChatTab t) {
            return new TabRuleSnapshot(t.filterRules().snapshot(), t.styleRules().snapshot());
        }
        boolean matches(String filterText, LzMessageIndicator indicator, LzText raw) {
            return filterRules.matches(filterText, indicator) && styleRules.matches(raw);
        }
    }

    private record RefilterConfig(boolean stripObjectContent, boolean normalizeFilterText,
                                   String collapseCounterFormat, boolean showTimestamps, int messageCap,
                                   boolean collapseRepeating, boolean collapseMatchFormatting,
                                   boolean forceHighlightMatches,
                                   TabRuleSnapshot ownRules, @Nullable TabRuleSnapshot parentGateRules,
                                   List<TabRuleSnapshot> higherDiffRules) {
        static RefilterConfig snapshot(ChatTab tab, @Nullable ChatTab parentGate, List<ChatTab> higherDiffTabs) {
            return new RefilterConfig(MultichatClient.CONFIG.stripObjectContent, MultichatClient.CONFIG.normalizeFilterText,
                MultichatClient.CONFIG.collapseCounterFormat, tab.showTimestamps(), tab.messageCap(),
                tab.collapseRepeating(), tab.collapseMatchFormatting(), tab.forceHighlightMatches(),
                TabRuleSnapshot.of(tab),
                parentGate != null ? TabRuleSnapshot.of(parentGate) : null,
                higherDiffTabs.stream().map(TabRuleSnapshot::of).toList());
        }
    }

    private record SearchRefilterResult(List<ChatTab.MessageEntry> entries, Map<LzChatEntry, Integer> collapseCounts) {
        static final SearchRefilterResult EMPTY = new SearchRefilterResult(List.of(), Map.of());
    }

    /** A panel's wrap inputs, snapshotted on the main thread before an offloaded refilter job starts —
     *  {@code resolveWidth()}/{@code primary()} are only safe to read from the main thread, same as
     *  every other "snapshot inputs" spot in this file. Lets the background job build each active
     *  panel's finished lines itself instead of the main thread having to bounce out to a second
     *  background job (WrapCoordinator) afterward — see refilterSearch/refilterTab. */
    /** {@code lineGeneration} is captured from {@code panel.showLoadingSkeleton(tab)} — the same
     *  counter WrapCoordinator checks before applying a rewrap — so a refilter superseded by an
     *  unrelated background rewrap (or a second refilter) started on the same panel/tab before this
     *  one lands is recognized as stale and dropped instead of appending onto newer content. */
    private record PanelWrapSpec(ChatPanel panel, int width, boolean primary, long lineGeneration) {
        static List<PanelWrapSpec> snapshot(ChatTab tab, List<ChatPanel> panels) {
            return panels.stream().map(p -> new PanelWrapSpec(p, p.resolveWidth(), p.primary(), p.showLoadingSkeleton(tab))).toList();
        }
    }

    public static void refilterSearch(SearchTab tab) {
        long generation = tab.bumpRefilterGeneration();
        for (ChatPanel panel : getAllActivePanels()) {
            panel.clearTabLines(tab);
            panel.setScrolledLines(tab, 0);
        }

        ChatTab srcTab = tab.sourceTab() != null ? tab.sourceTab() : tabs.stream().filter(t -> t.type() == TabType.ALL).findFirst().orElse(null);
        if (srcTab == null || !srcTab.hasMessages()) {
            applySearchRefilterResult(tab, generation, SearchRefilterResult.EMPTY);
            return;
        }

        List<ChatTab.MessageEntry> snapshot = new ArrayList<>(srcTab.messages());
        boolean collapseIdentical = tab.collapseIdentical();
        // sourceTab()'s own filterRules() — same snapshot-not-live requirement as RefilterConfig's,
        // see its javadoc.
        FilterRuleSet.Snapshot sourceRules = tab.sourceTab() != null ? tab.sourceTab().filterRules().snapshot() : null;
        RefilterConfig cfg = RefilterConfig.snapshot(tab, null, List.of());

        boolean worthOffloading = MultichatClient.CONFIG.optimizedRefilterEnabled
            && snapshot.size() >= MultichatClient.CONFIG.optimizedRefilterThreshold;

        if (!worthOffloading) {
            applySearchRefilterResult(tab, generation, computeSearchRefilter(tab, snapshot, collapseIdentical, sourceRules, cfg));
            return;
        }

        // Offloaded path: matching/collapse AND every active panel's line-wrap happen in the SAME
        // background job, so there's exactly one skeleton and one main-thread callback for the whole
        // refilter — not a filter job followed by a second, separate wrap job (which used to re-show
        // the skeleton and leave a window where tab.messages() had already moved on but nothing on
        // screen reflected it — results flashing in and then vanishing).
        List<PanelWrapSpec> panelSpecs = PanelWrapSpec.snapshot(tab, getAllActivePanels());
        FilterCoordinator.submit(tab, generation, () -> {
            SearchRefilterResult r = computeSearchRefilter(tab, snapshot, collapseIdentical, sourceRules, cfg);
            Map<ChatPanel, List<LzChatLine>> panelLines = new HashMap<>();
            Map<ChatPanel, Long> panelGenerations = new HashMap<>();
            for (PanelWrapSpec spec : panelSpecs) {
                panelLines.put(spec.panel(), ChatPanel.buildTabLines(r.entries(), spec.width(), spec.primary()));
                panelGenerations.put(spec.panel(), spec.lineGeneration());
            }
            return () -> applySearchRefilterResult(tab, generation, r, panelLines, panelGenerations);
        });
    }

    /** Pure — reads only {@code tab}'s final search fields (query/mode/case/time range, via
     *  matchesText/matchesTime), the pre-taken rule/config snapshots, never mutates {@code tab}.
     *  Safe to run on FilterCoordinator's background thread. Same matching/collapse-identical logic
     *  refilterSearch always used, just building a plain result list instead of mutating the tab's
     *  live message deque as it goes. */
    private static SearchRefilterResult computeSearchRefilter(SearchTab tab, List<ChatTab.MessageEntry> source,
                                                                boolean collapseIdentical, @Nullable FilterRuleSet.Snapshot sourceRules,
                                                                RefilterConfig cfg) {
        List<ChatTab.MessageEntry> result = new ArrayList<>();
        Map<LzChatEntry, Integer> collapseCounts = new HashMap<>();

        if (collapseIdentical) {
            LinkedHashMap<String, ChatTab.MessageEntry> newest = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
            for (ChatTab.MessageEntry me : source) {
                if (me.entry().originalContent == null) continue;
                String text = me.entry().originalContent.mc().getString();
                String fText1 = cfg.stripObjectContent()
                    ? LzText.stringSkippingObjects(me.entry().originalContent.mc()) : text;
                if (cfg.normalizeFilterText()) fText1 = FilterRule.normalizeFilterText(fText1);
                if (!tab.matchesText(fText1) || !tab.matchesTime(me.entry().creationTick)) continue;
                if (sourceRules != null && !sourceRules.matches(fText1, me.entry().indicator)) continue;
                newest.putIfAbsent(text, me);
                counts.merge(text, 1, Integer::sum);
            }
            // source is newest-first; LinkedHashMap keeps first-seen (= newest) per distinct text,
            // in that same newest-first insertion order — no reversal needed, unlike the old
            // addLast-into-a-live-deque version this replaced.
            for (Map.Entry<String, ChatTab.MessageEntry> e : newest.entrySet()) {
                ChatTab.MessageEntry me = e.getValue();
                int count = counts.get(e.getKey());
                LzText base = cfg.showTimestamps()
                    ? stampedText(me.entry().creationTick, me.entry().originalContent, me.entry().indicator)
                    : me.entry().originalContent;
                LzText rendered = count > 1
                    ? LzText.wrapMc(base.mc().copy().append(LzText.format(" " + cfg.collapseCounterFormat().replace("{count}", String.valueOf(count))).mc()))
                    : base;
                if (count > 1) collapseCounts.put(me.entry(), count);
                result.add(me.newRender(rendered));
            }
        } else {
            for (ChatTab.MessageEntry me : source) { // already newest-first
                if (me.entry().originalContent == null) continue;
                String text = me.entry().originalContent.mc().getString();
                String fText2 = cfg.stripObjectContent()
                    ? LzText.stringSkippingObjects(me.entry().originalContent.mc()) : text;
                if (cfg.normalizeFilterText()) fText2 = FilterRule.normalizeFilterText(fText2);
                if (!tab.matchesText(fText2)) continue;
                if (sourceRules != null && !sourceRules.matches(fText2, me.entry().indicator)) continue;
                if (!tab.matchesTime(me.entry().creationTick)) continue;
                LzText base = cfg.showTimestamps()
                    ? stampedText(me.entry().creationTick, me.entry().originalContent, me.entry().indicator)
                    : me.entry().originalContent;
                result.add(me.newRender(base));
            }
        }

        if (cfg.messageCap() > 0)
            while (result.size() > cfg.messageCap()) result.remove(result.size() - 1); // trim oldest = tail

        return new SearchRefilterResult(result, collapseCounts);
    }

    /** Guarded by a generation check — a result from a scan that's since been superseded (query
     *  changed again before this one finished) is dropped instead of overwriting newer content.
     *  Sync (non-offloaded) path only — routes through rebuildTabLines, which independently decides
     *  whether wrapping this (small) result is itself worth offloading. */
    private static void applySearchRefilterResult(SearchTab tab, long generation, SearchRefilterResult r) {
        if (generation != tab.refilterGeneration()) return;
        tab.clearMessages();
        tab.clearCollapseCounts();
        for (ChatTab.MessageEntry me : r.entries()) tab.addLast(me); // already newest-first; addLast preserves that order
        tab.collapseCounts().putAll(r.collapseCounts());
        for (ChatPanel panel : getAllActivePanels()) panel.rebuildTabLines(tab);
    }

    /** One background entry (by identity) whose rendering needs replacing because a live-tracked count
     *  for the same text needs folding into it, plus what to replace it with — see
     *  applySearchRefilterResult's offloaded overload. */
    private record SearchMergePatch(ChatTab.MessageEntry liveEntry, ChatTab.MessageEntry originalBgEntry, ChatTab.MessageEntry mergedEntry) {}

    /** Finds the contiguous run of lines belonging to {@code oldEntry} (by identity — buildTabLines
     *  always wraps one entry's lines together, in order) and swaps it for {@code newLines}. Touches
     *  only that one entry's slice, not the rest of {@code lines}. Returns {@code lines} unchanged if
     *  {@code oldEntry} isn't found in it (nothing to swap). */
    private static List<LzChatLine> replaceEntryLines(List<LzChatLine> lines, LzChatEntry oldEntry, List<LzChatLine> newLines) {
        int start = -1, end = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).entry() == oldEntry) {
                if (start < 0) start = i;
                end = i + 1;
            } else if (start >= 0) break;
        }
        if (start < 0) return lines;
        List<LzChatLine> result = new ArrayList<>(lines.size() - (end - start) + newLines.size());
        result.addAll(lines.subList(0, start));
        result.addAll(newLines);
        result.addAll(lines.subList(end, lines.size()));
        return result;
    }

    /** Offloaded path only — {@code panelLines} already holds each panel's finished wrap, built in the
     *  same background job as the filter/collapse pass (see refilterSearch), for the UN-merged {@code r}
     *  — merging needs tab.collapseCounts()/tab.messages() as they stand right now, main thread only, so
     *  it happens here. collapseIdentical search tabs always start empty (see refilterSearch — a new
     *  SearchTab every search), so whatever's in tab.messages()/collapseCounts() right now is purely
     *  "what arrived live during this one scan" — always small. Two things can happen to a live entry:
     *  <p>
     *  1. Its text matches a background entry — fold the live count into that entry and re-render with
     *     the combined total (the count map was already being merged before this; the actual displayed
     *     message never got told about it).
     *  2. Its text matches nothing in the background result — genuinely new, would otherwise be silently
     *     dropped by the clear+rebuild below. Kept, in its current (already newest-first) order, ahead
     *     of the background block.
     *  <p>
     *  Installs the merged data model, then patches just the handful of changed entries' lines into each
     *  panel's precomputed wrap — same one-entry-touch cost as an ordinary live collapse-repeat, not a
     *  rewrap of the tab and not a second background job; live-only entries need no panel patch at all,
     *  since their lines are already sitting in the panel's cache and applyWrapResult's own live-prefix
     *  scan preserves them once the matched ones are removed. A panel with no entry in {@code panelLines}
     *  became active after the job was submitted — falls back to a normal (possibly offloaded) rebuild
     *  for just that panel. */
    private static void applySearchRefilterResult(SearchTab tab, long generation, SearchRefilterResult r,
                                                    Map<ChatPanel, List<LzChatLine>> panelLines,
                                                    Map<ChatPanel, Long> panelGenerations) {
        if (generation != tab.refilterGeneration()) return;

        Map<String, ChatTab.MessageEntry> liveByText = new HashMap<>();
        if (tab.collapseIdentical())
            for (ChatTab.MessageEntry me : tab.messages())
                if (me.entry().originalContent != null)
                    liveByText.put(me.entry().originalContent.mc().getString(), me);

        List<ChatTab.MessageEntry> finalEntries = new ArrayList<>(r.entries().size());
        Map<LzChatEntry, Integer> finalCounts = new HashMap<>(r.collapseCounts());
        List<SearchMergePatch> patches = new ArrayList<>();
        IdentityHashMap<LzChatEntry, Boolean> matchedLiveEntries = new IdentityHashMap<>();
        for (ChatTab.MessageEntry me : r.entries()) {
            String text = me.entry().originalContent != null ? me.entry().originalContent.mc().getString() : null;
            ChatTab.MessageEntry liveEntry = text != null ? liveByText.get(text) : null;
            if (liveEntry == null) { finalEntries.add(me); continue; }

            matchedLiveEntries.put(liveEntry.entry(), Boolean.TRUE);
            int liveCount = tab.collapseCounts().getOrDefault(liveEntry.entry(), 1);
            int mergedCount = liveCount + finalCounts.getOrDefault(me.entry(), 1);
            LzText base = tab.showTimestamps()
                ? stampedText(me.entry().creationTick, me.entry().originalContent, me.entry().indicator)
                : me.entry().originalContent;
            LzText rendered = LzText.wrapMc(base.mc().copy().append(LzText.format(" "
                + MultichatClient.CONFIG.collapseCounterFormat.replace("{count}", String.valueOf(mergedCount))).mc()));
            ChatTab.MessageEntry mergedEntry = me.newRender(rendered);
            finalEntries.add(mergedEntry);
            finalCounts.put(me.entry(), mergedCount);
            patches.add(new SearchMergePatch(liveEntry, me, mergedEntry));
        }

        // Live entries that never matched anything in the background result are genuinely new — without
        // this they'd be silently dropped by clearMessages()+rebuild below (the actual clobber bug).
        // tab.messages() is already newest-first (live routing only ever addFirst()s), so walking it and
        // keeping whatever wasn't folded into a match, in that same order, ahead of the background
        // block, reconstructs the correct final order at effectively zero extra cost.
        if (!tab.messages().isEmpty()) {
            List<ChatTab.MessageEntry> liveOnly = new ArrayList<>();
            for (ChatTab.MessageEntry me : tab.messages()) {
                if (matchedLiveEntries.containsKey(me.entry())) continue;
                liveOnly.add(me);
                // Carry its own live collapse count forward too — otherwise a live-only entry that
                // already duplicated 2+ times during the scan loses that count on the
                // clearCollapseCounts()+putAll() below, and the next duplicate resumes from a lower
                // number than it should (addSearchMessageWithCollapse's getOrDefault(entry, 1) sees
                // nothing here and restarts from 1).
                Integer liveOnlyCount = tab.collapseCounts().get(me.entry());
                if (liveOnlyCount != null) finalCounts.put(me.entry(), liveOnlyCount);
            }
            if (!liveOnly.isEmpty()) {
                liveOnly.addAll(finalEntries);
                finalEntries = liveOnly;
            }
        }

        tab.clearMessages();
        tab.clearCollapseCounts();
        for (ChatTab.MessageEntry me : finalEntries) tab.addLast(me); // already newest-first; addLast preserves that order
        tab.collapseCounts().putAll(finalCounts);

        for (ChatPanel panel : getAllActivePanels()) {
            List<LzChatLine> lines = panelLines.get(panel);
            if (lines == null) { panel.rebuildTabLines(tab); continue; }
            // A different background job already bumped this panel's line-cache generation since this
            // one was submitted — drop this stale result instead of duplicating/clobbering (see
            // applyFilterTabResult's identical guard / applyWrapResult).
            Long expected = panelGenerations.get(panel);
            if (expected != null && panel.currentLineGeneration(tab) != expected) continue;
            if (!patches.isEmpty()) {
                int width = panel.resolveWidth();
                boolean primary = panel.primary();
                for (SearchMergePatch p : patches) {
                    panel.removeTabEntry(tab, p.liveEntry());
                    List<LzChatLine> newLines = ChatPanel.buildTabLines(List.of(p.mergedEntry()), width, primary);
                    lines = replaceEntryLines(lines, p.originalBgEntry().entry(), newLines);
                }
            }
            panel.applyWrapResult(tab, lines);
        }
    }

    public static void refilterTab(ChatTab tab) {
        if (tab.type() != TabType.FILTERED) return;
        long generation = tab.bumpRefilterGeneration();
        // Clear now, not at apply — the old content is fully reconstructable from allTab any time, so
        // there's no reason to hold onto it while the scan runs. Live routing (processMessage) isn't
        // touched by this — it keeps adding to what's now an empty list, same as always, and its own
        // collapse-repeat check (text.equals(cs.lastText())) keeps working correctly for whatever
        // arrives, since nothing clears/resets it again before applyFilterTabResult appends the
        // background result after it (see there for the other half of this).
        tab.clearMessages();
        // If this is a multi-tab's sub-tab, live routing only ever lets it see what the parent's own
        // filter already let through (MessageRouter.routeToSubTabs) — refilter needs the same gate,
        // otherwise it'd show messages the parent would have blocked live.
        ChatTab parentGate = findParentOf(tab);
        for (ChatPanel panel : getAllActivePanels()) {
            panel.clearTabLines(tab);
            panel.setScrolledLines(tab, 0);
        }

        ChatTab allTab = tabs.stream().filter(t -> t.type() == TabType.ALL).findFirst().orElse(null);
        if (allTab == null || !allTab.hasMessages()) {
            applyFilterTabResult(tab, generation, new FilterTabResult(List.of(), null, null, 0, 0), null, null);
            return;
        }

        boolean effectiveDiff = tab.differenceMode() && !tab.differenceException();
        List<ChatTab> higherDiffTabs = effectiveDiff
            ? tabs.stream()
                .filter(t -> t != tab && t.type() == TabType.FILTERED
                          && t.differenceMode() && !t.differenceException()
                          && t.differenceLayerIndex() > tab.differenceLayerIndex())
                .toList()
            : List.of();

        List<ChatTab.MessageEntry> snapshot = new ArrayList<>(allTab.messages());
        RefilterConfig cfg = RefilterConfig.snapshot(tab, parentGate, higherDiffTabs);

        boolean worthOffloading = MultichatClient.CONFIG.optimizedRefilterEnabled
            && snapshot.size() >= MultichatClient.CONFIG.optimizedRefilterThreshold;

        if (!worthOffloading) {
            applyFilterTabResult(tab, generation, computeFilterTab(snapshot, effectiveDiff, cfg), null, null);
            return;
        }

        // Same reasoning as refilterSearch's offloaded path — fold filtering AND every active panel's
        // line-wrap into one background job, so there's one skeleton and one main-thread callback
        // instead of a filter job bouncing to the main thread only to kick off a second, separate wrap.
        List<PanelWrapSpec> panelSpecs = PanelWrapSpec.snapshot(tab, getAllActivePanels());
        FilterCoordinator.submit(tab, generation, () -> {
            FilterTabResult result = computeFilterTab(snapshot, effectiveDiff, cfg);
            Map<ChatPanel, List<LzChatLine>> panelLines = new HashMap<>();
            Map<ChatPanel, Long> panelGenerations = new HashMap<>();
            for (PanelWrapSpec spec : panelSpecs) {
                panelLines.put(spec.panel(), ChatPanel.buildTabLines(result.entries(), spec.width(), spec.primary()));
                panelGenerations.put(spec.panel(), spec.lineGeneration());
            }
            return () -> applyFilterTabResult(tab, generation, result, panelLines, panelGenerations);
        });
    }

    /** {@code entries} — the built list, same as before. The trailing four fields are whatever the
     *  collapse-run tracking below landed on for the NEWEST included entry (the last one processed,
     *  since the loop runs oldest-to-newest) — the exact shape of ChatTab.CollapseState. The background
     *  thread can't touch tab.collapseState() itself, so this carries that state back to the main
     *  thread instead of it getting thrown away when the loop ends (see applyFilterTabResult). */
    private record FilterTabResult(List<ChatTab.MessageEntry> entries, @Nullable String lastText,
                                    @Nullable LzText lastContent, int lastRepeat, int lastTick) {}

    /** Pure — reads only the pre-taken rule/config snapshot in {@code cfg}, never touches a live
     *  ChatTab. Safe to run on FilterCoordinator's background thread. Same collapse-run-length logic
     *  refilterTab always used (a repeat replaces the single in-progress entry with an updated
     *  counter), just tracked against a local deque instead of the tab's live one. */
    private static FilterTabResult computeFilterTab(List<ChatTab.MessageEntry> all,
                                                      boolean effectiveDiff, RefilterConfig cfg) {
        ArrayDeque<ChatTab.MessageEntry> result = new ArrayDeque<>();
        String collapseLastText   = null;
        LzText collapseLastLzText = null;
        int    collapseRepeat     = 0;
        int    collapseLastTick   = 0;

        for (int i = all.size() - 1; i >= 0; i--) {
            ChatTab.MessageEntry src = all.get(i);
            LzText raw = src.entry().originalContent;
            if (raw == null) continue;
            String fullText = raw.mc().getString();
            String objText = cfg.stripObjectContent()
                ? LzText.stringSkippingObjects(raw.mc()) : fullText;
            final String filterText = cfg.normalizeFilterText()
                ? FilterRule.normalizeFilterText(objText) : objText;
            LzMessageIndicator indicator = src.entry().indicator;

            // Mirrors MessageRouter's FILTERED case: a diff-mode tab without its exception never gets
            // live messages through this simple bypass at all (it's driven by the diff branch below
            // instead), so the bypass only applies to the plain, non-diff match check — same as live.
            boolean forcedByHighlight = !effectiveDiff && cfg.forceHighlightMatches() && src.entry().highlightMatched;
            boolean include = forcedByHighlight ? true : (effectiveDiff
                ? cfg.higherDiffRules().stream().noneMatch(r -> r.matches(filterText, indicator, raw))
                  && cfg.ownRules().matches(filterText, indicator, raw)
                : cfg.ownRules().matches(filterText, indicator, raw));
            if (include && cfg.parentGateRules() != null && !cfg.parentGateRules().matches(filterText, indicator, raw))
                include = false;
            if (!include) continue;

            boolean doCollapse = cfg.collapseRepeating()
                && fullText.equals(collapseLastText)
                && (!cfg.collapseMatchFormatting() || raw.mc().equals(collapseLastLzText.mc()));

            if (doCollapse) {
                collapseRepeat++;
                result.removeFirst();
            } else {
                collapseRepeat     = 1;
                collapseLastText   = fullText;
                collapseLastLzText = raw;
            }
            collapseLastTick = src.entry().creationTick;

            LzText base = cfg.showTimestamps()
                ? stampedText(src.entry().creationTick, raw, indicator)
                : raw;
            LzText rendered = collapseRepeat > 1
                ? LzText.wrapMc(base.mc().copy().append(LzText.format(" " + cfg.collapseCounterFormat()
                    .replace("{count}", String.valueOf(collapseRepeat))).mc()))
                : base;

            result.addFirst(src.newRender(rendered));
        }

        List<ChatTab.MessageEntry> list = new ArrayList<>(result);
        if (cfg.messageCap() > 0)
            while (list.size() > cfg.messageCap()) list.remove(list.size() - 1); // trim oldest = tail
        return new FilterTabResult(list, collapseLastText, collapseLastLzText, collapseRepeat, collapseLastTick);
    }

    /** Recovers the repeat-count baked into an already-rendered message's counter suffix (e.g. the
     *  "5" in "message (x5)"), for a run that's no longer the tab's live-tracked one — something else
     *  arrived after it, so tab.collapseState() moved on and this run's count only still exists baked
     *  into its own rendered text. Locates the counter format's own plain-text shape once (rendering it
     *  with a "0" placeholder) instead of assuming any particular literal format, since the format is
     *  user-configurable. Returns null (caller treats it as "no count to recover", i.e. 1) if the
     *  rendered text doesn't look like the expected shape — safer to skip the merge than guess wrong. */
    private static @Nullable Integer parseTrailingCount(ChatTab.MessageEntry entry, String baseText) {
        String full = entry.rendered().mc().getString();
        int baseEnd = full.lastIndexOf(baseText);
        if (baseEnd < 0) return null;
        String suffix = full.substring(baseEnd + baseText.length());
        String template = LzText.format(" " + MultichatClient.CONFIG.collapseCounterFormat.replace("{count}", "0")).mc().getString();
        int digitIdx = template.indexOf('0');
        if (digitIdx < 0) return null;
        String prefix = template.substring(0, digitIdx);
        String tail   = template.substring(digitIdx + 1);
        if (!suffix.startsWith(prefix) || !suffix.endsWith(tail) || suffix.length() < prefix.length() + tail.length())
            return null;
        try {
            return Integer.parseInt(suffix.substring(prefix.length(), suffix.length() - tail.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Guarded by a generation check — a result from a scan that's since been superseded (filter
     *  rules edited again before this one finished) is dropped instead of overwriting newer content.
     *  {@code panelLines} is null for the sync (non-offloaded) path, which just routes every panel
     *  through rebuildTabLines instead. No clearMessages() here — refilterTab already cleared at
     *  trigger time; clearing again would reset CollapseState a second time and wipe out whatever live
     *  routing correctly built up while this ran.
     *  <p>
     *  Whatever's in tab.messages() right now, before {@code result.entries()} gets appended, is
     *  entirely live arrivals (see refilterTab's early clear). The one that matters for merging is
     *  whichever sits at the END of that block (the OLDEST live arrival — adjacent to the seam where
     *  {@code result.entries()} lands), NOT tab.collapseState()/the newest one: if something else
     *  arrived live after a repeat run (e.g. a genuinely different message right as the scan finished),
     *  collapseState moved on to track THAT instead, while the run this refilter actually needs to
     *  continue is still sitting frozen further back in the list. Three cases:
     *  <p>
     *  1. Nothing arrived live (tab still empty) — the background result's own trailing run becomes the
     *     tab's actual newest content, so its collapse state (computeFilterTab's last-processed-entry
     *     state, carried in {@code result} since the background thread can't touch tab.collapseState()
     *     itself) gets adopted for future live messages to continue from, instead of being thrown away.
     *  2. The seam-adjacent live entry is the SAME run as the background's own newest entry (same text,
     *     and — respecting collapseMatchFormatting, same "special rule" computeFilterTab itself follows
     *     — same exact formatting if that's on) — these are one logical run that got split by the scan
     *     boundary (e.g. "asaab (x19)" then, after the rebuild, a separately-counted "asaab (x2)"
     *     instead of one "asaab (x21)"). Folded into a single re-rendered entry with the combined count
     *     instead of shown as two.
     *  3. The seam-adjacent live entry is a different message — already correct and newer than the
     *     background result, left alone. */
    private static void applyFilterTabResult(ChatTab tab, long generation, FilterTabResult result,
                                              @Nullable Map<ChatPanel, List<LzChatLine>> panelLines,
                                              @Nullable Map<ChatPanel, Long> panelGenerations) {
        if (generation != tab.refilterGeneration()) return;
        ChatTab.CollapseState cs = tab.collapseState();

        boolean wasOnlyLiveEntry = tab.messageCount() == 1;
        ChatTab.MessageEntry liveLast = tab.hasMessages() ? tab.messages().get(tab.messageCount() - 1) : null;
        String liveLastText = liveLast != null && liveLast.entry().originalContent != null
            ? liveLast.entry().originalContent.mc().getString() : null;

        boolean merge = tab.collapseRepeating() && liveLastText != null && !result.entries().isEmpty()
            && result.lastText() != null && result.lastText().equals(liveLastText)
            && (!tab.collapseMatchFormatting() || (result.lastContent() != null
                && liveLast.entry().originalContent.mc().equals(result.lastContent().mc())));

        List<ChatTab.MessageEntry> toAppend = result.entries();
        ChatTab.MessageEntry removedSeamEntry = null;
        ChatTab.MessageEntry mergedEntry       = null;
        if (merge) {
            Integer liveCount = wasOnlyLiveEntry ? Integer.valueOf(cs.repeat()) : parseTrailingCount(liveLast, liveLastText);
            int mergedCount = (liveCount != null ? liveCount : 1) + result.lastRepeat();
            removedSeamEntry = liveLast;
            ChatTab.MessageEntry bgFirst = result.entries().get(0);
            LzText raw = bgFirst.entry().originalContent;
            LzText base = tab.showTimestamps()
                ? stampedText(bgFirst.entry().creationTick, raw, bgFirst.entry().indicator) : raw;
            LzText rendered = LzText.wrapMc(base.mc().copy().append(LzText.format(" "
                + MultichatClient.CONFIG.collapseCounterFormat.replace("{count}", String.valueOf(mergedCount))).mc()));
            mergedEntry = bgFirst.newRender(rendered);
            tab.removeLast(); // the seam-adjacent live entry, folding into the background's matching one
            tab.addLast(mergedEntry);
            toAppend = result.entries().subList(1, result.entries().size());
            if (wasOnlyLiveEntry) { // the merged run is (still) the tab's own tracked/frontmost run
                cs.setRepeat(mergedCount);
                cs.setLastTick(bgFirst.entry().creationTick);
            } // else: something else is still the actual frontmost — collapseState already correctly tracks that, untouched
        } else if (!tab.hasMessages() && result.lastText() != null) {
            cs.setLastText(result.lastText());
            cs.setLastContent(result.lastContent());
            cs.setRepeat(result.lastRepeat());
            cs.setLastTick(result.lastTick());
        }
        for (ChatTab.MessageEntry me : toAppend) tab.addLast(me); // already newest-first; addLast preserves that order

        for (ChatPanel panel : getAllActivePanels()) {
            List<LzChatLine> lines = panelLines != null ? panelLines.get(panel) : null;
            if (lines != null && panelGenerations != null) {
                // A different background job (this coordinator's own or WrapCoordinator's) already
                // bumped this panel's line-cache generation since this one was submitted — it either
                // already landed newer content or is about to; either way this result is stale, so
                // drop it instead of duplicating/clobbering whatever's current (see applyWrapResult).
                Long expected = panelGenerations.get(panel);
                if (expected != null && panel.currentLineGeneration(tab) != expected) continue;
            }
            if (merge && lines != null) {
                // Touch exactly ONE entry, same cost as an ordinary live collapse-repeat — not a rebuild
                // of anything, not the whole tab, not a background job. `lines` was wrapped from the
                // un-merged result before the seam check ran, so its first entry (bgFirst) still shows
                // the OLD count — swap just that entry's lines for a fresh wrap of mergedEntry (same
                // underlying LzChatEntry, new text) before installing. removeTabEntry drops the
                // seam-adjacent live line still sitting in this panel's own cache (never part of `lines`
                // to begin with); applyWrapResult then keeps whatever real line is still in front of it
                // (e.g. an unrelated message that arrived after the seam entry) ahead of the patched
                // result, exactly like it always does — so the merged entry lands in the right position
                // whether or not anything interrupted the run.
                panel.removeTabEntry(tab, removedSeamEntry);
                int width = panel.resolveWidth();
                boolean primary = panel.primary();
                int origFirstLineCount = ChatPanel.buildTabLines(List.of(result.entries().get(0)), width, primary).size();
                List<LzChatLine> mergedFirstLines = ChatPanel.buildTabLines(List.of(mergedEntry), width, primary);
                List<LzChatLine> patched = new ArrayList<>(mergedFirstLines.size() + lines.size() - origFirstLineCount);
                patched.addAll(mergedFirstLines);
                patched.addAll(lines.subList(origFirstLineCount, lines.size()));
                panel.applyWrapResult(tab, patched);
                continue;
            }
            if (lines != null) panel.applyWrapResult(tab, lines);
            else panel.rebuildTabLines(tab);
        }
    }

    // ------------------------------------------------------------------
    // Incremental collapse-identical update for live search routing
    // ------------------------------------------------------------------

    public static void addSearchMessageWithCollapse(SearchTab st, int tick, LzText content, LzMessageIndicator indicator, LzText rawLzText, LzChatEntry entry) {
        final String text = rawLzText.mc().getString();

        ChatTab.MessageEntry existingEntry = null;
        for (ChatTab.MessageEntry me : st.messages()) {
            if (me.entry().originalContent != null &&
                    me.entry().originalContent.mc().getString().equals(text)) {
                existingEntry = me;
                break;
            }
        }

        int newCount;
        if (existingEntry != null) {
            final LzChatEntry existingLzEntry = existingEntry.entry();
            newCount = st.collapseCounts().getOrDefault(existingLzEntry, 1) + 1;
            st.messages().remove(existingEntry);
            for (ChatPanel p : getAllActivePanels()) {
                List<LzChatLine> lines = p.getTabLines(st);
                if (lines == null) continue;
                int removeStart = -1;
                for (int li = 0; li < lines.size(); li++) {
                    if (lines.get(li).entry() == existingLzEntry) { removeStart = li; break; }
                }
                int before = lines.size();
                lines.removeIf(line -> line.entry() == existingLzEntry);
                int removed = before - lines.size();
                if (removed > 0 && removeStart >= 0) {
                    int scrolled = p.getScrolledLines(st);
                    if (scrolled > 0 && removeStart < scrolled) {
                        int overlap = Math.min(removed, scrolled - removeStart);
                        p.setScrolledLines(st, Math.max(0, scrolled - overlap));
                    }
                }
            }
            st.collapseCounts().remove(existingLzEntry);
        } else {
            newCount = 1;
        }

        LzText base = st.showTimestamps() ? stampedText(tick, rawLzText, indicator) : rawLzText;
        LzText rendered = newCount > 1
                ? LzText.wrapMc(base.mc().copy().append(LzText.format(" " + MultichatClient.CONFIG.collapseCounterFormat.replace("{count}", String.valueOf(newCount))).mc()))
                : base;
        ChatTab.MessageEntry newMsg = new ChatTab.MessageEntry(rendered, content, entry);
        if (newCount > 1) st.collapseCounts().put(entry, newCount);
        addNewMessage(st, newMsg);
    }

    // ------------------------------------------------------------------
    // Reset (MC chat reload / rescale)
    // ------------------------------------------------------------------

    public static void resetForRefresh() {
        // Rebuild per-panel wrapped lines at new width; preserve message history and unread counts
        for (ChatPanel panel : getAllActivePanels()) {
            panel.resetScrollPositionsExcept(TabType.NOTES);
            for (ChatTab tab : tabs) panel.rebuildTabLines(tab);
        }
    }

    // ------------------------------------------------------------------
    // Timestamp utilities
    // ------------------------------------------------------------------

    public static LzText stampedText(int creationTick, LzText originalContent, @Nullable LzMessageIndicator indicator) {
        String ts = formatTimestamp(MultichatClient.CONFIG.timestampFormat, getWallTime(creationTick));
        return LzText.empty().append(LzText.format(ts + " ")).append(originalContent);
    }

    public static List<LzOrderedText> stampedLines(int creationTick, LzText originalContent,
                                                    @Nullable LzMessageIndicator indicator, int panelWidth) {
        int indicatorW = indicator != null && indicator.hasIcon() ? indicator.iconWidth() + 6 : 0;
        int wrapWidth = panelWidth - indicatorW;
        if (wallTimeStart < 0) return LzChat.breakLines(originalContent, wrapWidth);
        return LzChat.breakLines(stampedText(creationTick, originalContent, indicator), wrapWidth);
    }

    public static String getPlainTimestamp(int tick) {
        if (wallTimeStart < 0) return "";
        String ts = formatTimestamp(MultichatClient.CONFIG.timestampFormat, getWallTime(tick));
        return ts.replaceAll("&[0-9a-fklmnorA-FKLMNOR]", "");
    }

    private static LocalTime getWallTime(int addedTick) {
        long millis = wallTimeStart + (long)(addedTick - tickStart) * 50L;
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime();
    }

    static String formatTimestamp(String fmtWithCodes, LocalTime time) {
        StringBuilder pure = new StringBuilder();
        List<int[]> inserts = new ArrayList<>();
        for (int i = 0; i < fmtWithCodes.length(); i++) {
            if (fmtWithCodes.charAt(i) == '&' && i + 1 < fmtWithCodes.length()) {
                inserts.add(new int[]{pure.length(), fmtWithCodes.charAt(++i)});
            } else {
                pure.append(fmtWithCodes.charAt(i));
            }
        }

        StringBuilder pat = new StringBuilder();
        boolean quoted = false;
        for (char c : pure.toString().toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                if (quoted) { pat.append('\''); quoted = false; }
                pat.append(c);
            } else {
                if (!quoted) { pat.append('\''); quoted = true; }
                if (c == '\'') pat.append('\'');
                pat.append(c);
            }
        }
        if (quoted) pat.append('\'');

        String formatted;
        try {
            formatted = DateTimeFormatter.ofPattern(pat.toString()).format(time);
        } catch (Exception e) { return "??"; }

        StringBuilder result = new StringBuilder(formatted);
        int offset = 0;
        for (int[] ins : inserts) {
            int pos = Math.min(ins[0] + offset, result.length());
            result.insert(pos, "&" + (char) ins[1]);
            offset += 2;
        }
        return result.toString();
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public static boolean isTabActiveInAnyPanel(ChatTab tab) {
        for (ChatPanel panel : getAllActivePanels()) {
            if (panel.getActiveTab() == tab) return true;
        }
        return false;
    }

    public static boolean isTabInAnyEnabledPanel(ChatTab tab) {
        for (ChatPanel panel : getAllActivePanels()) {
            if (tab.visibleOnPanels().contains(panel.id())) return true;
        }
        return false;
    }

    public static List<ChatTab> getAllActivePanelsTabs() {
        final List<ChatTab> list = new ArrayList<>();
        for (ChatPanel panel : getAllActivePanels()) {
            if (panel.getActiveTab() != null) list.add(panel.getActiveTab());
        }
        return list;
    }

    public static List<ChatPanel> getAllActivePanels() {
        final List<ChatPanel> list = new ArrayList<>();
        for (ChatPanel panel : panels) {
            if (panel.isEnabled()) list.add(panel);
        }
        return list;
    }

    public static @Nullable ChatPanel primaryPanel() {
        return panels.isEmpty() ? null : panels.getFirst();
    }

    public static List<ChatPanel> getAllPanels() { return panels; }
    public static List<ChatTab>   getTabs()      { return tabs; }

    /** Invalidates any WrapCoordinator/FilterCoordinator job already in flight for {@code tab} (and its
     *  sub-tabs, if it's a multi-tab) — call this right before deleting a tab so a background result
     *  computed against it doesn't land afterward and write into an orphaned line cache (nothing else
     *  cancels those jobs; the tab's removal from the tab list alone doesn't touch them). */
    public static void invalidateTabJobs(ChatTab tab) {
        tab.bumpRefilterGeneration();
        for (ChatPanel panel : panels) panel.invalidateTabJobs(tab);
        if (tab.isMultiTab()) for (ChatTab sub : tab.subTabs()) invalidateTabJobs(sub);
    }

    /** The multi-tab that owns the given sub-tab, or null if it isn't anyone's sub (including if it's
     *  a regular top-level tab). Sub-tabs aren't in the global tabs list, so this is the only way to
     *  find "who owns this" — used for sibling-scoped name clashes and gate-aware refiltering. */
    public static @Nullable ChatTab findParentOf(ChatTab sub) {
        for (ChatTab t : tabs) {
            if (t.isMultiTab() && t.subTabs().contains(sub)) return t;
        }
        return null;
    }

    /** Removes a panel and cleans up every tab's reference to it. Shared by PanelListScreen's Del button and ContextMenu's "Close Popout". */
    public static void removePanel(ChatPanel panel) {
        int id = panel.id();
        for (ChatTab t : tabs) {
            t.visibleOnPanels().remove(id);
            if (t.isMultiTab()) for (ChatTab sub : t.subTabs()) sub.visibleOnPanels().remove(id);
        }
        panels.remove(panel);
        MultichatClient.saveTabsToConfig();
    }

    /**
     * Creates a blank panel — enabled, CUSTOM-anchored, centered — with a unique id and a name made
     * unique by appending " (2)", " (3)", etc. if it clashes with an existing panel. No tabs attached;
     * the caller assigns those afterward via tab.visibleOnPanels().add(id). Shared recipe behind both
     * PanelListScreen's Add button and the API's createPanel/createPopoutPanel.
     */
    public static ChatPanel createPanel(String name) {
        Set<Integer> ids = new HashSet<>();
        for (ChatPanel p : panels) ids.add(p.id());
        int id = 0;
        while (ids.contains(id)) id++;

        ChatPanel panel = new ChatPanel();
        panel.setId(id);
        Set<String> usedNames = new HashSet<>();
        for (ChatPanel p : panels) usedNames.add(p.name());
        String uniqueName = name;
        int suffix = 2;
        while (usedNames.contains(uniqueName)) uniqueName = name + " (" + suffix++ + ")";
        panel.setName(uniqueName);
        panel.setEnabled(true);
        panel.setAnchor(ChatPanel.Anchor.CUSTOM);
        panel.setCustomX(0.5f);
        panel.setCustomY(0.5f);

        panels.add(panel);
        MultichatClient.saveTabsToConfig();
        return panel;
    }

    /** Creates a new panel showing only the given tab, positioned centered — the "Pop Out" action. */
    public static ChatPanel createPopoutPanel(ChatTab tab) {
        ChatPanel panel = createPanel(tab.name() + " Popout");
        panel.setPopout(true);
        tab.visibleOnPanels().add(panel.id());
        panel.rebuildTabLines(tab);
        if (tab.isMultiTab()) for (ChatTab sub : tab.subTabs()) panel.rebuildTabLines(sub);
        MultichatClient.saveTabsToConfig();
        return panel;
    }

    /** Deletes a message (by its LzChatEntry identity) from every tab that has it, including multi-tab
     *  sub-tabs — the "Delete from All" context-menu action, and the API equivalent. Adjusts each
     *  affected panel's scroll so the visible view doesn't jump. */
    public static void deleteMessageEverywhere(LzChatEntry targetEntry) {
        if (targetEntry == null) return;
        List<ChatTab> allTabs = new ArrayList<>(tabs);
        for (ChatTab t : tabs) if (t.isMultiTab()) allTabs.addAll(t.subTabs());

        for (ChatTab tab : allTabs) {
            List<ChatTab.MessageEntry> messages = tab.messages();
            int idx = -1;
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).entry() == targetEntry) { idx = i; break; }
            }
            if (idx < 0) continue;

            // Count the deleted entry's own visual line-span per panel BEFORE removing it from the
            // per-panel line caches below, since that's the only place the count is still knowable.
            Map<ChatPanel, Integer> removedCounts = new HashMap<>();
            for (ChatPanel p : getAllPanels()) {
                if (p.getScrolledLines(tab) <= 0) continue;
                List<LzChatLine> l = p.getTabLines(tab);
                int count = 0;
                if (l != null) for (LzChatLine ln : l) if (ln.entry() == targetEntry) count++;
                removedCounts.put(p, count);
            }
            ChatTab.MessageEntry removedEntry = messages.get(idx);
            messages.remove(idx);
            // Surgically drop just this entry's lines from each panel's cache instead of going through
            // rebuildTabLines — a full rebuild would offload to a background rewrap and flash a loading
            // skeleton over the whole tab on large tabs, just to remove one message.
            for (ChatPanel p : getAllActivePanels()) p.removeTabEntry(tab, removedEntry);
            for (Map.Entry<ChatPanel, Integer> e : removedCounts.entrySet()) {
                ChatPanel p = e.getKey();
                int removed = e.getValue();
                int scrolled = p.getScrolledLines(tab);
                if (removed > 0 && scrolled > 0) p.setScrolledLines(tab, Math.max(0, scrolled - removed));
            }
            ChatSelection.clampOrClear(tab);
        }
    }

    public static UUID getNextTabId() { return UUID.randomUUID(); }

    public static int getIndicatorWidth(@Nullable LzMessageIndicator indicator) {
        return indicator != null && indicator.hasIcon() ? indicator.iconWidth() + 6 : 0;
    }
}
