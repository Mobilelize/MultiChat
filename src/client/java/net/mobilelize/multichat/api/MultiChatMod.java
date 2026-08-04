package net.mobilelize.multichat.api;

import net.mobilelize.multichat.client.*;

import java.util.*;

public class MultiChatMod {

    private final String modId;
    private final Map<String, TabHandle> handles = new LinkedHashMap<>();

    MultiChatMod(String modId) {
        this.modId = modId;
    }

    // ------------------------------------------------------------------
    // Tab registration
    // ------------------------------------------------------------------

    public TabHandle registerTab(String name, String displayName) {
        return registerTabInternal(name, displayName, allPanelIds());
    }

    public TabHandle registerTab(String name, String displayName, int... panelIds) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (int id : panelIds) ids.add(id);
        return registerTabInternal(name, displayName, ids);
    }

    private TabHandle registerTabInternal(String name, String displayName, Set<Integer> defaultPanels) {
        TabHandle existing = handles.get(name);
        if (existing != null) {
            if (existing.isReady()) return existing;
            handles.remove(name); // tab was removed externally — re-register
        }

        String fullId = modId + ":" + name;
        ChatTab saved = findSaved(fullId);

        String resolvedName = (saved != null && !saved.name().isEmpty()) ? saved.name() : displayName;
        Set<Integer> resolvedPanels = (saved != null && !saved.visibleOnPanels().isEmpty())
            ? new LinkedHashSet<>(saved.visibleOnPanels())
            : defaultPanels;

        ChatTab tab = new ChatTab(resolvedName, TabType.API, resolvedPanels);
        tab.setApiId(fullId);

        if (saved != null) {
            tab.setTabColor(saved.tabColor());
            tab.filterRules().setWhitelistMode(saved.filterRules().whitelistMode());
            tab.filterRules().setBlacklistMode(saved.filterRules().blacklistMode());
            tab.filterRules().whitelist().addAll(saved.filterRules().whitelist());
            tab.filterRules().blacklist().addAll(saved.filterRules().blacklist());
            tab.setContextPrefix(saved.contextPrefix());
            tab.setContextCommandMode(saved.contextCommandMode());
            tab.setPingEnabled(saved.pingEnabled());
            tab.setPingSound(saved.pingSound());
            tab.setPingVolume(saved.pingVolume());
            tab.setPingPitch(saved.pingPitch());
            tab.setPingUseGlobalConditions(saved.pingUseGlobalConditions());
            tab.setPingCondAlways(saved.pingCondAlways());
            tab.setPingCondActiveTab(saved.pingCondActiveTab());
            tab.setPingCondUnfocusedTab(saved.pingCondUnfocusedTab());
            tab.setPingCondGameUnfocused(saved.pingCondGameUnfocused());
            tab.setPingCondAfk(saved.pingCondAfk());
            tab.setPingCondRequireSilence(saved.pingCondRequireSilence());
            tab.setPingCooldownSec(saved.pingCooldownSec());
            tab.setPingSilenceThresholdSec(saved.pingSilenceThresholdSec());
            tab.setPingAfkThresholdSec(saved.pingAfkThresholdSec());
        }

        ChatManager.getTabs().add(tab);
        ChatManager.refilterTab(tab);

        TabHandle handle = new TabHandle(fullId, tab);
        handles.put(name, handle);
        return handle;
    }

    // ------------------------------------------------------------------
    // Tab management
    // ------------------------------------------------------------------

    public void unregisterTab(String name) {
        TabHandle handle = handles.remove(name);
        if (handle == null) return;
        ChatManager.getTabs().remove(handle.getTab());
        MultichatClient.saveTabsToConfig();
        handle.fireRemoved();
    }

    /** Called by MultiChatApi when a tab is removed externally (e.g. player deletes it). */
    void notifyTabRemoved(ChatTab tab) {
        handles.entrySet().removeIf(e -> {
            if (e.getValue().getTab() == tab) {
                e.getValue().fireRemoved();
                return true;
            }
            return false;
        });
    }

    public TabHandle getTab(String name) { return handles.get(name); }

    public List<String> getTabIds() {
        return handles.values().stream().map(TabHandle::getId).toList();
    }

    public String getModId() { return modId; }

    // ------------------------------------------------------------------
    // Panel access
    // ------------------------------------------------------------------

    public List<ChatPanel> getPanels() {
        return java.util.Collections.unmodifiableList(ChatManager.getAllPanels());
    }

    /** Creates a new blank, centered panel and returns a handle to configure it. */
    public PanelHandle createPanel(String name) {
        return new PanelHandle(ChatManager.createPanel(name));
    }

    public ChatPanel getPanel(int panelId) {
        return ChatManager.getAllPanels().stream().filter(p -> p.id() == panelId).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Set<Integer> allPanelIds() {
        Set<Integer> ids = new LinkedHashSet<>();
        for (ChatPanel p : ChatManager.getAllPanels()) ids.add(p.id());
        return ids;
    }

    private static ChatTab findSaved(String fullId) {
        if (MultichatClient.CONFIG == null) return null;
        return MultichatClient.CONFIG.tabs.stream()
            .filter(t -> t.type() == TabType.API && fullId.equals(t.apiId()))
            .findFirst().orElse(null);
    }
}
