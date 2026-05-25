package net.mobilelize.multichat.api;

import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.client.config.MultiChatConfig.ApiTabConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.RuleConfig;
import net.mobilelize.multichat.client.filter.FilterRule;
import net.mobilelize.multichat.client.filter.IndicatorFilter;
import net.mobilelize.multichat.client.filter.MessageRouter;
import net.mobilelize.multichat.client.filter.WhitelistMode;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.LinkedHashMap;
import java.util.Map;

public class MultiChatMod {

    private final String modId;
    private final Map<String, TabHandle> handles = new LinkedHashMap<>();

    MultiChatMod(String modId) {
        this.modId = modId;
    }

    /**
     * Register a tab owned by this mod. If already registered, returns the existing handle.
     * The full ID will be {@code modId:name}. If saved config exists for this ID, user
     * customizations (visibility, color, filter rules) are restored automatically.
     *
     * @param name        short name within this mod's namespace (no colon)
     * @param displayName label shown in the tab bar
     * @param visibility  default panel visibility
     * @param color       default tab color (0 = default gray)
     */
    public TabHandle registerTab(String name, String displayName) {
        return registerTab(name, displayName, TabVisibility.BOTH, 0);
    }

    public TabHandle registerTab(String name, String displayName, TabVisibility visibility) {
        return registerTab(name, displayName, visibility, 0);
    }

    public TabHandle registerTab(String name, String displayName, int color) {
        return registerTab(name, displayName, TabVisibility.BOTH, color);
    }

    public TabHandle registerTab(String name, String displayName, TabVisibility visibility, int color) {
        if (handles.containsKey(name)) return handles.get(name);

        String fullId = modId + ":" + name;

        ApiTabConfig saved = findSaved(fullId);

        String resolvedName = (saved != null && saved.displayName != null && !saved.displayName.isEmpty())
            ? saved.displayName : displayName;
        java.util.Set<Integer> resolvedPanels;
        if (saved != null && !saved.visibleOnPanels.isEmpty()) {
            resolvedPanels = new java.util.LinkedHashSet<>(saved.visibleOnPanels);
        } else {
            resolvedPanels = TabVisibility.toPanelIds(visibility);
        }
        ChatTab tab = new ChatTab(resolvedName, TabType.API, resolvedPanels);
        tab.apiId    = fullId;
        tab.tabColor = saved != null ? saved.color : (color & 0xFFFFFF);

        if (saved != null) {
            tab.filterRules.whitelistMode = saved.whitelistMode != null ? saved.whitelistMode : WhitelistMode.ANY;
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

    /** Removes the tab from the panel and saves config. */
    public void unregisterTab(String name) {
        TabHandle handle = handles.remove(name);
        if (handle == null) return;
        ChatRenderer.TABS.remove(handle.getTab());
        MultichatClient.saveTabsToConfig();
    }

    public TabHandle getTab(String name) {
        return handles.get(name);
    }

    /** All full tab IDs ({@code "modid:name"}) registered by this mod. */
    public java.util.List<String> getTabIds() {
        return handles.values().stream().map(TabHandle::getId).toList();
    }

    public String getModId() { return modId; }

    private static ApiTabConfig findSaved(String fullId) {
        if (MultichatClient.CONFIG == null) return null;
        for (ApiTabConfig atc : MultichatClient.CONFIG.apiTabs)
            if (fullId.equals(atc.id)) return atc;
        return null;
    }
}
