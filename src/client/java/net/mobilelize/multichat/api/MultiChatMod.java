package net.mobilelize.multichat.api;

import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.config.MultiChatConfig.ApiTabConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.RuleConfig;
import net.mobilelize.multichat.client.filter.FilterRule;
import net.mobilelize.multichat.client.filter.IndicatorFilter;
import net.mobilelize.multichat.client.filter.MessageRouter;
import net.mobilelize.multichat.client.filter.WhitelistMode;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiChatMod {

    private final String modId;
    private final Map<String, TabHandle> handles = new LinkedHashMap<>();

    MultiChatMod(String modId) {
        this.modId = modId;
    }

    // ------------------------------------------------------------------
    // Tab registration
    // ------------------------------------------------------------------

    /** Register a tab visible on all panels. Use {@link TabHandle} setters to configure further. */
    public TabHandle registerTab(String name, String displayName) {
        return registerTabInternal(name, displayName, allPanelIds());
    }

    /** Register a tab visible only on the specified panel IDs (0 = left, 1 = right, …). */
    public TabHandle registerTab(String name, String displayName, int... panelIds) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (int id : panelIds) ids.add(id);
        return registerTabInternal(name, displayName, ids);
    }

    private TabHandle registerTabInternal(String name, String displayName, Set<Integer> defaultPanels) {
        if (handles.containsKey(name)) return handles.get(name);

        String fullId = modId + ":" + name;
        ApiTabConfig saved = findSaved(fullId);

        String resolvedName = (saved != null && saved.displayName != null && !saved.displayName.isEmpty())
            ? saved.displayName : displayName;

        Set<Integer> resolvedPanels = (saved != null && !saved.visibleOnPanels.isEmpty())
            ? new LinkedHashSet<>(saved.visibleOnPanels)
            : defaultPanels;

        ChatTab tab = new ChatTab(resolvedName, TabType.API, resolvedPanels);
        tab.apiId    = fullId;
        tab.tabColor = saved != null ? saved.color : 0;

        if (saved != null) {
            tab.filterRules.whitelistMode = saved.whitelistMode != null ? saved.whitelistMode : WhitelistMode.ANY;
            tab.filterRules.blacklistMode = saved.blacklistMode != null ? saved.blacklistMode : WhitelistMode.ANY;
            tab.contextPrefix      = saved.contextPrefix != null ? saved.contextPrefix : "";
            tab.contextCommandMode = saved.contextCommandMode;
            for (RuleConfig rc : saved.whitelist)
                if (rc.type != null) tab.filterRules.whitelist.add(
                    new FilterRule(rc.type, rc.value, rc.indicator != null ? rc.indicator : IndicatorFilter.ANY));
            for (RuleConfig rc : saved.blacklist)
                if (rc.type != null) tab.filterRules.blacklist.add(
                    new FilterRule(rc.type, rc.value, rc.indicator != null ? rc.indicator : IndicatorFilter.ANY));
        }

        ChatRenderer.TABS.add(tab);
        MessageRouter.refilterTab(tab);

        TabHandle handle = new TabHandle(fullId, tab);
        handles.put(name, handle);
        return handle;
    }

    // ------------------------------------------------------------------
    // Tab management
    // ------------------------------------------------------------------

    /** Removes the tab and saves config. */
    public void unregisterTab(String name) {
        TabHandle handle = handles.remove(name);
        if (handle == null) return;
        ChatRenderer.TABS.remove(handle.getTab());
        MultichatClient.saveTabsToConfig();
    }

    public TabHandle getTab(String name) { return handles.get(name); }

    /** All full tab IDs ({@code "modid:name"}) registered by this mod. */
    public List<String> getTabIds() {
        return handles.values().stream().map(TabHandle::getId).toList();
    }

    public String getModId() { return modId; }

    // ------------------------------------------------------------------
    // Panel access
    // ------------------------------------------------------------------

    /** All currently registered panels (including disabled ones). */
    public List<ChatPanel> getPanels() {
        return java.util.Collections.unmodifiableList(ChatRenderer.PANELS);
    }

    /** Panel by ID, or {@code null} if not found. */
    public ChatPanel getPanel(int panelId) {
        return ChatRenderer.PANELS.stream().filter(p -> p.id == panelId).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Set<Integer> allPanelIds() {
        Set<Integer> ids = new LinkedHashSet<>();
        for (ChatPanel p : ChatRenderer.PANELS) ids.add(p.id);
        return ids;
    }

    private static ApiTabConfig findSaved(String fullId) {
        if (MultichatClient.CONFIG == null) return null;
        for (ApiTabConfig atc : MultichatClient.CONFIG.apiTabs)
            if (fullId.equals(atc.id)) return atc;
        return null;
    }
}
