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

    public Anchor  anchor             = Anchor.LEFT;
    public boolean primary            = false;
    public int     id                 = 0;
    public String  name               = "";
    public int     margin             = 0;
    public int     activeTab          = 0;
    public boolean enabled            = false;
    public boolean showOnlyWhenFocused = false;
    public boolean showBadges         = true;
    public boolean showTabs           = true;
    public boolean tabsAtTop          = false;
    public int     tabScrollOffset    = 0;
    public int     tabLastRendered    = -1;
    public int     tabFlashTick       = -1000;

    // Custom position — fractions of screen dimensions (0.0–1.0), only used when anchor == CUSTOM
    public float customX = 0f;
    public float customY = 0f;

    // Per-panel display overrides — ignored on primary panel (always uses MC defaults)
    public int    customWidth       = -1;   // -1 = use MC default
    public int    customHeightLines = -1;   // -1 = use MC default
    public float  customScale       = -1f;  // -1 = use MC default
    public int    bgColor           = 0;    // 0 = transparent
    public double customBgOpacity   = -1;   // -1 = use MC default
    public double customChatOpacity = -1;   // -1 = use MC default

    public ChatTab  panelContextTab    = null;
    public boolean  useActiveTabContext = false;

    public ChatTab resolveContextTab() {
        if (useActiveTabContext) return getActiveTab();
        return panelContextTab;
    }

    private final Map<ChatTab, Integer> scrollPositions = new HashMap<>();

    public ChatPanel() {}

    // -----------------------------------------------------------------------
    // Resolvers — primary panel always uses MC defaults; others use override if set
    // -----------------------------------------------------------------------

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
        // TODO: compute from customScale when non-primary panels have custom scale set
        return LzChat.chatLineHeight();
    }

    public double resolveBgOpacity() {
        if (primary || customBgOpacity < 0) return LzOptions.textBackgroundOpacity();
        return customBgOpacity;
    }

    public double resolveChatOpacity() {
        if (primary || customChatOpacity < 0) return LzOptions.chatOpacity() * 0.9 + 0.1;
        return customChatOpacity;
    }

    // -----------------------------------------------------------------------
    // Tab helpers
    // -----------------------------------------------------------------------

    public List<ChatTab> getVisibleTabs() {
        return ChatRenderer.TABS.stream()
            .filter(t -> t.visibleOnPanels.contains(this.id))
            .toList();
    }

    public ChatTab getActiveTab() {
        List<ChatTab> visible = getVisibleTabs();
        if (visible.isEmpty()) return null;
        if (activeTab >= visible.size()) activeTab = 0;
        return visible.get(activeTab);
    }

    public int getScrolledLines(ChatTab tab) {
        return scrollPositions.getOrDefault(tab, 0);
    }

    public void setScrolledLines(ChatTab tab, int lines) {
        scrollPositions.put(tab, lines);
    }

    public void resetScrollPositions() {
        scrollPositions.clear();
    }

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
        // customY is center-Y fraction; recover topY from center
        return (int)(customY * windowHeight / resolveScale()
                   + resolveLines() * resolveLineHeight() / 2f
                   - ChatRenderer.TAB_HEIGHT / 2f);
    }

    public int computeXOffset(int scaledWindowWidth) {
        return switch (anchor) {
            case RIGHT  -> Math.round(scaledWindowWidth / resolveScale()) - resolveWidth() - 12 - margin;
            // customX is center-X fraction; recover x1 from center
            case CUSTOM -> !primary ? (int)(customX * scaledWindowWidth / resolveScale() - resolveWidth() / 2f - 4) : margin;
            default     -> margin;
        };
    }
}
