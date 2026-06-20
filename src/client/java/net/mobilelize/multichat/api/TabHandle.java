package net.mobilelize.multichat.api;

import net.minecraft.text.Text;
import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.*;
import net.mobilelize.multichat.client.filter.MessageRouter;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TabHandle {

    private final String fullId;
    private final ChatTab tab;

    private Runnable onRemovedCallback;

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

        int tick = LzChat.ticks();
        LzChatEntry entry = new LzChatEntry(tick, message, null);
        ChatTab.MessageEntry me = new ChatTab.MessageEntry(message, message, entry);
        ChatManager.addNewMessage(tab, me);

        if (addToAll) {
            MessageRouter.skipTabOnRoute = tab;
            try {
                LzChat.chatHud().addMessage(message.mc(), null, null);
            } finally {
                MessageRouter.skipTabOnRoute = null;
            }
        } else if (MultichatClient.CONFIG.logApiMessages) {
            MultichatClient.LOGGER.info("[{}] {}", tab.name(), message.mc().getString());
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
        tab.setName(name);
        return this;
    }

    public TabHandle setColor(int argbOrRgb) {
        tab.setTabColor(argbOrRgb);
        return this;
    }

    public TabHandle setMessageCap(int cap) {
        tab.setMessageCap(cap);
        return this;
    }

    public TabHandle setShowBadge(boolean show) {
        tab.setShowBadge(show);
        return this;
    }

    /** Replace the full set of panels this tab is visible on by panel IDs. */
    public TabHandle setVisibleOnPanels(int... panelIds) {
        tab.visibleOnPanels().clear();
        for (int id : panelIds) tab.visibleOnPanels().add(id);
        return this;
    }

    /** Add or remove this tab from a single panel without affecting others. */
    public TabHandle setVisibleOnPanel(int panelId, boolean visible) {
        if (visible) tab.visibleOnPanels().add(panelId);
        else         tab.visibleOnPanels().remove(panelId);
        return this;
    }

    public Set<Integer> getVisiblePanels() { return tab.visibleOnPanels(); }

    // ------------------------------------------------------------------
    // Sound pings
    // ------------------------------------------------------------------

    public TabHandle setPingEnabled(boolean enabled) {
        tab.setPingEnabled(enabled);
        return this;
    }

    public TabHandle setPingSound(String soundId) {
        tab.setPingSound(soundId != null ? soundId : "");
        return this;
    }

    /** Volume multiplier. 1.0 = normal, 0.0–2.0 range. -1 = inherit global. */
    public TabHandle setPingVolume(float volume) {
        tab.setPingVolume(volume);
        return this;
    }

    /** Pitch multiplier. 1.0 = normal, 0.5–2.0 range. -1 = inherit global. */
    public TabHandle setPingPitch(float pitch) {
        tab.setPingPitch(pitch);
        return this;
    }

    /** Minimum seconds between pings. 0 = no cooldown, -1 = inherit global. */
    public TabHandle setPingCooldownSec(int seconds) {
        tab.setPingCooldownSec(seconds);
        return this;
    }

    /** Seconds of message silence required before Require Silence condition fires. -1 = inherit global. */
    public TabHandle setPingSilenceThresholdSec(int seconds) {
        tab.setPingSilenceThresholdSec(seconds);
        return this;
    }

    /** Seconds without mouse movement to be considered AFK. -1 = inherit global. */
    public TabHandle setPingAfkThresholdSec(int seconds) {
        tab.setPingAfkThresholdSec(seconds);
        return this;
    }

    /** When true this tab uses the global ping conditions; when false the per-tab condition flags below apply. */
    public TabHandle setPingUseGlobalConditions(boolean useGlobal) {
        tab.setPingUseGlobalConditions(useGlobal);
        return this;
    }

    public TabHandle setPingCondAlways(boolean enabled) {
        tab.setPingCondAlways(enabled);
        return this;
    }

    public TabHandle setPingCondUnfocusedTab(boolean enabled) {
        tab.setPingCondUnfocusedTab(enabled);
        return this;
    }

    public TabHandle setPingCondGameUnfocused(boolean enabled) {
        tab.setPingCondGameUnfocused(enabled);
        return this;
    }

    public TabHandle setPingCondAfk(boolean enabled) {
        tab.setPingCondAfk(enabled);
        return this;
    }

    public TabHandle setPingCondRequireSilence(boolean enabled) {
        tab.setPingCondRequireSilence(enabled);
        return this;
    }

    // Ping getters
    public boolean getPingEnabled()              { return tab.pingEnabled(); }
    public String  getPingSound()                { return tab.pingSound(); }
    public float   getPingVolume()               { return tab.pingVolume(); }
    public float   getPingPitch()                { return tab.pingPitch(); }
    public int     getPingCooldownSec()          { return tab.pingCooldownSec(); }
    public int     getPingSilenceThresholdSec()  { return tab.pingSilenceThresholdSec(); }
    public int     getPingAfkThresholdSec()      { return tab.pingAfkThresholdSec(); }
    public boolean getPingUseGlobalConditions()  { return tab.pingUseGlobalConditions(); }

    // ------------------------------------------------------------------
    // Message access & control
    // ------------------------------------------------------------------

    /** All stored messages as text, newest first. */
    public List<LzText> getMessages() {
        return tab.messages().stream().map(ChatTab.MessageEntry::content).toList();
    }

    /** All stored message entries, newest first. Full data including rendered form and chat entry. */
    public List<ChatTab.MessageEntry> getMessageEntries() {
        return Collections.unmodifiableList(tab.messages());
    }

    /** Clear all messages from this tab and all panels. No ping, no event. */
    public TabHandle clearMessages() {
        tab.clearMessages();
        for (ChatPanel panel : ChatManager.getAllPanels()) panel.clearTabLines(tab);
        return this;
    }

    /**
     * Add a message silently — no ping, no {@code MessageAddedToTab} event.
     * Respects message cap.
     */
    public TabHandle addMessageSilent(LzText message) {
        int tick = LzChat.ticks();
        LzChatEntry entry = new LzChatEntry(tick, message, null);
        ChatTab.MessageEntry me = new ChatTab.MessageEntry(message, message, entry);
        tab.addFirst(me);
        if (tab.messageCap() > 0 && tab.messageCount() > tab.messageCap()) tab.removeLast();
        for (ChatPanel panel : ChatManager.getAllActivePanels()) panel.addTabMessage(tab, me);
        return this;
    }

    /** Add a vanilla {@link Text} message silently. */
    public TabHandle addMessageSilent(Text message) {
        return addMessageSilent(LzText.wrapMc(message));
    }

    /** Add a plain string message silently. */
    public TabHandle addMessageSilent(String message) {
        return addMessageSilent(LzText.literal(message));
    }

    /**
     * Replace all stored messages with the provided list (pass oldest → newest order).
     * No pings, no events. Ignores message cap — caller controls the size.
     */
    public TabHandle setMessages(List<LzText> messages) {
        tab.clearMessages();
        for (ChatPanel panel : ChatManager.getAllPanels()) panel.clearTabLines(tab);
        int tick = LzChat.ticks();
        for (LzText msg : messages) {
            LzChatEntry entry = new LzChatEntry(tick, msg, null);
            tab.addFirst(new ChatTab.MessageEntry(msg, msg, entry));
        }
        for (ChatPanel panel : ChatManager.getAllActivePanels()) panel.rebuildTabLines(tab);
        return this;
    }

    // ------------------------------------------------------------------
    // Unread
    // ------------------------------------------------------------------

    public int getUnreadCount() { return tab.unreadCount(); }

    public TabHandle clearUnread() { tab.clearUnread(); return this; }

    // ------------------------------------------------------------------
    // Custom content
    // ------------------------------------------------------------------

    /** Switch to custom content mode and set displayed lines. Scroll is preserved unless resetScroll is true. */
    public TabHandle setContent(List<LzText> lines) {
        return setContent(lines, false);
    }

    public TabHandle setContent(List<LzText> lines, boolean resetScroll) {
        tab.setCustomRawLines(lines);
        tab.setContentMode(TabMode.CUSTOM_CONTENT);
        for (ChatPanel panel : ChatManager.getAllActivePanels())
            panel.setCustomTabLines(tab, lines, resetScroll);
        return this;
    }

    /** Switch to custom content mode (shows lines set via {@link #setContent}). */
    public TabHandle useCustomContent() {
        tab.setContentMode(TabMode.CUSTOM_CONTENT);
        return this;
    }

    /** Switch back to routed messages mode (shows incoming chat history). */
    public TabHandle useRoutedMessages() {
        tab.setContentMode(TabMode.ROUTED_MESSAGES);
        return this;
    }

    /** True if this tab is currently showing custom content rather than routed messages. */
    public boolean isCustomContent() { return tab.contentMode() == TabMode.CUSTOM_CONTENT; }

    /** Generate a new UUID string to use as a click-event action id. Pass it to /multichat execute. */
    public String newActionId() {
        return UUID.randomUUID().toString();
    }

    // ------------------------------------------------------------------
    // Context
    // ------------------------------------------------------------------

    public TabHandle setAsContext()   { ChatManager.contextTab = tab; return this; }
    public TabHandle clearContext()   { if (ChatManager.contextTab == tab) ChatManager.contextTab = null; return this; }

    public TabHandle setContextPrefix(String prefix) {
        tab.setContextPrefix(prefix);
        return this;
    }

    public TabHandle setContextCommandMode(boolean asCmd) {
        tab.setContextCommandMode(asCmd);
        return this;
    }

    public String getContextPrefix() { return tab.contextPrefix(); }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    /**
     * Allow or prevent the player from deleting this tab via the tab list or /multichat tab remove.
     * Default is {@code false} — the player cannot delete API tabs unless the API explicitly allows it.
     */
    public TabHandle setPlayerDeletable(boolean v) {
        tab.setPlayerDeletable(v);
        return this;
    }

    public boolean isPlayerDeletable() { return tab.playerDeletable(); }

    /**
     * Register a callback that fires when this tab is removed — either by the player (if playerDeletable)
     * or by {@link net.mobilelize.multichat.api.MultiChatMod#unregisterTab}.
     * Only one callback is stored; calling this again replaces the previous one.
     */
    public TabHandle onRemoved(Runnable callback) {
        this.onRemovedCallback = callback;
        return this;
    }

    /** Called internally when the tab is removed. */
    void fireRemoved() {
        if (onRemovedCallback != null) onRemovedCallback.run();
    }

    public String  getId()   { return fullId; }
    public ChatTab getTab()  { return tab; }

    /** True if the client is ready to accept messages and this tab is still registered. */
    public boolean isReady() {
        return LzChat.chatHud() != null && ChatManager.getTabs().contains(tab);
    }

}
