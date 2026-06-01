package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.multichat.client.filter.FilterRuleSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ChatTab {

    // ── Collapse state ─────────────────────────────────────────────────────────
    public static class CollapseState {
        private String              lastText    = null;
        private LzText              lastContent = null;
        private int                 repeat      = 0;
        private int                 lineCount   = 0;
        private int                 lastTick    = 0;
        private List<LzOrderedText> lastLines   = null;

        public String              lastText()                          { return lastText; }
        public void                setLastText(String v)              { lastText = v; }
        public LzText              lastContent()                      { return lastContent; }
        public void                setLastContent(LzText v)           { lastContent = v; }
        public int                 repeat()                           { return repeat; }
        public void                setRepeat(int v)                   { repeat = v; }
        public void                incrementRepeat()                  { repeat++; }
        public int                 lineCount()                        { return lineCount; }
        public void                setLineCount(int v)                { lineCount = v; }
        public int                 lastTick()                         { return lastTick; }
        public void                setLastTick(int v)                 { lastTick = v; }
        public List<LzOrderedText> lastLines()                        { return lastLines; }
        public void                setLastLines(List<LzOrderedText> v) { lastLines = v; }
        public boolean             hasLastLines()                     { return lastLines != null; }

        public void reset() {
            lastText    = null;
            lastContent = null;
            repeat      = 0;
            lineCount   = 0;
            lastLines   = null;
        }
    }

    // ── All fields private ─────────────────────────────────────────────────────
    private String             name;
    private final TabType      type;
    private String             apiId                   = null;
    private final Set<Integer> visibleOnPanels         = new LinkedHashSet<>();
    private final FilterRuleSet filterRules            = new FilterRuleSet();
    private boolean            differenceMode          = false;
    private int                differenceLayerIndex    = 0;
    private boolean            differenceException     = false;
    private boolean            showBadge               = true;
    private boolean            showTimestamps          = false;
    private int                tabColor                = 0;
    private int                msgBgColor              = 0;
    private boolean            collapseRepeating       = false;
    private boolean            collapseMatchFormatting = false;
    private int                messageCap              = 0;
    private String             contextPrefix           = "";
    private boolean            contextCommandMode      = true;
    private final CollapseState collapse               = new CollapseState();
    private final List<LzChatLine>               messages        = new ArrayList<>();
    private final Map<Integer, List<LzChatLine>> messagesByWidth = new HashMap<>();
    private int                unreadCount             = 0;

    public ChatTab(String name, TabType type, Collection<Integer> visibleOnPanels) {
        this.name = name;
        this.type = type;
        this.visibleOnPanels.addAll(visibleOnPanels);
    }

    // ── Identity ───────────────────────────────────────────────────────────────
    public String  name()             { return name; }
    public void    setName(String v)  { name = v != null ? v : ""; }
    public TabType type()             { return type; }
    public String  apiId()            { return apiId; }
    public void    setApiId(String v) { apiId = v; }

    public boolean isSystemTab() {
        return type == TabType.ALL || type == TabType.VANILLA;
    }

    // ── Visibility ─────────────────────────────────────────────────────────────
    public Set<Integer>  visibleOnPanels() { return visibleOnPanels; }

    // ── Filtering ──────────────────────────────────────────────────────────────
    public FilterRuleSet filterRules()     { return filterRules; }

    // ── Collapse tracking ──────────────────────────────────────────────────────
    public CollapseState collapse()        { return collapse; }

    // ── Display settings ───────────────────────────────────────────────────────
    public boolean differenceMode()                      { return differenceMode; }
    public void    setDifferenceMode(boolean v)          { differenceMode = v; }
    public int     differenceLayerIndex()                { return differenceLayerIndex; }
    public void    setDifferenceLayerIndex(int v)        { differenceLayerIndex = v; }
    public boolean differenceException()                 { return differenceException; }
    public void    setDifferenceException(boolean v)     { differenceException = v; }
    public boolean showBadge()                           { return showBadge; }
    public void    setShowBadge(boolean v)               { showBadge = v; }
    public boolean showTimestamps()                      { return showTimestamps; }
    public void    setShowTimestamps(boolean v)          { showTimestamps = v; }
    public int     tabColor()                            { return tabColor; }
    public void    setTabColor(int v)                    { tabColor = v & 0xFFFFFF; }
    public int     msgBgColor()                          { return msgBgColor; }
    public void    setMsgBgColor(int v)                  { msgBgColor = v; }
    public boolean collapseRepeating()                   { return collapseRepeating; }
    public void    setCollapseRepeating(boolean v)       { collapseRepeating = v; }
    public boolean collapseMatchFormatting()             { return collapseMatchFormatting; }
    public void    setCollapseMatchFormatting(boolean v) { collapseMatchFormatting = v; }
    public int     messageCap()                          { return messageCap; }
    public void    setMessageCap(int v)                  { messageCap = Math.max(0, v); }

    // ── Context ────────────────────────────────────────────────────────────────
    public String  contextPrefix()                        { return contextPrefix; }
    public void    setContextPrefix(String v)             { contextPrefix = v != null ? v : ""; }
    public boolean contextCommandMode()                   { return contextCommandMode; }
    public void    setContextCommandMode(boolean v)       { contextCommandMode = v; }

    // ── Message access ─────────────────────────────────────────────────────────

    /** Raw primary list. Primary panel renders from this directly. */
    public List<LzChatLine> messages()      { return messages; }

    public void       addFirst(LzChatLine line) { messages.addFirst(line); }
    public void       addLast(LzChatLine line)  { messages.addLast(line); }
    public void       removeFirst()             { messages.removeFirst(); }
    public void       removeLast()              { messages.removeLast(); }
    public LzChatLine getMessage(int i)         { return messages.get(i); }
    public LzChatLine getFirstMessage()         { return messages.getFirst(); }
    public int        messageCount()            { return messages.size(); }
    public boolean    hasMessages()             { return !messages.isEmpty(); }

    public void clearMessages() {
        messages.clear();
        collapse.reset();
        messagesByWidth.clear();
    }

    // ── Width cache (secondary panels render at their own width) ───────────────

    /** Returns or builds the wrapped line list for a secondary panel at {@code width}. */
    public List<LzChatLine> getOrBuildWidth(int width, Function<Integer, List<LzChatLine>> builder) {
        return messagesByWidth.computeIfAbsent(width, builder);
    }

    /** Invalidates all per-width caches so they rebuild from scratch on next render. */
    public void clearWidthCaches() {
        messagesByWidth.clear();
    }

    // ── Unread ─────────────────────────────────────────────────────────────────
    public int     unreadCount()     { return unreadCount; }
    public boolean hasUnread()       { return unreadCount > 0; }
    public void    incrementUnread() { unreadCount++; }
    public void    clearUnread()     { unreadCount = 0; }
}
