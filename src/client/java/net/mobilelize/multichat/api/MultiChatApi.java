package net.mobilelize.multichat.api;

import net.mobilelize.multichat.client.*;
import net.mobilelize.multichat.client.config.MultiChatConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point for other mods to interact with MultiChat.
 * <p>
 * Quick-start:
 * <pre>{@code
 * MultiChatMod api = MultiChatApi.register("mymod");
 * TabHandle tab = api.registerTab("alerts", "Alerts", TabVisibility.BOTH, 0xFF5500);
 * tab.sendMessage(Text.literal("Hello from mymod!"));
 *
 * MultiChatApi.MESSAGE_RECEIVED.register(msg -> false);  // observe all incoming chat
 * }</pre>
 */
public final class MultiChatApi {

    private static final Map<String, MultiChatMod> MODS = new LinkedHashMap<>();

    private MultiChatApi() {}

    // ------------------------------------------------------------------
    // Mod registration
    // ------------------------------------------------------------------

    /**
     * Register your mod with MultiChat and obtain a handle to create tabs and manage them.
     * Calling this multiple times with the same modId returns the same handle.
     */
    public static MultiChatMod register(String modId) {
        return MODS.computeIfAbsent(modId, MultiChatMod::new);
    }

    /** Returns an already-registered mod handle, or {@code null} if not registered. */
    public static MultiChatMod getMod(String modId) {
        return MODS.get(modId);
    }

    // ------------------------------------------------------------------
    // Events  (Fabric Event<T> — call .register() to subscribe)
    // ------------------------------------------------------------------

    /** Fires before MultiChat routes an incoming chat message. Return {@code true} to cancel routing. */
    public static final net.fabricmc.fabric.api.event.Event<MessageReceivedCallback> MESSAGE_RECEIVED =
        MessageReceivedCallback.EVENT;

    /** Fires after a message is added to any tab (including API and FILTERED tabs). */
    public static final net.fabricmc.fabric.api.event.Event<MessageAddedToTabCallback> MESSAGE_ADDED_TO_TAB =
        MessageAddedToTabCallback.EVENT;

    /** Fires when the user switches the active tab on either panel. */
    public static final net.fabricmc.fabric.api.event.Event<TabChangedCallback> TAB_CHANGED =
        TabChangedCallback.EVENT;

    /** Fires when the player sends a chat message (non-command). Return {@code true} to cancel. */
    public static final net.fabricmc.fabric.api.event.Event<MessageSentCallback> MESSAGE_SENT =
        MessageSentCallback.EVENT;

    /** Fires when /multichat execute {@literal <uuid>} is run. Use with TabHandle.newActionId() for click events. */
    public static final net.fabricmc.fabric.api.event.Event<TabActionCallback> TAB_ACTION =
        TabActionCallback.EVENT;

    // ------------------------------------------------------------------
    // Read-only access
    // ------------------------------------------------------------------

    /** All tabs currently registered (unmodifiable view). */
    public static List<ChatTab> getTabs() {
        return Collections.unmodifiableList(ChatManager.getTabs());
    }

    /** Find a tab by its full API id ({@code "modid:name"}), or {@code null}. */
    public static ChatTab getTab(String fullId) {
        return ChatManager.getTabs().stream()
            .filter(t -> fullId.equals(t.apiId()))
            .findFirst().orElse(null);
    }

    /** Current MultiChat config (read-only by convention — do not mutate). */
    public static MultiChatConfig getConfig() {
        return MultichatClient.CONFIG;
    }

    /** Clears the active context tab (no prefix applied to outgoing messages). */
    public static void clearContext() { ChatManager.contextTab = null; }

    /** All currently registered panels (unmodifiable). */
    public static List<ChatPanel> getPanels() {
        return Collections.unmodifiableList(ChatManager.getAllPanels());
    }

    /** Panel by ID, or {@code null} if not found. */
    public static ChatPanel getPanel(int panelId) {
        return ChatManager.getAllPanels().stream().filter(p -> p.id() == panelId).findFirst().orElse(null);
    }

    public static ChatPanel getLeftPanel()  { return ChatManager.primaryPanel(); }
    public static ChatPanel getRightPanel() {
        List<ChatPanel> p = ChatManager.getAllPanels();
        return p.size() > 1 ? p.get(1) : null;
    }

    /** Returns the All tab, or {@code null} if not found. */
    public static ChatTab getAllTab() {
        return ChatManager.getTabs().stream()
            .filter(t -> t.type() == TabType.ALL).findFirst().orElse(null);
    }
}
