package net.mobilelize.multichat.api;

import net.minecraft.text.Text;
import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.text.LzStyle;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.*;
import net.mobilelize.multichat.client.filter.BlacklistMode;
import net.mobilelize.multichat.client.filter.FilterRule;
import net.mobilelize.multichat.client.filter.FilterType;
import net.mobilelize.multichat.client.filter.IndicatorFilter;
import net.mobilelize.multichat.client.filter.MessageRouter;
import net.mobilelize.multichat.client.filter.WhitelistMode;

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

    public TabHandle setShowTimestamps(boolean show) {
        tab.setShowTimestamps(show);
        return this;
    }
    public boolean getShowTimestamps() { return tab.showTimestamps(); }

    /** Custom message background color for this tab specifically. 0 = inherit the panel/theme default. */
    public TabHandle setMsgBgColor(int argbOrRgb) {
        tab.setMsgBgColor(argbOrRgb);
        return this;
    }

    public TabHandle setCollapseRepeating(boolean enabled) {
        tab.setCollapseRepeating(enabled);
        return this;
    }

    /** Only take effect together with setCollapseRepeating(true): also require identical formatting. */
    public TabHandle setCollapseMatchFormatting(boolean enabled) {
        tab.setCollapseMatchFormatting(enabled);
        return this;
    }

    public boolean getCollapseRepeating()       { return tab.collapseRepeating(); }
    public boolean getCollapseMatchFormatting() { return tab.collapseMatchFormatting(); }

    // ------------------------------------------------------------------
    // Difference mode
    // ------------------------------------------------------------------
    // Routes messages through a layered pipeline: lower-index layers claim messages first, higher
    // layers only see what wasn't already claimed. See ConfigMenu → Difference Mode for the player-facing
    // explanation of the same mechanic.

    public TabHandle setDifferenceMode(boolean enabled) {
        tab.setDifferenceMode(enabled);
        return this;
    }

    /** Higher = higher priority. All matching tabs at the same index receive the message together. */
    public TabHandle setDifferenceLayerIndex(int index) {
        tab.setDifferenceLayerIndex(index);
        return this;
    }

    /** This tab receives messages normally but never blocks the difference pipeline for other tabs. */
    public TabHandle setDifferenceException(boolean enabled) {
        tab.setDifferenceException(enabled);
        return this;
    }

    /** When enabled, this tab blocks the difference pipeline even when it isn't the active tab, as long
     *  as it's assigned to any enabled panel. */
    public TabHandle setDifferenceInclusion(boolean enabled) {
        tab.setDifferenceInclusion(enabled);
        return this;
    }

    public boolean getDifferenceMode()      { return tab.differenceMode(); }
    public int     getDifferenceLayerIndex(){ return tab.differenceLayerIndex(); }
    public boolean getDifferenceException() { return tab.differenceException(); }
    public boolean getDifferenceInclusion() { return tab.differenceInclusion(); }

    // ------------------------------------------------------------------
    // Filtering
    // ------------------------------------------------------------------
    // Lets a mod configure its own tab's whitelist/blacklist entirely in code — no player setup required.
    // Same FilterRuleSet/FilterRule mechanics as the player-facing Whitelist/Blacklist screens.

    public TabHandle addWhitelistRule(FilterType type, String value, IndicatorFilter indicator) {
        tab.filterRules().whitelist().add(new FilterRule(type, value, indicator));
        return this;
    }

    /** Shorthand for a plain CONTAINS rule matching any message indicator. */
    public TabHandle addWhitelistRule(String containsValue) {
        return addWhitelistRule(FilterType.CONTAINS, containsValue, IndicatorFilter.ANY);
    }

    public TabHandle addBlacklistRule(FilterType type, String value, IndicatorFilter indicator) {
        tab.filterRules().blacklist().add(new FilterRule(type, value, indicator));
        return this;
    }

    /** Shorthand for a plain CONTAINS rule matching any message indicator. */
    public TabHandle addBlacklistRule(String containsValue) {
        return addBlacklistRule(FilterType.CONTAINS, containsValue, IndicatorFilter.ANY);
    }

    public TabHandle clearWhitelistRules() { tab.filterRules().whitelist().clear(); return this; }
    public TabHandle clearBlacklistRules() { tab.filterRules().blacklist().clear(); return this; }

    /** ANY: match any rule. ALL: must match every rule. */
    public TabHandle setWhitelistMode(WhitelistMode mode) {
        tab.filterRules().setWhitelistMode(mode);
        return this;
    }

    /** ANY: exclude if any rule matches. ALL: exclude only if all rules match. */
    public TabHandle setBlacklistMode(BlacklistMode mode) {
        tab.filterRules().setBlacklistMode(mode);
        return this;
    }

    public List<FilterRule> getWhitelistRules() { return Collections.unmodifiableList(tab.filterRules().whitelist()); }
    public List<FilterRule> getBlacklistRules() { return Collections.unmodifiableList(tab.filterRules().blacklist()); }

    /** Rescans the All tab's full message history against this tab's current rules and rebuilds it —
     *  call after changing rules if you want existing messages to reflect the new configuration
     *  instead of only applying to messages that arrive from now on. */
    public TabHandle refilter() {
        ChatManager.refilterTab(tab);
        return this;
    }

    // ------------------------------------------------------------------
    // Multi-tab (nested sub-tabs)
    // ------------------------------------------------------------------
    // Sub-tabs are real tabs, filtered within whatever this tab's own rules already let through — same
    // layered gate as the player-facing "Multi Tab" toggle. There's always an implicit "All" sub-tab
    // (index 0) catching everything this tab lets through unfiltered; extra sub-tabs narrow further.

    /** Turns this tab into a multi-tab. Safe to call repeatedly — a no-op once already on, and turning
     *  it on for the first time auto-creates the protected "All" sub-tab. */
    public TabHandle setMultiTab(boolean enabled) {
        tab.setMultiTab(enabled);
        return this;
    }

    public boolean isMultiTab() { return tab.isMultiTab(); }

    /**
     * Adds a sub-tab under this multi-tab (turning it into one first if needed) and returns a handle to
     * it — same TabHandle type as a top-level tab, so every other method here (filter rules, pings,
     * messages…) works on it identically. The name only needs to be unique among this tab's own subs.
     */
    public TabHandle addSubTab(String name) {
        tab.setMultiTab(true);
        ChatTab sub = new ChatTab(name, TabType.FILTERED, java.util.Collections.emptyList());
        tab.subTabs().add(sub);
        return new TabHandle(fullId + ":" + name, sub);
    }

    /** The always-present "All" sub-tab (index 0), or null if this tab isn't a multi-tab. Catches
     *  everything this tab's own filter lets through, regardless of its own rules. */
    public TabHandle getAllSubTab() {
        if (!tab.isMultiTab() || tab.subTabs().isEmpty()) return null;
        ChatTab all = tab.subTabs().get(0);
        return new TabHandle(fullId + ":" + all.name(), all);
    }

    /** All sub-tabs of this multi-tab (including the "All" sub at index 0), or empty if not a multi-tab. */
    public List<TabHandle> getSubTabs() {
        if (!tab.isMultiTab()) return List.of();
        return tab.subTabs().stream().map(sub -> new TabHandle(fullId + ":" + sub.name(), sub)).toList();
    }

    /** Removes a sub-tab by name. The protected "All" sub-tab (index 0) can't be removed this way. */
    public TabHandle removeSubTab(String name) {
        if (!tab.isMultiTab()) return this;
        List<ChatTab> subs = tab.subTabs();
        if (!subs.isEmpty() && subs.get(0).name().equals(name)) return this; // protect "All"
        subs.removeIf(s -> s.name().equals(name));
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

    /** Ping even while this tab is currently visible/active on a panel. */
    public TabHandle setPingCondActiveTab(boolean enabled) {
        tab.setPingCondActiveTab(enabled);
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
    public boolean getPingCondAlways()           { return tab.pingCondAlways(); }
    public boolean getPingCondActiveTab()        { return tab.pingCondActiveTab(); }
    public boolean getPingCondUnfocusedTab()     { return tab.pingCondUnfocusedTab(); }
    public boolean getPingCondGameUnfocused()    { return tab.pingCondGameUnfocused(); }
    public boolean getPingCondAfk()              { return tab.pingCondAfk(); }
    public boolean getPingCondRequireSilence()   { return tab.pingCondRequireSilence(); }

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

    /**
     * One-call version of the click-to-run-a-callback pattern: generates an action id, registers
     * {@code onClicked} to fire when it's triggered, and returns a ready-to-use click style — no need
     * to know the "/multichat execute &lt;id&gt;" format or wire up {@link MultiChatApi#TAB_ACTION}
     * yourself:
     * <pre>{@code
     * LzText shown = original.withStyle(tab.newAction(() -> restoreOriginal(entry)));
     * }</pre>
     */
    public LzStyle newAction(Runnable onClicked) {
        String id = newActionId();
        MultiChatApi.TAB_ACTION.register(actionId -> {
            if (actionId.equals(id)) onClicked.run();
        });
        return LzStyle.EMPTY.withClickRunCommand("/multichat execute " + id);
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
