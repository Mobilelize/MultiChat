package net.mobilelize.multichat.client;

import net.fabricmc.api.ClientModInitializer;
import net.mobilelize.fabriclib.config.LzConfig;
import net.mobilelize.multichat.client.command.MultiChatCommand;
import net.mobilelize.multichat.client.config.MultiChatConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.TabConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.RuleConfig;
import net.mobilelize.multichat.client.filter.FilterRule;
import net.mobilelize.multichat.client.filter.FilterType;
import net.mobilelize.multichat.client.filter.MessageType;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.ArrayList;
import java.util.List;

public class MultichatClient implements ClientModInitializer {

    public static MultiChatConfig CONFIG;

    @Override
    public void onInitializeClient() {
        CONFIG = LzConfig.load(MultiChatConfig.class);

        ChatRenderer.PANELS.get(1).enabled              = CONFIG.rightPanelEnabled;
        ChatRenderer.PANELS.get(1).showOnlyWhenFocused  = CONFIG.rightPanelShowOnlyWhenFocused;

        restoreTabsFromConfig();
        MultiChatCommand.register();
    }

    // ------------------------------------------------------------------
    // Tab persistence — called by command handlers after mutations
    // ------------------------------------------------------------------

    public static void saveTabsToConfig() {
        CONFIG.leftTabs  = serializeTabs(ChatRenderer.PANELS.get(0));
        CONFIG.rightTabs = serializeTabs(ChatRenderer.PANELS.get(1));
        CONFIG.save();
    }

    private static void restoreTabsFromConfig() {
        restoreTabsForPanel(ChatRenderer.PANELS.get(0), CONFIG.leftTabs);
        restoreTabsForPanel(ChatRenderer.PANELS.get(1), CONFIG.rightTabs);
    }

    private static void restoreTabsForPanel(ChatPanel panel, TabConfig[] configs) {
        panel.tabs.removeIf(t -> t.type == TabType.FILTERED);
        for (TabConfig tc : configs) {
            ChatTab tab = new ChatTab(tc.name, TabType.FILTERED);
            tab.differenceMode = tc.differenceMode;
            for (RuleConfig rc : tc.whitelist) {
                FilterRule rule = parseRule(rc);
                if (rule != null) tab.filterRules.whitelist.add(rule);
            }
            for (RuleConfig rc : tc.blacklist) {
                FilterRule rule = parseRule(rc);
                if (rule != null) tab.filterRules.blacklist.add(rule);
            }
            panel.tabs.add(tab);
        }
    }

    private static TabConfig[] serializeTabs(ChatPanel panel) {
        List<TabConfig> out = new ArrayList<>();
        for (ChatTab tab : panel.tabs) {
            if (tab.type != TabType.FILTERED) continue;
            TabConfig tc = new TabConfig();
            tc.name           = tab.name;
            tc.differenceMode = tab.differenceMode;
            tc.whitelist      = serializeRules(tab.filterRules.whitelist);
            tc.blacklist      = serializeRules(tab.filterRules.blacklist);
            out.add(tc);
        }
        return out.toArray(new TabConfig[0]);
    }

    private static RuleConfig[] serializeRules(List<FilterRule> rules) {
        return rules.stream().map(r -> {
            RuleConfig rc = new RuleConfig();
            rc.type  = r.type.name().toLowerCase();
            rc.value = r.value;
            if (r.messageType != null) rc.messageType = r.messageType.name().toLowerCase();
            return rc;
        }).toArray(RuleConfig[]::new);
    }

    private static FilterRule parseRule(RuleConfig rc) {
        FilterType ft = MultiChatCommand.parseFilterType(rc.type);
        if (ft == null) return null;
        if (ft == FilterType.MESSAGE_TYPE) {
            MessageType mt = MultiChatCommand.parseMessageType(rc.messageType);
            return mt != null ? new FilterRule(ft, rc.value, mt) : null;
        }
        return new FilterRule(ft, rc.value);
    }
}
