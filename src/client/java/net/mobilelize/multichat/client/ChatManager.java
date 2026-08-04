package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzMessageIndicator;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.api.MessageAddedToTabCallback;
import net.mobilelize.multichat.client.filter.FilterRule;
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

    public static void refilterSearch(SearchTab tab) {
        tab.clearMessages();
        tab.clearCollapseCounts();
        for (ChatPanel panel : getAllActivePanels()) {
            panel.clearTabLines(tab);
            panel.setScrolledLines(tab, 0);
        }

        ChatTab srcTab = tab.sourceTab() != null ? tab.sourceTab() : tabs.stream().filter(t -> t.type() == TabType.ALL).findFirst().orElse(null);
        if (srcTab == null || !srcTab.hasMessages()) return;

        List<ChatTab.MessageEntry> source = srcTab.messages();

        if (tab.collapseIdentical()) {
            LinkedHashMap<String, ChatTab.MessageEntry> newest = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
            for (ChatTab.MessageEntry me : source) {
                if (me.entry().originalContent == null) continue;
                String text = me.entry().originalContent.mc().getString();
                if (!tab.matchesText(text) || !tab.matchesTime(me.entry().creationTick)) continue;
                String fText1 = MultichatClient.CONFIG.normalizeFilterText ? FilterRule.normalizeFilterText(text) : text;
                if (tab.sourceTab() != null && !tab.sourceTab().filterRules().matches(fText1, me.entry().indicator)) continue;
                newest.putIfAbsent(text, me);
                counts.merge(text, 1, Integer::sum);
            }
            // Render newest→oldest with addLast: newest ends at index 0
            for (Map.Entry<String, ChatTab.MessageEntry> e : newest.entrySet()) {
                ChatTab.MessageEntry me = e.getValue();
                int count = counts.get(e.getKey());
                LzText base = tab.showTimestamps()
                    ? stampedText(me.entry().creationTick, me.entry().originalContent, me.entry().indicator)
                    : me.entry().originalContent;
                LzText rendered = count > 1
                    ? LzText.wrapMc(base.mc().copy().append(LzText.format(" " + MultichatClient.CONFIG.collapseCounterFormat.replace("{count}", String.valueOf(count))).mc()))
                    : base;
                if (count > 1) tab.collapseCounts().put(me.entry(), count);
                tab.addLast(me.newRender(rendered));
            }
        } else {
            for (int i = source.size() - 1; i >= 0; i--) {
                ChatTab.MessageEntry me = source.get(i);
                if (me.entry().originalContent == null) continue;
                String text = me.entry().originalContent.mc().getString();
                if (!tab.matchesText(text)) continue;
                String fText2 = MultichatClient.CONFIG.normalizeFilterText ? FilterRule.normalizeFilterText(text) : text;
                if (tab.sourceTab() != null && !tab.sourceTab().filterRules().matches(fText2, me.entry().indicator)) continue;
                if (!tab.matchesTime(me.entry().creationTick)) continue;
                LzText base = tab.showTimestamps()
                    ? stampedText(me.entry().creationTick, me.entry().originalContent, me.entry().indicator)
                    : me.entry().originalContent;
                tab.addFirst(me.newRender(base));
            }
        }

        if (tab.messageCap() > 0)
            while (tab.messageCount() > tab.messageCap()) tab.removeLast();

        for (ChatPanel panel : getAllActivePanels())
            panel.rebuildTabLines(tab);
    }

    public static void refilterTab(ChatTab tab) {
        if (tab.type() != TabType.FILTERED) return;
        // If this is a multi-tab's sub-tab, live routing only ever lets it see what the parent's own
        // filter already let through (MessageRouter.routeToSubTabs) — refilter needs the same gate,
        // otherwise it'd show messages the parent would have blocked live.
        ChatTab parentGate = findParentOf(tab);
        tab.clearMessages();
        for (ChatPanel panel : getAllActivePanels()) {
            panel.clearTabLines(tab);
            panel.setScrolledLines(tab, 0);
        }

        ChatTab allTab = tabs.stream().filter(t -> t.type() == TabType.ALL).findFirst().orElse(null);
        if (allTab == null || !allTab.hasMessages()) return;

        boolean effectiveDiff = tab.differenceMode() && !tab.differenceException();
        List<ChatTab> higherDiffTabs = effectiveDiff
            ? tabs.stream()
                .filter(t -> t != tab && t.type() == TabType.FILTERED
                          && t.differenceMode() && !t.differenceException()
                          && t.differenceLayerIndex() > tab.differenceLayerIndex())
                .toList()
            : List.of();

        String collapseLastText   = null;
        LzText collapseLastLzText = null;
        int    collapseRepeat     = 0;

        List<ChatTab.MessageEntry> all = allTab.messages();
        for (int i = all.size() - 1; i >= 0; i--) {
            ChatTab.MessageEntry src = all.get(i);
            LzText raw = src.entry().originalContent;
            if (raw == null) continue;
            String fullText   = raw.mc().getString();
            String filterText = MultichatClient.CONFIG.normalizeFilterText
                ? FilterRule.normalizeFilterText(fullText) : fullText;
            LzMessageIndicator indicator = src.entry().indicator;

            boolean include = effectiveDiff
                ? higherDiffTabs.stream().noneMatch(t -> t.filterRules().matches(filterText, indicator))
                  && tab.filterRules().matches(filterText, indicator)
                : tab.filterRules().matches(filterText, indicator);
            if (include && parentGate != null && !parentGate.filterRules().matches(filterText, indicator))
                include = false;
            if (!include) continue;

            boolean doCollapse = tab.collapseRepeating()
                && fullText.equals(collapseLastText)
                && (!tab.collapseMatchFormatting() || raw.mc().equals(collapseLastLzText.mc()));

            if (doCollapse) {
                collapseRepeat++;
                tab.removeFirst();
            } else {
                collapseRepeat     = 1;
                collapseLastText   = fullText;
                collapseLastLzText = raw;
            }

            LzText base = tab.showTimestamps()
                ? stampedText(src.entry().creationTick, raw, indicator)
                : raw;
            LzText rendered = collapseRepeat > 1
                ? LzText.wrapMc(base.mc().copy().append(LzText.format(" " + MultichatClient.CONFIG.collapseCounterFormat
                    .replace("{count}", String.valueOf(collapseRepeat))).mc()))
                : base;

            tab.addFirst(src.newRender(rendered));
        }

        if (tab.messageCap() > 0)
            while (tab.messageCount() > tab.messageCap()) tab.removeLast();

        for (ChatPanel panel : getAllActivePanels())
            panel.rebuildTabLines(tab);
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
     * Creates a new panel showing only the given tab, positioned centered — the "Pop Out" action.
     * Same creation recipe as PanelListScreen's Add button (enabled, CUSTOM anchor, centered),
     * just scoped to one tab instead of every tab, and flagged popout() for later cleanup.
     */
    public static ChatPanel createPopoutPanel(ChatTab tab) {
        Set<Integer> ids = new HashSet<>();
        for (ChatPanel p : panels) ids.add(p.id());
        int id = 0;
        while (ids.contains(id)) id++;

        ChatPanel panel = new ChatPanel();
        panel.setId(id);
        Set<String> usedNames = new HashSet<>();
        for (ChatPanel p : panels) usedNames.add(p.name());
        String baseName = tab.name() + " Popout";
        String uniqueName = baseName;
        int suffix = 2;
        while (usedNames.contains(uniqueName)) uniqueName = baseName + " (" + suffix++ + ")";
        panel.setName(uniqueName);
        panel.setEnabled(true);
        panel.setAnchor(ChatPanel.Anchor.CUSTOM);
        panel.setCustomX(0.5f);
        panel.setCustomY(0.5f);
        panel.setPopout(true);

        tab.visibleOnPanels().add(id);
        panels.add(panel);
        MultichatClient.saveTabsToConfig();
        return panel;
    }

    public static UUID getNextTabId() { return UUID.randomUUID(); }

    public static int getIndicatorWidth(@Nullable LzMessageIndicator indicator) {
        return indicator != null && indicator.hasIcon() ? indicator.iconWidth() + 6 : 0;
    }
}
