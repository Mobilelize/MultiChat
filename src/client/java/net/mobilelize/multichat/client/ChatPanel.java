package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzOptions;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatPanel {

    public enum Anchor { LEFT, RIGHT, CUSTOM }

    private Anchor  anchor             = Anchor.LEFT;
    private boolean primary            = false;
    private int     id                 = 0;
    private String  name               = "";
    private int     margin             = 0;
    private int     activeTab          = 0;
    private ChatTab activeTabRef       = null;
    private boolean enabled            = false;
    private boolean showOnlyWhenFocused = false;
    private boolean showBadges         = true;
    private boolean showTabs           = true;
    private boolean tabsAtTop          = false;
    private int     tabScrollOffset    = 0;
    private int     tabLastRendered    = -1;
    private int     tabFlashTick       = -1000;

    private float  customX            = 0f;
    private float  customY            = 0f;
    private int    customWidth        = -1;
    private int    customHeightLines  = -1;
    private float  customScale        = -1f;
    private int    bgColor            = 0;
    private double customBgOpacity    = -1;
    private double customChatOpacity  = -1;

    private ChatTab panelContextTab    = null;
    private boolean useActiveTabContext = false;

    private final Map<ChatTab, Integer> scrollPositions = new HashMap<>();

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

    // ── State ──────────────────────────────────────────────────────────────────
    public boolean enabled()                    { return enabled; }
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
    public void   setCustomWidth(int v)       { customWidth = v; }
    public int    customHeightLines()         { return customHeightLines; }
    public void   setCustomHeightLines(int v) { customHeightLines = v; }
    public float  customScale()               { return customScale; }
    public void   setCustomScale(float v)     { customScale = v; }
    public int    bgColor()                   { return bgColor; }
    public void   setBgColor(int v)           { bgColor = v & 0xFFFFFF; }
    public double customBgOpacity()               { return customBgOpacity; }
    public void   setCustomBgOpacity(double v)    { customBgOpacity = v; }
    public double customChatOpacity()             { return customChatOpacity; }
    public void   setCustomChatOpacity(double v)  { customChatOpacity = v; }

    // ── Context ────────────────────────────────────────────────────────────────
    public ChatTab panelContextTab()               { return panelContextTab; }
    public void    setPanelContextTab(ChatTab v)   { panelContextTab = v; }
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

    public int resolveLines() {
        if (primary || customHeightLines <= 0) return LzChat.visibleLineCount();
        return customHeightLines;
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
        return ChatRenderer.TABS.stream()
            .filter(t -> t.visibleOnPanels().contains(this.id))
            .toList();
    }

    public ChatTab getActiveTab() {
        List<ChatTab> visible = getVisibleTabs();
        if (visible.isEmpty()) { activeTabRef = null; return null; }
        if (activeTabRef != null) {
            int idx = visible.indexOf(activeTabRef);
            if (idx >= 0) { activeTab = idx; return activeTabRef; }
        }
        if (activeTab >= visible.size()) activeTab = 0;
        activeTabRef = visible.get(activeTab);
        return activeTabRef;
    }

    /** Returns the raw active-tab index (for serialization). */
    public int activeTabIndex() { return activeTab; }

    public void setActiveTab(int index) {
        List<ChatTab> visible = getVisibleTabs();
        if (visible.isEmpty()) { activeTab = 0; activeTabRef = null; return; }
        activeTab = Math.max(0, Math.min(index, visible.size() - 1));
        activeTabRef = visible.get(activeTab);
    }

    /** Scroll position in visual line index (0 = no scroll, newest at bottom — mirrors vanilla). */
    public int  getScrolledLines(ChatTab tab)               { return scrollPositions.getOrDefault(tab, 0); }
    public void setScrolledLines(ChatTab tab, int lines)    { scrollPositions.put(tab, lines); }
    public void resetScrollPositions()                      { scrollPositions.clear(); }

    public void scrollActiveTabIntoView() {
        if (activeTab < tabScrollOffset) {
            tabScrollOffset = activeTab;
        } else if (tabLastRendered >= 0 && activeTab > tabLastRendered) {
            int max = Math.max(0, getVisibleTabs().size() - 1);
            tabScrollOffset = Math.min(activeTab, max);
        }
    }

    public int resolveTopY(int windowHeight) {
        if (primary || anchor != Anchor.CUSTOM)
            return LzMath.floor((float)(windowHeight - 40) / resolveScale());
        return (int)(customY * windowHeight / resolveScale()
                   + resolveLines() * resolveLineHeight() / 2f
                   - ChatRenderer.TAB_HEIGHT / 2f);
    }

    public int computeXOffset(int scaledWindowWidth) {
        return switch (anchor) {
            case RIGHT  -> Math.round(scaledWindowWidth / resolveScale()) - resolveWidth() - 12 - margin;
            case CUSTOM -> !primary ? (int)(customX * scaledWindowWidth / resolveScale() - resolveWidth() / 2f - 4) : margin;
            default     -> margin;
        };
    }
}
