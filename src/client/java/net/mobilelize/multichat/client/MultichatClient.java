package net.mobilelize.multichat.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.mobilelize.fabriclib.config.LzConfig;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.command.MultiChatCommand;
import net.mobilelize.multichat.client.config.MultiChatConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.ApiTabConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.PanelConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.TabConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.RuleConfig;
import net.mobilelize.multichat.client.filter.FilterRule;
import net.mobilelize.multichat.client.filter.MessageRouter;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.ArrayList;
import java.util.List;

public class MultichatClient implements ClientModInitializer {

    public static MultiChatConfig CONFIG;

    @Override
    public void onInitializeClient() {
        CONFIG = LzConfig.load(MultiChatConfig.class);
        restorePanelsFromConfig();
        restoreTabsFromConfig();
        MultiChatCommand.register();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveTabsToConfig());

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || CONFIG.modDisabled || !CONFIG.earlyMessageCapture) return;
            MessageRouter.offerRaw(LzText.wrapMc(message));
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> {
            if (CONFIG.modDisabled || !CONFIG.earlyMessageCapture) return;
            MessageRouter.offerRaw(LzText.wrapMc(message));
        });
    }

    // ------------------------------------------------------------------
    // Save — called on client stop and by command handlers after mutations
    // ------------------------------------------------------------------

    public static void saveTabsToConfig() {
        // Panels
        List<PanelConfig> panelOut = new ArrayList<>();
        for (ChatPanel panel : ChatRenderer.PANELS) {
            PanelConfig pc      = new PanelConfig();
            pc.id                  = panel.id;
            pc.name                = panel.name;
            pc.enabled             = panel.enabled;
            pc.anchor              = panel.anchor.name();
            pc.margin              = panel.margin;
            pc.activeTab           = panel.activeTab;
            pc.showOnlyWhenFocused = panel.showOnlyWhenFocused;
            pc.showBadges          = panel.showBadges;
            pc.showTabs            = panel.showTabs;
            pc.tabsAtTop           = panel.tabsAtTop;
            pc.customX             = panel.customX;
            pc.customY             = panel.customY;
            pc.customWidth         = panel.customWidth;
            pc.customHeightLines   = panel.customHeightLines;
            pc.customScale         = panel.customScale;
            pc.bgColor             = panel.bgColor;
            pc.customBgOpacity     = panel.customBgOpacity;
            pc.customChatOpacity   = panel.customChatOpacity;
            panelOut.add(pc);
        }
        CONFIG.panels = panelOut;

        // Tabs
        List<TabConfig> out = new ArrayList<>();
        for (ChatTab tab : ChatRenderer.TABS) {
            if (tab.type != TabType.FILTERED) continue;
            TabConfig tc          = new TabConfig();
            tc.name               = tab.name;
            tc.differenceMode     = tab.differenceMode;
            tc.showBadge          = tab.showBadge;
            tc.showTimestamps     = tab.showTimestamps;
            tc.tabColor           = tab.tabColor;
            tc.msgBgColor         = tab.msgBgColor;
            tc.visibleOnPanels    = new java.util.ArrayList<>(tab.visibleOnPanels);
            tc.collapseRepeating        = tab.collapseRepeating;
            tc.collapseMatchFormatting  = tab.collapseMatchFormatting;
            tc.messageCap         = tab.messageCap;
            tc.whitelistMode      = tab.filterRules.whitelistMode;
            tc.contextPrefix      = tab.contextPrefix;
            tc.contextCommandMode = tab.contextCommandMode;
            tc.whitelist          = serializeRules(tab.filterRules.whitelist);
            tc.blacklist          = serializeRules(tab.filterRules.blacklist);
            out.add(tc);
        }
        CONFIG.tabs = out;

        List<ApiTabConfig> apiOut = new ArrayList<>();
        for (ChatTab tab : ChatRenderer.TABS) {
            if (tab.type != TabType.API || tab.apiId == null) continue;
            ApiTabConfig atc       = new ApiTabConfig();
            atc.id                 = tab.apiId;
            atc.displayName        = tab.name;
            atc.visibleOnPanels    = new java.util.ArrayList<>(tab.visibleOnPanels);
            atc.color              = tab.tabColor;
            atc.whitelistMode      = tab.filterRules.whitelistMode;
            atc.contextPrefix      = tab.contextPrefix;
            atc.contextCommandMode = tab.contextCommandMode;
            atc.whitelist          = serializeRules(tab.filterRules.whitelist);
            atc.blacklist          = serializeRules(tab.filterRules.blacklist);
            apiOut.add(atc);
        }
        CONFIG.apiTabs = apiOut;

        for (ChatTab tab : ChatRenderer.TABS) {
            if (tab.type == TabType.ALL)
                CONFIG.allTabPanels = new ArrayList<>(tab.visibleOnPanels);
            else if (tab.type == TabType.VANILLA)
                CONFIG.vanillaTabPanels = new ArrayList<>(tab.visibleOnPanels);
        }

        CONFIG.contextTabName = ChatRenderer.contextTab != null ? ChatRenderer.contextTab.name : "";
        CONFIG.save();
    }

    // ------------------------------------------------------------------
    // Restore
    // ------------------------------------------------------------------

    private static void restorePanelsFromConfig() {
        if (CONFIG.panels.isEmpty()) return;

        // Panel 0 — always LEFT (primary, enabled)
        if (!CONFIG.panels.isEmpty()) {
            applyPanelConfig(ChatRenderer.LEFT, CONFIG.panels.getFirst());
            ChatRenderer.LEFT.primary = true;
            ChatRenderer.LEFT.enabled = true;
            ChatRenderer.LEFT.id      = 0;
        }

        // Panel 1 — RIGHT
        if (CONFIG.panels.size() >= 2) {
            applyPanelConfig(ChatRenderer.RIGHT, CONFIG.panels.get(1));
            ChatRenderer.RIGHT.id = 1;
        }

        // Extra panels beyond the two defaults
        for (int i = 2; i < CONFIG.panels.size(); i++) {
            ChatPanel panel = new ChatPanel();
            applyPanelConfig(panel, CONFIG.panels.get(i));
            ChatRenderer.PANELS.add(panel);
        }
    }

    private static void applyPanelConfig(ChatPanel panel, PanelConfig pc) {
        panel.id   = pc.id;
        panel.name = pc.name.isEmpty() ? panel.name : pc.name;
        panel.enabled = pc.enabled;
        try { panel.anchor = ChatPanel.Anchor.valueOf(pc.anchor); }
        catch (Exception e) { panel.anchor = ChatPanel.Anchor.LEFT; }
        panel.margin              = pc.margin;
        panel.activeTab           = pc.activeTab;
        panel.showOnlyWhenFocused = pc.showOnlyWhenFocused;
        panel.showBadges          = pc.showBadges;
        panel.showTabs            = pc.showTabs;
        panel.tabsAtTop           = pc.tabsAtTop;
        panel.customX             = pc.customX;
        panel.customY             = pc.customY;
        panel.customWidth         = pc.customWidth;
        panel.customHeightLines   = pc.customHeightLines;
        panel.customScale         = pc.customScale;
        panel.bgColor             = pc.bgColor;
        panel.customBgOpacity     = pc.customBgOpacity;
        panel.customChatOpacity   = pc.customChatOpacity;
    }

    private static void restoreTabsFromConfig() {
        for (ChatTab tab : ChatRenderer.TABS) {
            if (tab.type == TabType.ALL) {
                tab.visibleOnPanels.clear();
                tab.visibleOnPanels.addAll(CONFIG.allTabPanels);
            } else if (tab.type == TabType.VANILLA) {
                tab.visibleOnPanels.clear();
                tab.visibleOnPanels.addAll(CONFIG.vanillaTabPanels);
            }
        }

        ChatRenderer.TABS.removeIf(t -> t.type == TabType.FILTERED);
        for (TabConfig tc : CONFIG.tabs) {
            ChatTab tab = new ChatTab(tc.name, TabType.FILTERED, new java.util.LinkedHashSet<>(tc.visibleOnPanels));
            tab.differenceMode            = tc.differenceMode;
            tab.showBadge                 = tc.showBadge;
            tab.showTimestamps            = tc.showTimestamps;
            tab.tabColor                  = tc.tabColor;
            tab.msgBgColor                = tc.msgBgColor;
            tab.collapseRepeating         = tc.collapseRepeating;
            tab.collapseMatchFormatting   = tc.collapseMatchFormatting;
            tab.messageCap                = tc.messageCap;
            tab.filterRules.whitelistMode = tc.whitelistMode != null ? tc.whitelistMode
                : net.mobilelize.multichat.client.filter.WhitelistMode.ANY;
            tab.contextPrefix             = tc.contextPrefix != null ? tc.contextPrefix : "";
            tab.contextCommandMode        = tc.contextCommandMode;
            for (RuleConfig rc : tc.whitelist) {
                if (rc.type != null) tab.filterRules.whitelist.add(new FilterRule(rc.type, rc.value,
                    rc.indicator != null ? rc.indicator : net.mobilelize.multichat.client.filter.IndicatorFilter.ANY));
            }
            for (RuleConfig rc : tc.blacklist) {
                if (rc.type != null) tab.filterRules.blacklist.add(new FilterRule(rc.type, rc.value,
                    rc.indicator != null ? rc.indicator : net.mobilelize.multichat.client.filter.IndicatorFilter.ANY));
            }
            ChatRenderer.TABS.add(tab);
        }

        if (!CONFIG.contextTabName.isEmpty()) {
            ChatRenderer.contextTab = ChatRenderer.TABS.stream()
                .filter(t -> t.name.equals(CONFIG.contextTabName))
                .findFirst().orElse(null);
        }

        // Clamp each panel's activeTab to the valid visible-tab range
        for (ChatPanel panel : ChatRenderer.PANELS) {
            int size = panel.getVisibleTabs().size();
            panel.activeTab = size > 0 ? Math.min(panel.activeTab, size - 1) : 0;
        }
    }

    private static List<RuleConfig> serializeRules(List<FilterRule> rules) {
        List<RuleConfig> out = new ArrayList<>();
        for (FilterRule r : rules) {
            RuleConfig rc = new RuleConfig();
            rc.type      = r.type;
            rc.value     = r.value;
            rc.indicator = r.indicator;
            out.add(rc);
        }
        return out;
    }
}
