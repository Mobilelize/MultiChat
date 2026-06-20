package net.mobilelize.multichat.api;

import net.minecraft.text.Text;
import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.*;
import net.mobilelize.multichat.client.filter.MessageRouter;

import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    /** Replace the full set of panels this tab is visible on by panel IDs (0 = left, 1 = right). */
    public TabHandle setVisibleOnPanels(int... panelIds) {
        tab.visibleOnPanels().clear();
        for (int id : panelIds) tab.visibleOnPanels().add(id);
        return this;
    }

    /** Convenience overload using {@link TabVisibility} enum. */
    public TabHandle setVisibleOnPanels(TabVisibility visibility) {
        tab.visibleOnPanels().clear();
        tab.visibleOnPanels().addAll(TabVisibility.toPanelIds(visibility));
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
    // Custom content
    // ------------------------------------------------------------------

    /** Switch to CUSTOM mode and set displayed lines. Scroll is preserved unless resetScroll is true. */
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

    /** Switch display mode. ROUTED_MESSAGES shows routed history; CUSTOM_CONTENT shows content set via setContent. */
    public TabHandle setMode(TabMode mode) {
        tab.setContentMode(mode);
        return this;
    }

    public TabMode getMode() { return tab.contentMode(); }

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

    public String  getId()   { return fullId; }
    public ChatTab getTab()  { return tab; }

    /** True if the client is ready to accept messages and this tab is still registered. */
    public boolean isReady() {
        return LzChat.chatHud() != null && ChatManager.getTabs().contains(tab);
    }

}
