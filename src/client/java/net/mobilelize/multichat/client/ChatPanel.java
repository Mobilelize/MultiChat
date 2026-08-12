package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.*;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.integration.chatheads.ChatHeadsSupport;

import java.util.*;

public class ChatPanel {

    public enum Anchor { LEFT, RIGHT, CUSTOM }

    private Anchor  anchor             = Anchor.LEFT;
    private boolean primary            = false;
    private int     id                 = 0;
    private String  name               = "";
    private boolean popout             = false; // created via "Pop Out" on a tab — tags it for the "Close Popout" cleanup action

    private transient Map<UUID, List<LzChatLine>> tabMessages;
    private transient Map<UUID, List<LzChatLine>> customTabLines;

    private int     margin             = 0;
    private int activeTabId = 0;
    // Mirrors activeTabId whenever it's a genuine top-level index (i.e. not drilled into a multi-tab's
    // subs). expandedParent is transient and always null right after a restart, but activeTabId is
    // persisted — without this, restarting while drilled in would leave activeTabId pointing at a raw
    // sub-list position that means something else entirely once read back as a top-level index.
    private int topLevelActiveTabId = 0;
    private transient ChatTab activeTab = null;
    private boolean enabled            = false;
    private boolean showOnlyWhenFocused = false;
    private boolean alwaysFocused      = false;
    private boolean showBadges         = true;
    private boolean showTabs           = true;
    private boolean tabsAtTop          = false;
    private transient int     tabScrollOffset       = 0;
    private transient int     tabLastRendered       = -1;
    private transient boolean pendingScrollIntoView = false;
    private transient int     tabFlashTick          = -1000;

    private float  customX                    = 0f;
    private float  customY                    = 0f;
    private int    customWidth                = -1;
    private int    customHeightLines          = -1;
    private int    customUnfocusedHeightLines = -1;
    private boolean lockUnfocusedToFocused   = true;
    private float  customScale                = -1f;
    private int    bgColor            = 0;
    private double customBgOpacity    = -1;
    private double customChatOpacity  = -1;
    private double tabBgOpacity       = -1;
    private double tabFgOpacity       = -1;

    private transient ChatTab panelContextTab = null;
    private UUID    panelContextTabId  = null;
    private boolean useActiveTabContext = false;

    private transient Map<ChatTab, Integer> scrollPositions;
    private transient ChatTab               expandedParent; // multi-tab currently drilled into on this panel, or null

    public ChatPanel() {}

    // ── Identity ───────────────────────────────────────────────────────────────
    public Anchor  anchor()             { return anchor; }
    public void    setAnchor(Anchor v)  { anchor = v; }
    public boolean primary()            { return primary; }
    public void    setPrimary(boolean v) { primary = v; }
    public int     id()                 { return id; }
    public void    setId(int v)         { id = v; }
    public String  name()               { return name; }
    public void    setName(String v)    { name = v != null ? v : ""; }
    public boolean popout()             { return popout; }
    public void    setPopout(boolean v) { popout = v; }
    public int     margin()             { return margin; }
    public void    setMargin(int v)     { margin = v; }

    private Map<UUID, List<LzChatLine>> tabMessages() {
        if (tabMessages == null) tabMessages = new HashMap<>();
        return tabMessages;
    }

    private Map<UUID, List<LzChatLine>> customTabLines() {
        if (customTabLines == null) customTabLines = new HashMap<>();
        return customTabLines;
    }

    private Map<ChatTab, Integer> scrollPositions() {
        if (scrollPositions == null) scrollPositions = new HashMap<>();
        return scrollPositions;
    }

    /** Drills into a multi-tab: getVisibleTabs() substitutes its sub-tabs for the normal top-level list,
     *  so the existing tab-bar rendering/click/arrow-cycling code works on sub-tabs with zero new code. */
    public void expandMultiTab(ChatTab parent) {
        // Capture the parent's own top-level position before swapping getVisibleTabs() over to its subs.
        int idx = getVisibleTabs().indexOf(parent);
        if (idx >= 0) topLevelActiveTabId = idx;
        expandedParent = parent;
        setActiveTabId(0); // lands on the parent's first sub-tab ("All")
    }

    /** Backs out of the currently-expanded multi-tab, landing back on the parent itself. */
    public void collapseMultiTab() {
        ChatTab parent = expandedParent;
        if (parent == null) return;
        expandedParent = null;
        int idx = getVisibleTabs().indexOf(parent);
        setActiveTabId(Math.max(0, idx));
    }

    public boolean isExpanded() { return expandedParent != null; }

    /** Called once after loading from disk: expandedParent is transient and always null at this point,
     *  but activeTabId may still hold a raw sub-list index left over from before the client closed —
     *  meaningless (or pointing at the wrong tab entirely) once read back as a top-level index. Reset to
     *  the last known top-level tab (the parent, if the panel was drilled in) instead. */
    public void normalizeActiveTabAfterLoad() {
        activeTabId = topLevelActiveTabId;
        activeTab   = null;
    }

    private void wrapEntryInto(List<LzChatLine> panelLines, ChatTab.MessageEntry entry) {
        // chat_heads' BEFORE_LINE head (if any) does NOT reserve space at wrap time in real
        // chat_heads — it wraps at full width same as always, then shifts the first line's text
        // right by headWidth at render time only, accepting that it may extend slightly past the
        // normal edge (their own fixTextOverflow mixin widens the background-fill rect to match,
        // rather than avoiding the overflow). The primary panel matches that exactly — its render
        // bound grows instead (resolveRenderWidth()) so the overflow stays visible and selectable.
        // Every other panel has nowhere for that overflow to go (fixed edge, other UI can sit past
        // it), so unlike real chat_heads it DOES reserve the width here, trading "matches vanilla
        // exactly" for "never renders outside its own bounds" on panels that aren't the main chat.
        int width = resolveWidth() - ChatManager.getIndicatorWidth(LzMessageIndicator.wrapNullable(entry.entry().indicator != null ? entry.entry().indicator.mc() : null));
        if (!primary) {
            // Reserve only what this entry will actually use — headWidth() for a real head on
            // line 0, emptyLineWidth() for the "offset non-player text" alignment case on every
            // other line, both correctly 0 when neither applies (e.g. no head found AND that
            // alignment setting is off). Blindly reserving maxHeadWidth() for every entry that
            // merely participated in chat_heads' pipeline wrapped messages a line early even when
            // nothing was ever going to render an offset for them.
            ChatHeadsSupport.HeadRef ref = ChatHeadsSupport.get(entry.entry());
            if (ref != ChatHeadsSupport.HeadRef.NONE) {
                width -= Math.max(ChatHeadsSupport.headWidth(ref), ChatHeadsSupport.emptyLineWidth());
            }
        }
        ChatHeadsSupport.beginHeadRendering();
        List<LzOrderedText> lines;
        try {
            lines = LzChat.breakLines(LzText.wrapMc(entry.rendered().mc()), width);
        } finally {
            ChatHeadsSupport.endHeadRendering();
        }
        for (int i = 0; i < lines.size(); i++) {
            boolean endOfEntry   = i == lines.size() - 1;
            boolean startOfEntry = i == 0;
            panelLines.addFirst(LzChatLine.create(entry.entry().creationTick, lines.get(i), entry.entry().indicator, endOfEntry, entry.entry(), startOfEntry));
        }
    }

    public void addTabMessage(ChatTab tab, ChatTab.MessageEntry entry) {
        wrapEntryInto(tabMessages().computeIfAbsent(tab.id(), k -> new ArrayList<>()), entry);
    }

    public void removeTabEntry(ChatTab tab, ChatTab.MessageEntry entry) {
        List<LzChatLine> panelLines = tabMessages().get(tab.id());
        if (panelLines != null) panelLines.removeIf(line -> line.entry() == entry.entry());
    }

    public void clearTabLines(ChatTab tab) {
        tabMessages().remove(tab.id());
    }

    public void rebuildTabLines(ChatTab tab) {
        List<LzChatLine> panelLines = new ArrayList<>();
        tabMessages().put(tab.id(), panelLines);
        List<ChatTab.MessageEntry> entries = tab.messages();
        for (int i = entries.size() - 1; i >= 0; i--) wrapEntryInto(panelLines, entries.get(i));

        List<LzText> raw = tab.customRawLines();
        if (!raw.isEmpty()) {
            List<LzChatLine> customLines = new ArrayList<>();
            customTabLines().put(tab.id(), customLines);
            for (int i = raw.size() - 1; i >= 0; i--) {
                LzText text = raw.get(i);
                LzChatEntry synth = new LzChatEntry(0, text, null);
                wrapEntryInto(customLines, new ChatTab.MessageEntry(text, text, synth));
            }
        }
    }

    public void setCustomTabLines(ChatTab tab, List<LzText> rawLines, boolean resetScroll) {
        List<LzChatLine> customLines = new ArrayList<>();
        customTabLines().put(tab.id(), customLines);
        for (int i = rawLines.size() - 1; i >= 0; i--) {
            LzText text = rawLines.get(i);
            LzChatEntry synth = new LzChatEntry(0, text, null);
            wrapEntryInto(customLines, new ChatTab.MessageEntry(text, text, synth));
        }
        if (resetScroll) setScrolledLines(tab, 0);
    }

    public void rebuildAllTabLines() {
        for (ChatTab tab : ChatManager.getTabs()) {
            rebuildTabLines(tab);
            if (tab.isMultiTab()) for (ChatTab sub : tab.subTabs()) rebuildTabLines(sub);
        }
    }


    // ── State ──────────────────────────────────────────────────────────────────
    public boolean isEnabled()                    { return enabled; }
    public void    setEnabled(boolean v)        { enabled = v; }


    public boolean showOnlyWhenFocused()        { return showOnlyWhenFocused; }
    public void    setShowOnlyWhenFocused(boolean v) { showOnlyWhenFocused = v; }
    public boolean alwaysFocused()              { return alwaysFocused; }
    public void    setAlwaysFocused(boolean v)  { alwaysFocused = v; }
    public boolean showBadges()                 { return showBadges; }
    public void    setShowBadges(boolean v)     { showBadges = v; }
    public boolean showTabs()                   { return showTabs; }
    public void    setShowTabs(boolean v)       { showTabs = v; }
    public boolean tabsAtTop()                  { return tabsAtTop; }
    public void    setTabsAtTop(boolean v)      { tabsAtTop = v; }

    // ── Render state ───────────────────────────────────────────────────────────
    public int  tabScrollOffset()           { return tabScrollOffset; }
    public void setTabScrollOffset(int v)   { tabScrollOffset = v; }
    public int  tabLastRendered()           { return tabLastRendered; }
    public void setTabLastRendered(int v)   { tabLastRendered = v; }
    public int  tabFlashTick()              { return tabFlashTick; }

    public void flash(int currentTick) { tabFlashTick = currentTick; }

    // ── Custom layout ──────────────────────────────────────────────────────────
    public float  customX()                   { return customX; }
    public void   setCustomX(float v)         { customX = v; }
    public float  customY()                   { return customY; }
    public void   setCustomY(float v)         { customY = v; }
    public int    customWidth()               { return customWidth; }
    public void   setCustomWidth(int v)       { customWidth = v; rebuildAllTabLines(); }
    public int    customHeightLines()                      { return customHeightLines; }
    public void   setCustomHeightLines(int v)              { customHeightLines = v; }
    public int    customUnfocusedHeightLines()             { return customUnfocusedHeightLines; }
    public void   setCustomUnfocusedHeightLines(int v)     { customUnfocusedHeightLines = v; }
    public boolean lockUnfocusedToFocused()                { return lockUnfocusedToFocused; }
    public void    setLockUnfocusedToFocused(boolean v)    { lockUnfocusedToFocused = v; }
    public float  customScale()               { return customScale; }
    public void   setCustomScale(float v)     { customScale = v; }
    public int    bgColor()                   { return bgColor; }
    public void   setBgColor(int v)           { bgColor = v & 0xFFFFFF; }
    public double customBgOpacity()               { return customBgOpacity; }
    public void   setCustomBgOpacity(double v)    { customBgOpacity = v; }
    public double customChatOpacity()             { return customChatOpacity; }
    public void   setCustomChatOpacity(double v)  { customChatOpacity = v; }
    public double tabBgOpacity()                  { return tabBgOpacity; }
    public void   setTabBgOpacity(double v)       { tabBgOpacity = v; }
    public double tabFgOpacity()                  { return tabFgOpacity; }
    public void   setTabFgOpacity(double v)       { tabFgOpacity = v; }

    // ── Context ────────────────────────────────────────────────────────────────
    public ChatTab panelContextTab()               { return panelContextTab; }
    public void    setPanelContextTab(ChatTab v)   { panelContextTab = v; panelContextTabId = v != null ? v.id() : null; }
    public UUID    panelContextTabId()             { return panelContextTabId; }
    public boolean useActiveTabContext()            { return useActiveTabContext; }
    public void    setUseActiveTabContext(boolean v) { useActiveTabContext = v; }

    public ChatTab resolveContextTab() {
        if (useActiveTabContext) return getActiveTab();
        return panelContextTab;
    }

    // ── Visibility ─────────────────────────────────────────────────────────────
    public boolean isVisibleInChat(boolean chatFocused) {
        return enabled && (!showOnlyWhenFocused || chatFocused);
    }

    // ── Resolvers ──────────────────────────────────────────────────────────────

    public float resolveScale() {
        if (primary || customScale <= 0) return (float) LzChat.chatScale();
        return customScale;
    }

    public int resolveWidth() {
        float scale = resolveScale();
        if (primary || customWidth <= 0) return LzMath.ceil((float) LzChat.chatWidth() / scale);
        return customWidth;
    }

    /** Width to use for rendering bounds and hit-testing (background fill, click/drag/hover
     *  region) — {@link #resolveWidth()} plus the primary panel's chat_heads BEFORE_LINE
     *  allowance, if any (0 for every other panel, and 0 there too unless chat_heads is actually
     *  in that mode). {@code resolveWidth()} itself stays the configured/logical width, used for
     *  panel positioning math ({@code computeXOffset}'s RIGHT/CUSTOM anchors) and the layout
     *  editor — neither should react to this. See ChatHeadsSupport's BEFORE_LINE section for why:
     *  chat_heads shifts a headed line's text right at render time without shrinking the wrap, so
     *  it can extend past the nominal edge — only the primary panel grows to keep that overflow
     *  selectable and visible; other panels reserve the same width at wrap time instead
     *  (ChatPanel.wrapEntryInto) so nothing overflows past their fixed edge at all. */
    public int resolveRenderWidth() {
        return resolveWidth() + (primary ? ChatHeadsSupport.maxHeadWidth() : 0);
    }

    // Focused line count from MC options — not isChatFocused()-dependent.
    public int resolveFocusedLines() {
        if (primary || customHeightLines <= 0) {
            double h = LzClient.mc().options.getChatHeightFocused().getValue();
            int heightPx = (int)(h * 160.0 + 20.0);
            return Math.max(1, heightPx / resolveLineHeight());
        }
        return customHeightLines;
    }

    public int resolveLines() {
        return resolveLines(true);
    }

    public int resolveLines(boolean focused) {
        if (focused || lockUnfocusedToFocused) return resolveFocusedLines();
        if (customUnfocusedHeightLines > 0) return customUnfocusedHeightLines;
        return LzChat.visibleLineCount(); // unfocused fallback: MC returns unfocused count here
    }

    public int resolveLineHeight() {
        if (primary || customScale <= 0) return LzChat.chatLineHeight();
        return Math.round(LzChat.chatLineHeight() * customScale / (float) LzChat.chatScale());
    }

    public double resolveBgOpacity() {
        if (primary || customBgOpacity < 0) return LzOptions.textBackgroundOpacity();
        return customBgOpacity;
    }

    public double resolveChatOpacity() {
        if (primary || customChatOpacity < 0) return LzOptions.chatOpacity() * 0.9 + 0.1;
        return customChatOpacity;
    }

    // ── Tab helpers ────────────────────────────────────────────────────────────

    public List<ChatTab> getVisibleTabs() {
        if (expandedParent != null) return expandedParent.subTabs();
        // Sub-tabs aren't in ChatManager.getTabs() (only reachable via their parent's subTabs()), so a
        // sub popped out onto its own dedicated panel (visibleOnPanels() naming that panel directly,
        // not via drill-in) needs its own explicit lookup here too, not just the top-level stream below.
        List<ChatTab> result = ChatManager.getTabs().stream()
            .filter(t -> t.visibleOnPanels().contains(this.id))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        for (ChatTab t : ChatManager.getTabs()) {
            if (!t.isMultiTab()) continue;
            for (ChatTab sub : t.subTabs()) {
                if (sub.visibleOnPanels().contains(this.id)) result.add(sub);
            }
        }
        return result;
    }

    public ChatTab getActiveTab() {
        List<ChatTab> visible = getVisibleTabs();
        if (visible.isEmpty()) { activeTab = null; return null; }
        if (activeTab != null) {
            int idx = visible.indexOf(activeTab);
            if (idx >= 0) {
                activeTabId = idx;
                if (expandedParent == null) topLevelActiveTabId = idx;
                return activeTab;
            }
        }
        if (activeTabId >= visible.size()) activeTabId = 0;
        activeTab = visible.get(activeTabId);
        if (expandedParent == null) topLevelActiveTabId = activeTabId;
        return activeTab;
    }

    /** Returns the raw active-tab index (for serialization). */
    public int activeTabIndex() { return activeTabId; }

    public void setActiveTabId(int index) {
        List<ChatTab> visible = getVisibleTabs();
        if (visible.isEmpty()) { activeTabId = 0; activeTab = null; return; }
        activeTabId = Math.max(0, Math.min(index, visible.size() - 1));
        activeTab = visible.get(activeTabId);
        if (expandedParent == null) topLevelActiveTabId = activeTabId;
    }

    /** Scroll position in visual line index (0 = no scroll, newest at bottom — mirrors vanilla). */
    public int  getScrolledLines(ChatTab tab)               { return scrollPositions().getOrDefault(tab, 0); }
    public void setScrolledLines(ChatTab tab, int lines)    { scrollPositions().put(tab, lines); }
    public void resetScrollPositions()                      { scrollPositions().clear(); }
    public void resetScrollPositionsExcept(TabType keep)    { scrollPositions().keySet().removeIf(t -> t.type() != keep); }

    public List<LzChatLine> getTabLines(ChatTab tab) {
        if (tab.contentMode() == TabMode.CUSTOM_CONTENT) return customTabLines().get(tab.id());
        return tabMessages().get(tab.id());
    }

    public int tabLineCount(ChatTab tab) {
        List<LzChatLine> lines = tabMessages().get(tab.id());
        return lines == null ? 0 : lines.size();
    }

    public void shiftScrollForAdd(ChatTab tab, int delta) {
        if (delta == 0 || tab.contentMode() == TabMode.CUSTOM_CONTENT) return;
        boolean freeze = MultichatClient.CONFIG.selectionFreezeChat
            && ChatSelection.isSelecting()
            && ChatSelection.getPanel() == this
            && getActiveTab() == tab;
        int scrolled = getScrolledLines(tab);
        if (scrolled > 0 || freeze)
            setScrolledLines(tab, Math.max(0, scrolled + delta));
    }

    public void scrollActiveTabIntoView() {
        pendingScrollIntoView = true;
    }

    public void deferScrollActiveTabIntoView() { pendingScrollIntoView = true; }

    public boolean consumePendingScrollIntoView() {
        if (!pendingScrollIntoView) return false;
        pendingScrollIntoView = false;
        return true;
    }

    public int resolveTopY(int windowHeight) {
        if (primary || anchor != Anchor.CUSTOM)
            return LzMath.floor((float)(windowHeight - 40) / resolveScale());
        return (int)(customY * windowHeight / resolveScale()
                   + resolveLines() * resolveLineHeight() / 2f
                   - 10 / 2f); // TAB_HEIGHT = 10
    }

    public int computeXOffset(int scaledWindowWidth) {
        return switch (anchor) {
            case RIGHT  -> Math.round(scaledWindowWidth / resolveScale()) - resolveWidth() - 12 - margin;
            case CUSTOM -> !primary ? (int)(customX * scaledWindowWidth / resolveScale() - resolveWidth() / 2f - 4) : margin;
            default     -> margin;
        };
    }
}
