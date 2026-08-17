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

        if (tab.messageCap() > 0) {
            IdentityHashMap<LzChatEntry, Boolean> toRemove = new IdentityHashMap<>();
            while (tab.messageCount() > tab.messageCap()) {
                toRemove.put(tab.messages().getLast().entry(), Boolean.TRUE);
                tab.removeLast();
            }
            if (!toRemove.isEmpty()) {
                for (ChatPanel panel : getAllActivePanels()) {
                    List<LzChatLine> lines = panel.getTabLines(tab);
                    if (lines == null) continue;
                    int before = lines.size();
                    lines.removeIf(line -> line.entry() != null && toRemove.containsKey(line.entry()));
                    int linesRemoved = before - lines.size();
                    int scrolled = panel.getScrolledLines(tab);
                    if (scrolled > 0) panel.setScrolledLines(tab, Math.max(0, scrolled - linesRemoved));
                }
                ChatSelection.clampOrClear(tab);
            }
        }

        if (!isTabActiveInAnyPanel(tab)) tab.incrementUnread();
        if (tab.hasMessages()) MessageAddedToTabCallback.EVENT.invoker().onMessageAdded(tab, tab.getFirstMessage());
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
                                   TabRuleSnapshot ownRules, @Nullable TabRuleSnapshot parentGateRules,
                                   List<TabRuleSnapshot> higherDiffRules) {
        static RefilterConfig snapshot(ChatTab tab, @Nullable ChatTab parentGate, List<ChatTab> higherDiffTabs) {
            return new RefilterConfig(MultichatClient.CONFIG.stripObjectContent, MultichatClient.CONFIG.normalizeFilterText,
                MultichatClient.CONFIG.collapseCounterFormat, tab.showTimestamps(), tab.messageCap(),
                tab.collapseRepeating(), tab.collapseMatchFormatting(),
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
    private record PanelWrapSpec(ChatPanel panel, int width, boolean primary) {
        static List<PanelWrapSpec> snapshot(List<ChatPanel> panels) {
            return panels.stream().map(p -> new PanelWrapSpec(p, p.resolveWidth(), p.primary())).toList();
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
        for (ChatPanel panel : getAllActivePanels()) panel.showLoadingSkeleton(tab);
        List<PanelWrapSpec> panelSpecs = PanelWrapSpec.snapshot(getAllActivePanels());
        FilterCoordinator.submit(tab, generation, () -> {
            SearchRefilterResult r = computeSearchRefilter(tab, snapshot, collapseIdentical, sourceRules, cfg);
            Map<ChatPanel, List<LzChatLine>> panelLines = new HashMap<>();
            for (PanelWrapSpec spec : panelSpecs)
                panelLines.put(spec.panel(), ChatPanel.buildTabLines(r.entries(), spec.width(), spec.primary()));
            return () -> applySearchRefilterResult(tab, generation, r, panelLines);
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

    /** Offloaded path only — {@code panelLines} already holds each panel's finished wrap, built in the
     *  same background job as the filter/collapse pass (see refilterSearch). Installs the filtered
     *  data model and every panel's finished lines in one shot via applyWrapResult (which also
     *  preserves whatever live routing prepended onto the panel's skeleton while the job ran — same
     *  as WrapCoordinator's own callback), instead of calling rebuildTabLines and triggering a second,
     *  separate background wrap. A panel with no entry in {@code panelLines} became active after the
     *  job was submitted — falls back to a normal (possibly offloaded) rebuild for just that panel. */
    private static void applySearchRefilterResult(SearchTab tab, long generation, SearchRefilterResult r, Map<ChatPanel, List<LzChatLine>> panelLines) {
        if (generation != tab.refilterGeneration()) return;
        tab.clearMessages();
        tab.clearCollapseCounts();
        for (ChatTab.MessageEntry me : r.entries()) tab.addLast(me); // already newest-first; addLast preserves that order
        tab.collapseCounts().putAll(r.collapseCounts());
        for (ChatPanel panel : getAllActivePanels()) {
            List<LzChatLine> lines = panelLines.get(panel);
            if (lines != null) panel.applyWrapResult(tab, lines);
            else panel.rebuildTabLines(tab);
        }
    }

    public static void refilterTab(ChatTab tab) {
        if (tab.type() != TabType.FILTERED) return;
        long generation = tab.bumpRefilterGeneration();
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
            applyFilterTabResult(tab, generation, List.of());
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
            applyFilterTabResult(tab, generation, computeFilterTab(snapshot, effectiveDiff, cfg));
            return;
        }

        // Same reasoning as refilterSearch's offloaded path — fold filtering AND every active panel's
        // line-wrap into one background job, so there's one skeleton and one main-thread callback
        // instead of a filter job bouncing to the main thread only to kick off a second, separate wrap.
        for (ChatPanel panel : getAllActivePanels()) panel.showLoadingSkeleton(tab);
        List<PanelWrapSpec> panelSpecs = PanelWrapSpec.snapshot(getAllActivePanels());
        FilterCoordinator.submit(tab, generation, () -> {
            List<ChatTab.MessageEntry> result = computeFilterTab(snapshot, effectiveDiff, cfg);
            Map<ChatPanel, List<LzChatLine>> panelLines = new HashMap<>();
            for (PanelWrapSpec spec : panelSpecs)
                panelLines.put(spec.panel(), ChatPanel.buildTabLines(result, spec.width(), spec.primary()));
            return () -> applyFilterTabResult(tab, generation, result, panelLines);
        });
    }

    /** Pure — reads only the pre-taken rule/config snapshot in {@code cfg}, never touches a live
     *  ChatTab. Safe to run on FilterCoordinator's background thread. Same collapse-run-length logic
     *  refilterTab always used (a repeat replaces the single in-progress entry with an updated
     *  counter), just tracked against a local deque instead of the tab's live one. */
    private static List<ChatTab.MessageEntry> computeFilterTab(List<ChatTab.MessageEntry> all,
                                                                 boolean effectiveDiff, RefilterConfig cfg) {
        ArrayDeque<ChatTab.MessageEntry> result = new ArrayDeque<>();
        String collapseLastText   = null;
        LzText collapseLastLzText = null;
        int    collapseRepeat     = 0;

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

            boolean include = effectiveDiff
                ? cfg.higherDiffRules().stream().noneMatch(r -> r.matches(filterText, indicator, raw))
                  && cfg.ownRules().matches(filterText, indicator, raw)
                : cfg.ownRules().matches(filterText, indicator, raw);
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
        return list;
    }

    /** Guarded by a generation check — a result from a scan that's since been superseded (filter
     *  rules edited again before this one finished) is dropped instead of overwriting newer content.
     *  Sync (non-offloaded) path only — see applySearchRefilterResult's 3-arg overload. */
    private static void applyFilterTabResult(ChatTab tab, long generation, List<ChatTab.MessageEntry> entries) {
        if (generation != tab.refilterGeneration()) return;
        tab.clearMessages();
        for (ChatTab.MessageEntry me : entries) tab.addLast(me); // already newest-first; addLast preserves that order
        for (ChatPanel panel : getAllActivePanels()) panel.rebuildTabLines(tab);
    }

    /** Offloaded path only — see applySearchRefilterResult's 4-arg overload, same shape here. */
    private static void applyFilterTabResult(ChatTab tab, long generation, List<ChatTab.MessageEntry> entries, Map<ChatPanel, List<LzChatLine>> panelLines) {
        if (generation != tab.refilterGeneration()) return;
        tab.clearMessages();
        for (ChatTab.MessageEntry me : entries) tab.addLast(me); // already newest-first; addLast preserves that order
        for (ChatPanel panel : getAllActivePanels()) {
            List<LzChatLine> lines = panelLines.get(panel);
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

            Map<ChatPanel, Integer> preSizes = new HashMap<>();
            for (ChatPanel p : getAllPanels()) {
                if (p.getScrolledLines(tab) > 0) {
                    List<LzChatLine> l = p.getTabLines(tab);
                    preSizes.put(p, l != null ? l.size() : 0);
                }
            }
            messages.remove(idx);
            for (ChatPanel p : getAllActivePanels()) p.rebuildTabLines(tab);
            for (ChatPanel p : getAllPanels()) {
                int scrolled = p.getScrolledLines(tab);
                if (scrolled <= 0 || !preSizes.containsKey(p)) continue;
                List<LzChatLine> l = p.getTabLines(tab);
                int removed = preSizes.get(p) - (l != null ? l.size() : 0);
                if (removed > 0) p.setScrolledLines(tab, Math.max(0, scrolled - removed));
            }
            ChatSelection.clampOrClear(tab);
        }
    }

    public static UUID getNextTabId() { return UUID.randomUUID(); }

    public static int getIndicatorWidth(@Nullable LzMessageIndicator indicator) {
        return indicator != null && indicator.hasIcon() ? indicator.iconWidth() + 6 : 0;
    }
}
