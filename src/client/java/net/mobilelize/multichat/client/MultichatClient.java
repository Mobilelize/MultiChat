package net.mobilelize.multichat.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.mobilelize.fabriclib.client.LzAfk;
import net.mobilelize.fabriclib.config.LzConfig;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.fabriclib.world.LzWorld;
import net.mobilelize.multichat.api.MessageAddedToTabCallback;
import net.mobilelize.multichat.client.command.MultiChatCommand;
import net.mobilelize.multichat.client.config.MultiChatConfig;
import net.mobilelize.multichat.client.mouse.MouseClient;
import net.mobilelize.multichat.client.ping.PingPlayer;

import java.util.*;
import java.util.stream.Collectors;

public class MultichatClient implements ClientModInitializer {

    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("multichat");
    public static MultiChatConfig CONFIG;
    public static String pendingChatText = null;
    private static boolean firstTime = true;

    @Override
    public void onInitializeClient() {
        CONFIG = LzConfig.load(MultiChatConfig.class);
        LzAfk.init();
        restoreFromConfig();
        MultiChatCommand.register();
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> MouseClient.init());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            saveTabsToConfig();
            MouseClient.cleanup();
        });

        ClientTickEvents.END_CLIENT_TICK.register(minecraftClient -> {
            if (firstTime && LzWorld.isLoaded()) {
                firstTime = false;
                for (ChatTab tab : ChatManager.getTabs()) {
                    if (tab instanceof NoteTab nt) nt.rebuildMessages();
                    if (tab instanceof InfoTab it) it.rebuildMessages();
                }
            }
        });

        MessageAddedToTabCallback.EVENT.register((tab, message) -> PingPlayer.playForTab(tab));
    }

    // ------------------------------------------------------------------
    // Save — direct serialization, no mirror classes
    // ------------------------------------------------------------------

    public static void saveTabsToConfig() {
        CONFIG.panels   = new ArrayList<>(ChatManager.getAllPanels());
        CONFIG.tabs     = ChatManager.getTabs().stream()
            .filter(t -> !(t instanceof NoteTab) && t.type() != TabType.SEARCH && t.type() != TabType.INFO)
            .collect(Collectors.toList());
        CONFIG.noteTabs = ChatManager.getTabs().stream()
            .filter(t -> t instanceof NoteTab)
            .map(t -> (NoteTab) t)
            .collect(Collectors.toList());
        CONFIG.activeNoteTabId = ChatManager.activeNoteTab != null ? ChatManager.activeNoteTab.id() : null;
        CONFIG.infoTabOnPanel2 = ChatManager.getTabs().stream()
            .filter(t -> t.type() == TabType.INFO)
            .anyMatch(t -> t.visibleOnPanels().contains(1));
        CONFIG.save();
    }

    // ------------------------------------------------------------------
    // Restore — direct deserialization, resolve transient refs
    // ------------------------------------------------------------------

    private static void restoreFromConfig() {
        ChatManager.getTabs().clear();
        ChatManager.getTabs().addAll(CONFIG.tabs);
        ensureDefaultTabs();

        for (NoteTab nt : CONFIG.noteTabs) {
            for (NoteTab.NoteEntry e : nt.noteEntries()) e.restoreContent();
            ChatManager.getTabs().add(nt);
        }

        ChatManager.getAllPanels().clear();
        ChatManager.getAllPanels().addAll(CONFIG.panels);
        ensureDefaultPanels();

        Map<UUID, ChatTab> tabById = new HashMap<>();
        for (ChatTab t : ChatManager.getTabs()) tabById.put(t.id(), t);

        ChatManager.activeNoteTab = null;
        if (CONFIG.activeNoteTabId != null) {
            ChatTab found = tabById.get(CONFIG.activeNoteTabId);
            if (found instanceof NoteTab nt) ChatManager.activeNoteTab = nt;
        }
        if (ChatManager.activeNoteTab == null) {
            ChatManager.activeNoteTab = (NoteTab) ChatManager.getTabs().stream()
                .filter(t -> t instanceof NoteTab).findFirst().orElse(null);
        }

        for (ChatPanel panel : ChatManager.getAllPanels()) {
            if (panel.panelContextTabId() != null)
                panel.setPanelContextTab(tabById.get(panel.panelContextTabId()));
            panel.setActiveTabId(panel.activeTabIndex());
            panel.scrollActiveTabIntoView();
        }

        //for (NoteTab nt : CONFIG.noteTabs) nt.rebuildMessages();
    }

    // ------------------------------------------------------------------
    // Default panel bootstrap
    // ------------------------------------------------------------------

    private static void ensureDefaultTabs() {
        List<ChatTab> tabs = ChatManager.getTabs();

        boolean hasAll     = tabs.stream().anyMatch(t -> t.type() == TabType.ALL);
        boolean hasVanilla = tabs.stream().anyMatch(t -> t.type() == TabType.VANILLA);
        boolean hasInfo    = tabs.stream().anyMatch(t -> t.type() == TabType.INFO);

        if (!hasVanilla) {
            ChatTab vanilla = new ChatTab("Vanilla", TabType.VANILLA, List.of(0));
            int insertAt = hasAll ? 1 : 0;
            tabs.add(insertAt, vanilla);
        }
        if (!hasAll) {
            tabs.addFirst(new ChatTab("All", TabType.ALL, List.of(0, 1)));
        }
        if (!hasInfo) {
            tabs.add(2, new InfoTab(CONFIG.infoTabOnPanel2 ? List.of(1) : List.of()));
        }
    }

    private static void ensureDefaultPanels() {
        List<ChatPanel> panels = ChatManager.getAllPanels();

        // Primary panel — always index 0, id 0
        if (panels.isEmpty() || panels.get(0).id() != 0) {
            ChatPanel p = new ChatPanel();
            p.setId(0);
            p.setPrimary(true);
            p.setEnabled(true);
            p.setAnchor(ChatPanel.Anchor.LEFT);
            p.setName("Main");
            p.setLockUnfocusedToFocused(false);
            panels.addFirst(p);
        }
        panels.get(0).setPrimary(true);

        // Secondary panel — always index 1, id 1
        if (panels.size() < 2 || panels.get(1).id() != 1) {
            ChatPanel p = new ChatPanel();
            p.setId(1);
            p.setEnabled(true);
            p.setAnchor(ChatPanel.Anchor.RIGHT);
            p.setName("Second");
            p.setLockUnfocusedToFocused(false);
            panels.add(1, p);
            List<ChatTab> visible = p.getVisibleTabs();
            for (int i = 0; i < visible.size(); i++) {
                if (visible.get(i) instanceof InfoTab) { p.setActiveTabId(i); break; }
            }
        }
    }

    // ------------------------------------------------------------------
    // Text helpers (used by NoteTab.NoteEntry)
    // ------------------------------------------------------------------

    private static net.minecraft.registry.RegistryWrapper.WrapperLookup LOOKUP = null;
    private static net.minecraft.registry.RegistryWrapper.WrapperLookup lookup() {
        if (LOOKUP == null) LOOKUP = net.minecraft.registry.BuiltinRegistries.createWrapperLookup();
        return LOOKUP;
    }

    public static String textToJson(LzText text) {
        try {
            return net.minecraft.text.Text.Serialization.toJsonString(text.mc(), lookup());
        } catch (Exception e) {
            return "\"" + text.mc().getString().replace("\"", "\\\"") + "\"";
        }
    }

    public static LzText textFromJson(String json) {
        try {
            net.minecraft.text.Text t = net.minecraft.text.Text.Serialization.fromJson(json, lookup());
            return t != null ? LzText.wrapMc(t) : LzText.literal(json);
        } catch (Exception e) {
            return LzText.literal(json);
        }
    }
}
