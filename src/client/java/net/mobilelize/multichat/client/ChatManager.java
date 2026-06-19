package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzMessageIndicator;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.api.MessageAddedToTabCallback;
import net.mobilelize.multichat.client.ping.PingPlayer;
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
            for (int i = source.size() - 1; i >= 0; i--) {
                ChatTab.MessageEntry me = source.get(i);
                if (me.entry().originalContent == null) continue;
                String text = me.entry().originalContent.mc().getString();
                if (!tab.matchesText(text) || !tab.matchesTime(me.entry().creationTick)) continue;
                newest.put(text, me);
                counts.merge(text, 1, Integer::sum);
            }
            List<Map.Entry<String, ChatTab.MessageEntry>> entries = new ArrayList<>(newest.entrySet());
            for (int i = entries.size() - 1; i >= 0; i--) {
                ChatTab.MessageEntry me = entries.get(i).getValue();
                int count = counts.get(entries.get(i).getKey());
                LzText base = tab.showTimestamps()
                    ? stampedText(me.entry().creationTick, me.entry().originalContent, me.entry().indicator)
                    : me.entry().originalContent;
                LzText rendered = count > 1
                    ? LzText.wrapMc(base.mc().copy().append(LzText.format(" " + MultichatClient.CONFIG.collapseCounterFormat.replace("{count}", String.valueOf(count))).mc()))
                    : base;
                if (count > 1) tab.collapseCounts().put(me.entry(), count);
                tab.addFirst(me.newRender(rendered));
            }
        } else {
            for (int i = source.size() - 1; i >= 0; i--) {
                ChatTab.MessageEntry me = source.get(i);
                if (me.entry().originalContent == null) continue;
                String text = me.entry().originalContent.mc().getString();
                if (!tab.matchesText(text)) continue;
                if (tab.sourceTab() != null && !tab.sourceTab().filterRules().matches(text, me.entry().indicator)) continue;
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
            String fullText = raw.mc().getString();
            LzMessageIndicator indicator = src.entry().indicator;

            boolean include = effectiveDiff
                ? higherDiffTabs.stream().noneMatch(t -> t.filterRules().matches(fullText, indicator))
                  && tab.filterRules().matches(fullText, indicator)
                : tab.filterRules().matches(fullText, indicator);
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

    public static UUID getNextTabId() { return UUID.randomUUID(); }

    public static int getIndicatorWidth(@Nullable LzMessageIndicator indicator) {
        return indicator != null && indicator.hasIcon() ? indicator.iconWidth() + 6 : 0;
    }
}
