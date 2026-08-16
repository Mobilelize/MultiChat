package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.filter.FilterRuleSet;
import net.mobilelize.multichat.client.filter.StyleFilterSet;

import java.util.*;

public class ChatTab {

    // ── Collapse state (runtime only) ──────────────────────────────────────────
    public static class CollapseState {
        private String lastText    = null;
        private LzText lastContent = null;
        private int    repeat      = 0;
        private int    lastTick    = 0;

        public String lastText()               { return lastText; }
        public void   setLastText(String v)    { lastText = v; }
        public LzText lastContent()            { return lastContent; }
        public void   setLastContent(LzText v) { lastContent = v; }
        public int    repeat()                 { return repeat; }
        public void   setRepeat(int v)         { repeat = v; }
        public void   incrementRepeat()        { repeat++; }
        public int    lastTick()               { return lastTick; }
        public void   setLastTick(int v)       { lastTick = v; }

        public void reset() {
            lastText    = null;
            lastContent = null;
            repeat      = 0;
        }
    }

    public record MessageEntry(LzText rendered, LzText content, LzChatEntry entry) {
        public MessageEntry newRender(LzText rendered) {
            return new MessageEntry(rendered, content, entry);
        }
    }

    // ── Config fields (serialized) ─────────────────────────────────────────────
    private String           name;
    private final TabType    type;
    private final UUID       tabId                   = ChatManager.getNextTabId();
    private String           apiId                   = null;
    private boolean          playerDeletable         = false;
    private final Set<Integer> visibleOnPanels       = new LinkedHashSet<>();
    private final FilterRuleSet filterRules          = new FilterRuleSet();
    private boolean          forceHighlightMatches  = false;
    private boolean          differenceMode          = false;
    private int              differenceLayerIndex    = 0;
    private boolean          differenceException     = false;
    private boolean          differenceInclusion     = false;
    private boolean          showBadge               = true;
    private boolean          showTimestamps          = false;
    private int              tabColor                = 0;
    private int              msgBgColor              = 0;
    private boolean          collapseRepeating       = false;
    private boolean          collapseMatchFormatting = false;
    private int              messageCap              = 0;
    private String           contextPrefix           = "";
    private boolean          contextCommandMode      = true;
    private boolean          pingEnabled             = false;
    private String           pingSound               = "";
    private float            pingVolume              = -1f;
    private float            pingPitch               = -1f;
    private boolean          pingUseGlobalConditions = true;
    private boolean          pingCondAlways          = false;
    private boolean          pingCondActiveTab       = false;
    private boolean          pingCondUnfocusedTab    = false;
    private boolean          pingCondGameUnfocused   = false;
    private boolean          pingCondAfk             = false;
    private boolean          pingCondRequireSilence  = false;
    private int              pingCooldownSec         = -1;
    private int              pingSilenceThresholdSec = -1;
    private int              pingAfkThresholdSec     = -1;
    private boolean       isMultiTab = false;
    private List<ChatTab> subTabs;    // lazy-init below, NOT final — Gson uses Unsafe allocation for ChatTab
                                       // (no no-arg constructor), which skips field initializers, so any tab
                                       // saved before this field existed deserializes with subTabs == null.
    private StyleFilterSet styleRules; // same reasoning — lazy-init, not final

    // ── Runtime fields (not serialized) — lazy-init because Gson uses Unsafe ──
    private transient List<MessageEntry> messages;
    private transient CollapseState      collapse;
    private transient int                unreadCount = 0;
    private transient TabMode            contentMode = TabMode.ROUTED_MESSAGES;
    private transient List<LzText>       customRawLines;
    // Bumped on every refilterTab/refilterSearch call for this tab; carried on the background
    // FilterCoordinator job so a result from a refilter that's since been superseded (query/filter
    // changed again before the old scan finished) is recognized as stale and dropped. Read from the
    // background worker (to skip a stale job before wasting compute on it) as well as the main
    // thread, so it's a plain volatile long rather than needing a wrapper map like ChatPanel's
    // per-tab generations — there's only ever one of these per tab, not one per panel.
    private transient volatile long      refilterGeneration = 0;

    public ChatTab(String name, TabType type, Collection<Integer> visibleOnPanels) {
        this.name = name;
        this.type = type;
        this.visibleOnPanels.addAll(visibleOnPanels);
    }

    // ── Identity ───────────────────────────────────────────────────────────────
    public String  name()             { return name; }
    public void    setName(String v)  { name = v != null ? v : ""; }
    public TabType type()             { return type; }
    public UUID    id()               { return tabId; }
    public String  apiId()                     { return apiId; }
    public void    setApiId(String v)          { apiId = v; }
    public boolean playerDeletable()           { return playerDeletable; }
    public void    setPlayerDeletable(boolean v) { playerDeletable = v; }

    public boolean isSystemTab() {
        return type == TabType.ALL || type == TabType.VANILLA || type == TabType.INFO;
    }

    // ── Visibility ─────────────────────────────────────────────────────────────
    public Set<Integer> visibleOnPanels() { return visibleOnPanels; }

    // ── Filtering ──────────────────────────────────────────────────────────────
    public FilterRuleSet filterRules() { return filterRules; }

    /** When true, messages that matched a Highlight Words keyword reach this tab even if the filter rules above reject them. */
    public boolean forceHighlightMatches()               { return forceHighlightMatches; }
    public void    setForceHighlightMatches(boolean v)   { forceHighlightMatches = v; }

    /** Style-aware conditions, separate from filterRules — see StyleFilterSet. Empty = no restriction. */
    public StyleFilterSet styleRules() {
        if (styleRules == null) styleRules = new StyleFilterSet();
        return styleRules;
    }

    // ── Multi-tab (nested sub-tabs) ───────────────────────────────────────────
    // Sub-tabs are real ChatTab objects, never added to ChatManager's global tab list — they're only
    // reachable via parent.subTabs(), so they never show up in normal tab-bar cycling/arrow-key logic,
    // and routing (MessageRouter) fans out to them manually instead of the normal top-level loop.
    public boolean isMultiTab() { return isMultiTab; }
    public void setMultiTab(boolean v) {
        isMultiTab = v;
        if (v && subTabs().isEmpty()) {
            ChatTab all = new ChatTab("All", TabType.FILTERED, Collections.emptyList());
            all.setPlayerDeletable(false); // mirrors the top-level All tab being a protected system tab
            subTabs().add(all);
        }
    }
    public List<ChatTab> subTabs() {
        if (subTabs == null) subTabs = new ArrayList<>();
        return subTabs;
    }

    // ── Collapse tracking ──────────────────────────────────────────────────────
    public CollapseState collapseState() {
        if (collapse == null) collapse = new CollapseState();
        return collapse;
    }

    // ── Refilter generation (see FilterCoordinator) ────────────────────────────
    public long bumpRefilterGeneration() { return ++refilterGeneration; }
    public long refilterGeneration()     { return refilterGeneration; }

    // ── Display settings ───────────────────────────────────────────────────────
    public boolean differenceMode()                      { return differenceMode; }
    public void    setDifferenceMode(boolean v)          { differenceMode = v; }
    public int     differenceLayerIndex()                { return differenceLayerIndex; }
    public void    setDifferenceLayerIndex(int v)        { differenceLayerIndex = v; }
    public boolean differenceException()                 { return differenceException; }
    public void    setDifferenceException(boolean v)     { differenceException = v; }
    public boolean differenceInclusion()                 { return differenceInclusion; }
    public void    setDifferenceInclusion(boolean v)     { differenceInclusion = v; }
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
    public String  contextPrefix()                   { return contextPrefix; }
    public void    setContextPrefix(String v)        { contextPrefix = v != null ? v : ""; }
    public boolean contextCommandMode()              { return contextCommandMode; }
    public void    setContextCommandMode(boolean v)  { contextCommandMode = v; }

    // ── Messages ───────────────────────────────────────────────────────────────
    private List<MessageEntry> msgs() {
        if (messages == null) messages = new ArrayList<>();
        return messages;
    }

    public List<MessageEntry> messages()          { return msgs(); }
    public void          addFirst(MessageEntry e) { msgs().addFirst(e); }
    public void          addLast(MessageEntry e)  { msgs().addLast(e); }
    public void          removeFirst()            { msgs().removeFirst(); }
    public void          removeLast()             { msgs().removeLast(); }
    public MessageEntry  getFirstMessage()        { return msgs().getFirst(); }
    public int           messageCount()           { return msgs().size(); }
    public boolean       hasMessages()            { return !msgs().isEmpty(); }

    public void clearMessages() {
        msgs().clear();
        collapseState().reset();
    }

    // ── Unread ─────────────────────────────────────────────────────────────────
    public int     unreadCount()     { return unreadCount; }
    public boolean hasUnread()       { return unreadCount > 0; }
    public void    incrementUnread() { unreadCount++; }
    public void    clearUnread()     { unreadCount = 0; }

    // ── Custom content mode ────────────────────────────────────────────────────
    public TabMode contentMode()              { return contentMode != null ? contentMode : TabMode.ROUTED_MESSAGES; }
    public void    setContentMode(TabMode v)  { contentMode = v != null ? v : TabMode.ROUTED_MESSAGES; }

    public List<LzText> customRawLines() {
        if (customRawLines == null) customRawLines = new ArrayList<>();
        return customRawLines;
    }
    public void setCustomRawLines(List<LzText> v) { customRawLines = v != null ? new ArrayList<>(v) : new ArrayList<>(); }

    // ── Pings ──────────────────────────────────────────────────────────────────
    public boolean pingEnabled()                         { return pingEnabled; }
    public void    setPingEnabled(boolean v)             { pingEnabled = v; }
    public String  pingSound()                           { return pingSound; }
    public void    setPingSound(String v)                { pingSound = v != null ? v : ""; }
    public float   pingVolume()                          { return pingVolume; }
    public void    setPingVolume(float v)                { pingVolume = v; }
    public float   pingPitch()                           { return pingPitch; }
    public void    setPingPitch(float v)                 { pingPitch = v; }
    public boolean pingUseGlobalConditions()             { return pingUseGlobalConditions; }
    public void    setPingUseGlobalConditions(boolean v) { pingUseGlobalConditions = v; }
    public boolean pingCondAlways()                      { return pingCondAlways; }
    public void    setPingCondAlways(boolean v)          { pingCondAlways = v; }
    public boolean pingCondActiveTab()                   { return pingCondActiveTab; }
    public void    setPingCondActiveTab(boolean v)       { pingCondActiveTab = v; }
    public boolean pingCondUnfocusedTab()                { return pingCondUnfocusedTab; }
    public void    setPingCondUnfocusedTab(boolean v)    { pingCondUnfocusedTab = v; }
    public boolean pingCondGameUnfocused()               { return pingCondGameUnfocused; }
    public void    setPingCondGameUnfocused(boolean v)   { pingCondGameUnfocused = v; }
    public boolean pingCondAfk()                         { return pingCondAfk; }
    public void    setPingCondAfk(boolean v)             { pingCondAfk = v; }
    public boolean pingCondRequireSilence()              { return pingCondRequireSilence; }
    public void    setPingCondRequireSilence(boolean v)  { pingCondRequireSilence = v; }
    public int     pingCooldownSec()                     { return pingCooldownSec; }
    public void    setPingCooldownSec(int v)             { pingCooldownSec = v; }
    public int     pingSilenceThresholdSec()             { return pingSilenceThresholdSec; }
    public void    setPingSilenceThresholdSec(int v)     { pingSilenceThresholdSec = v; }
    public int     pingAfkThresholdSec()                 { return pingAfkThresholdSec; }
    public void    setPingAfkThresholdSec(int v)         { pingAfkThresholdSec = v; }
}
