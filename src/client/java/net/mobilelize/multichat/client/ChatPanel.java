package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.*;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzText;

import java.util.*;

public class ChatPanel {

    public enum Anchor { LEFT, RIGHT, CUSTOM }

    private Anchor  anchor             = Anchor.LEFT;
    private boolean primary            = false;
    private int     id                 = 0;
    private String  name               = "";

    private transient Map<UUID, List<LzChatLine>> tabMessages;
    private transient Map<UUID, List<LzChatLine>> customTabLines;

    private int     margin             = 0;
    private int activeTabId = 0;
    private transient ChatTab activeTab = null;
    private boolean enabled            = false;
    private boolean showOnlyWhenFocused = false;
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

    private void wrapEntryInto(List<LzChatLine> panelLines, ChatTab.MessageEntry entry) {
        int width = resolveWidth() - ChatManager.getIndicatorWidth(LzMessageIndicator.wrapNullable(entry.entry().indicator != null ? entry.entry().indicator.mc() : null));
        List<LzOrderedText> lines = LzChat.breakLines(LzText.wrapMc(entry.rendered().mc()), width);
        for (int i = 0; i < lines.size(); i++) {
            boolean endOfEntry = i == lines.size() - 1;
            panelLines.addFirst(LzChatLine.create(entry.entry().creationTick, lines.get(i), entry.entry().indicator, endOfEntry, entry.entry()));
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
        for (ChatTab tab : ChatManager.getTabs()) rebuildTabLines(tab);
    }


    // ── State ──────────────────────────────────────────────────────────────────
    public boolean isEnabled()                    { return enabled; }
    public void    setEnabled(boolean v)        { enabled = v; }


    public boolean showOnlyWhenFocused()        { return showOnlyWhenFocused; }
    public void    setShowOnlyWhenFocused(boolean v) { showOnlyWhenFocused = v; }
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
        return ChatManager.getTabs().stream()
            .filter(t -> t.visibleOnPanels().contains(this.id))
            .toList();
    }

    public ChatTab getActiveTab() {
        List<ChatTab> visible = getVisibleTabs();
        if (visible.isEmpty()) { activeTab = null; return null; }
        if (activeTab != null) {
            int idx = visible.indexOf(activeTab);
            if (idx >= 0) { activeTabId = idx; return activeTab; }
        }
        if (activeTabId >= visible.size()) activeTabId = 0;
        activeTab = visible.get(activeTabId);
        return activeTab;
    }

    /** Returns the raw active-tab index (for serialization). */
    public int activeTabIndex() { return activeTabId; }

    public void setActiveTabId(int index) {
        List<ChatTab> visible = getVisibleTabs();
        if (visible.isEmpty()) { activeTabId = 0; activeTab = null; return; }
        activeTabId = Math.max(0, Math.min(index, visible.size() - 1));
        activeTab = visible.get(activeTabId);
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
        if (tabLastRendered < 0) {
            pendingScrollIntoView = true;
            return;
        }
        if (activeTabId < tabScrollOffset) {
            tabScrollOffset = activeTabId;
        } else if (activeTabId > tabLastRendered) {
            int visibleCount = tabLastRendered - tabScrollOffset + 1;
            tabScrollOffset = Math.max(0, activeTabId - visibleCount + 1);
        }
    }

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
