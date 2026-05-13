package net.mobilelize.multichat.client;

import net.fabricmc.api.ClientModInitializer;
import net.mobilelize.fabriclib.config.LzConfig;
import net.mobilelize.multichat.client.command.MultiChatCommand;
import net.mobilelize.multichat.client.config.MultiChatConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.TabConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.RuleConfig;
import net.mobilelize.multichat.client.filter.FilterRule;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.ArrayList;
import java.util.List;

public class MultichatClient implements ClientModInitializer {

    public static MultiChatConfig CONFIG;

    @Override
    public void onInitializeClient() {
        CONFIG = LzConfig.load(MultiChatConfig.class);

        ChatRenderer.LEFT.showBadges           = CONFIG.leftPanelShowBadges;
        ChatRenderer.LEFT.showTabs             = CONFIG.leftPanelShowTabs;
        ChatRenderer.RIGHT.enabled             = CONFIG.rightPanelEnabled;
        ChatRenderer.RIGHT.showOnlyWhenFocused = CONFIG.rightPanelShowOnlyWhenFocused;
        ChatRenderer.RIGHT.showBadges          = CONFIG.rightPanelShowBadges;
        ChatRenderer.RIGHT.showTabs            = CONFIG.rightPanelShowTabs;

        restoreTabsFromConfig();
        MultiChatCommand.register();
    }

    // ------------------------------------------------------------------
    // Tab persistence — called by command handlers after mutations
    // ------------------------------------------------------------------

    public static void saveTabsToConfig() {
        List<TabConfig> out = new ArrayList<>();
        for (ChatTab tab : ChatRenderer.TABS) {
            if (tab.type != TabType.FILTERED) continue;
            TabConfig tc = new TabConfig();
            tc.name           = tab.name;
            tc.differenceMode = tab.differenceMode;
            tc.showBadge      = tab.showBadge;
            tc.showTimestamps = tab.showTimestamps;
            tc.visibility     = tab.visibility;
            tc.collapseRepeating  = tab.collapseRepeating;
            tc.messageCap         = tab.messageCap;
            tc.whitelistMode      = tab.filterRules.whitelistMode;
            tc.contextPrefix      = tab.contextPrefix;
            tc.contextCommandMode = tab.contextCommandMode;
            tc.whitelist          = serializeRules(tab.filterRules.whitelist);
            tc.blacklist          = serializeRules(tab.filterRules.blacklist);
            out.add(tc);
        }
        CONFIG.tabs = out;
        CONFIG.contextTabName = ChatRenderer.contextTab != null ? ChatRenderer.contextTab.name : "";
        CONFIG.save();
    }

    private static void restoreTabsFromConfig() {
        ChatRenderer.TABS.removeIf(t -> t.type == TabType.FILTERED);
        for (TabConfig tc : CONFIG.tabs) {
            ChatTab tab = new ChatTab(tc.name, TabType.FILTERED,
                tc.visibility != null ? tc.visibility : TabVisibility.BOTH);
            tab.differenceMode      = tc.differenceMode;
            tab.showBadge           = tc.showBadge;
            tab.showTimestamps      = tc.showTimestamps;
            tab.collapseRepeating           = tc.collapseRepeating;
            tab.messageCap                  = tc.messageCap;
            tab.filterRules.whitelistMode   = tc.whitelistMode != null ? tc.whitelistMode : net.mobilelize.multichat.client.filter.WhitelistMode.ANY;
            tab.contextPrefix       = tc.contextPrefix != null ? tc.contextPrefix : "";
            tab.contextCommandMode  = tc.contextCommandMode;
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
