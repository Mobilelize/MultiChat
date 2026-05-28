package net.mobilelize.multichat.api;

import net.minecraft.text.Text;
import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatSelection;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.client.filter.MessageRouter;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TabHandle {

    private final String fullId;
    private final ChatTab tab;

    TabHandle(String fullId, ChatTab tab) {
        this.fullId = fullId;
        this.tab    = tab;
    }

    // ------------------------------------------------------------------
    // Send message
    // ------------------------------------------------------------------

    /** Send an LzText message to this tab. Returns false if the client isn't ready or the tab is unregistered. */
    public boolean sendMessage(LzText message) {
        return sendMessage(message, false);
    }

    /** Send an LzText message, optionally routing it through the full vanilla pipeline (All + Vanilla + Filtered tabs). */
    public boolean sendMessage(LzText message, boolean addToAll) {
        if (!isReady()) return false;

        float scale     = (float) LzChat.chatScale();
        int   chatWidth = LzMath.ceil((float) LzChat.chatWidth() / scale);
        var   lines     = LzChat.breakLines(message, chatWidth);
        int   tick      = LzChat.ticks();

        List<LzChatLine> created = new ArrayList<>(lines.size());
        for (int j = 0; j < lines.size(); j++) {
            LzChatLine cl = LzChatLine.create(tick, lines.get(j), null, j == lines.size() - 1);
            tab.messages.addFirst(cl);
            created.add(cl);
        }
        adjustAfterAdd(tab, created.size());

        if (addToAll) {
            // Route through vanilla pipeline — hits All, Vanilla, and any matching Filtered/API tabs.
            // Skip this.tab in the router to prevent double-add if its filter rules match.
            MessageRouter.skipTabOnRoute = tab;
            try {
                LzChat.chatHud().addMessage(message.mc(), null, null);
            } finally {
                MessageRouter.skipTabOnRoute = null;
            }
        } else if (MultichatClient.CONFIG.logApiMessages) {
            MultichatClient.LOGGER.info("[{}] {}", tab.name, message.mc().getString());
        }

        return true;
    }

    /** Send a vanilla {@link Text} message (wrapped internally to LzText). */
    public boolean sendMessage(Text message) {
        return sendMessage(LzText.wrapMc(message), false);
    }

    /** Send a vanilla {@link Text} message, optionally also adding it to the All tab. */
    public boolean sendMessage(Text message, boolean addToAll) {
        return sendMessage(LzText.wrapMc(message), addToAll);
    }

    /** Send a plain string message. */
    public boolean sendMessage(String message) {
        return sendMessage(LzText.literal(message), false);
    }

    /** Send a plain string message, optionally also adding it to the All tab. */
    public boolean sendMessage(String message, boolean addToAll) {
        return sendMessage(LzText.literal(message), addToAll);
    }

    // ------------------------------------------------------------------
    // Runtime setters (fluent)
    // ------------------------------------------------------------------

    public TabHandle setName(String name) {
        tab.name = name != null ? name : "";
        return this;
    }

    public TabHandle setColor(int argbOrRgb) {
        tab.tabColor = argbOrRgb & 0xFFFFFF;
        return this;
    }

    public TabHandle setMessageCap(int cap) {
        tab.messageCap = Math.max(0, cap);
        return this;
    }

    public TabHandle setShowBadge(boolean show) {
        tab.showBadge = show;
        return this;
    }

    /** Replace the full set of panels this tab is visible on by panel IDs (0 = left, 1 = right). */
    public TabHandle setVisibleOnPanels(int... panelIds) {
        tab.visibleOnPanels.clear();
        for (int id : panelIds) tab.visibleOnPanels.add(id);
        return this;
    }

    /** Convenience overload using {@link TabVisibility} enum. */
    public TabHandle setVisibleOnPanels(TabVisibility visibility) {
        tab.visibleOnPanels.clear();
        tab.visibleOnPanels.addAll(TabVisibility.toPanelIds(visibility));
        return this;
    }

    /** Add or remove this tab from a single panel without affecting others. */
    public TabHandle setVisibleOnPanel(int panelId, boolean visible) {
        if (visible) tab.visibleOnPanels.add(panelId);
        else         tab.visibleOnPanels.remove(panelId);
        return this;
    }

    public Set<Integer> getVisiblePanels() { return tab.visibleOnPanels; }

    // ------------------------------------------------------------------
    // Context
    // ------------------------------------------------------------------

    public TabHandle setAsContext()   { ChatRenderer.contextTab = tab; return this; }
    public TabHandle clearContext()   { if (ChatRenderer.contextTab == tab) ChatRenderer.contextTab = null; return this; }

    public TabHandle setContextPrefix(String prefix) {
        tab.contextPrefix = prefix != null ? prefix : "";
        return this;
    }

    public TabHandle setContextCommandMode(boolean asCmd) {
        tab.contextCommandMode = asCmd;
        return this;
    }

    public String getContextPrefix() { return tab.contextPrefix; }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public String  getId()   { return fullId; }
    public ChatTab getTab()  { return tab; }

    /** True if the client is ready to accept messages and this tab is still registered. */
    public boolean isReady() {
        return LzChat.chatHud() != null && ChatRenderer.TABS.contains(tab);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private static void adjustAfterAdd(ChatTab tab, int added) {
        for (ChatPanel panel : ChatRenderer.PANELS) {
            int scrolled = panel.getScrolledLines(tab);
            if (scrolled > 0) panel.setScrolledLines(tab, scrolled + added);
        }
        ChatSelection.shiftLines(tab, added);

        if (tab.messageCap > 0) {
            int before = tab.messages.size();
            while (tab.messages.size() > tab.messageCap) tab.messages.removeLast();
            int removed = before - tab.messages.size();
            if (removed > 0) {
                for (ChatPanel panel : ChatRenderer.PANELS) {
                    int scrolled = panel.getScrolledLines(tab);
                    if (scrolled > 0)
                        panel.setScrolledLines(tab, Math.max(0, scrolled - removed));
                }
                ChatSelection.clampOrClear(tab);
            }
        }

        if (!isActiveInAnyPanel(tab)) tab.unreadCount++;
        if (!tab.messages.isEmpty())
            MessageAddedToTabCallback.EVENT.invoker().onMessageAdded(tab, tab.messages.getFirst());
    }

    private static boolean isActiveInAnyPanel(ChatTab tab) {
        for (ChatPanel panel : ChatRenderer.PANELS) {
            List<ChatTab> visible = panel.getVisibleTabs();
            int idx = visible.indexOf(tab);
            if (idx >= 0 && panel.activeTab == idx) return true;
        }
        return false;
    }
}
