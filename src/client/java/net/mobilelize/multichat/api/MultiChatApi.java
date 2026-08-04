package net.mobilelize.multichat.api;

import net.mobilelize.fabriclib.client.LzChatEntry;
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
 * TabHandle tab = api.registerTab("alerts", "Alerts", 0, 1);  // panel IDs: 0=left, 1=right
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

    /**
     * True only the very first time this modId has ever called this — persisted across restarts, not
     * just this session. Call once right after {@link #register(String)} to decide whether to seed
     * default tabs/settings: {@code register()}/{@code registerTab()} are always safe to call again on
     * every later launch (they restore/reuse what's already there), but without this check a mod has no
     * way to know whether it should recreate a tab the player deliberately deleted.
     */
    public static boolean isFirstEverRegistration(String modId) {
        if (MultichatClient.CONFIG == null) return true;
        java.util.Set<String> seen = MultichatClient.CONFIG.registeredMods;
        if (seen.contains(modId)) return false;
        seen.add(modId);
        MultichatClient.saveTabsToConfig();
        return true;
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

    /**
     * Fires when the focused panel changes (Ctrl+Arrow or tab click that switches focus).
     * {@code previous} is null if no panel was focused before.
     */
    public static final net.fabricmc.fabric.api.event.Event<PanelFocusedCallback> PANEL_FOCUSED =
        PanelFocusedCallback.EVENT;

    // ------------------------------------------------------------------
    // Highlight Words
    // ------------------------------------------------------------------

    /**
     * Register a keyword this mod wants highlighted. Applies to every incoming message, in every tab,
     * using the "Mod Keywords" color pair from Settings → Highlight Words — independent from the
     * player's own keyword list, and the player can mute all mod-registered rules at once from there.
     */
    public static void addHighlightRule(String pattern, String sourceModId) {
        HighlightEngine.addApiRule(pattern, sourceModId);
    }

    public static void removeHighlightRule(String pattern, String sourceModId) {
        HighlightEngine.removeApiRule(pattern, sourceModId);
    }

    /**
     * True only the very first time this modId has ever called this — persisted across restarts,
     * not just this session. Call this before seeding your default keyword list so you only ever
     * add your defaults once; re-adding them on every client start would silently undo any word
     * the player removed.
     */
    public static boolean isFirstHighlightRegistration(String sourceModId) {
        return HighlightEngine.isFirstRegistration(sourceModId);
    }

    // ------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------

    /** Register a chat color theme. Shows up in the theme picker (Settings → Appearance) alongside the built-ins. */
    public static void registerTheme(ChatTheme theme) {
        ThemeRegistry.register(theme);
    }

    public static void setActiveTheme(String id) {
        ThemeRegistry.setActive(id);
    }

    public static ChatTheme getActiveTheme() {
        return ThemeRegistry.active();
    }

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

    /** The currently focused panel (last clicked or Ctrl+Arrow'd into), or {@code null}. */
    public static ChatPanel getFocusedPanel() { return ChatManager.focusedPanel; }

    /** The active context tab (prefix applied to outgoing messages), or {@code null} if none. */
    public static ChatTab getContextTab() { return ChatManager.contextTab; }

    /**
     * Called internally when an API tab is removed (by player or via unregisterTab).
     * Cleans up the owning mod's handle map and fires any onRemoved callback.
     */
    public static void notifyTabRemoved(ChatTab tab) {
        if (tab.apiId() == null) return;
        int colon = tab.apiId().indexOf(':');
        if (colon < 0) return;
        MultiChatMod mod = MODS.get(tab.apiId().substring(0, colon));
        if (mod != null) mod.notifyTabRemoved(tab);
    }

    /** All tabs visible on the given panel (by panel ID). Includes all tab types. */
    public static List<ChatTab> getTabsOnPanel(int panelId) {
        return ChatManager.getTabs().stream()
            .filter(t -> t.visibleOnPanels().contains(panelId))
            .toList();
    }

    /** Deletes a message (by its LzChatEntry identity, from TabHandle.getMessageEntries()) from every
     *  tab that has it, including multi-tab sub-tabs — same as the player-facing "Delete from All"
     *  context menu item. */
    public static void deleteMessageEverywhere(LzChatEntry entry) {
        ChatManager.deleteMessageEverywhere(entry);
    }

    // ------------------------------------------------------------------
    // Panels
    // ------------------------------------------------------------------

    /** Creates a new blank, centered panel and returns a handle to configure it. No tabs attached —
     *  add them via {@link PanelHandle#addTab(TabHandle)}. */
    public static PanelHandle createPanel(String name) {
        return new PanelHandle(ChatManager.createPanel(name));
    }
}
