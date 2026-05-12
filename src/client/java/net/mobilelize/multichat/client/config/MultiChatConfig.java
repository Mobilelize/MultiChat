package net.mobilelize.multichat.client.config;

import net.mobilelize.fabriclib.config.LzConfig;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.client.filter.FilterType;

import java.util.ArrayList;
import java.util.List;

public class MultiChatConfig extends LzConfig {

    @Override
    public String directory() { return "config/multichat"; }

    public String contextTabName = "";
    public boolean leftPanelShowBadges = true;
    public boolean rightPanelEnabled = false;
    public boolean rightPanelShowOnlyWhenFocused = false;
    public boolean rightPanelShowBadges = true;

    public List<TabConfig> tabs = new ArrayList<>();

    public static class TabConfig {
        public String name = "";
        public boolean differenceMode = false;
        public boolean showBadge = true;
        public TabVisibility visibility = TabVisibility.BOTH;
        public String contextPrefix = "";
        public boolean contextCommandMode = true;
        public List<RuleConfig> whitelist = new ArrayList<>();
        public List<RuleConfig> blacklist = new ArrayList<>();
    }

    public static class RuleConfig {
        public FilterType type = FilterType.CONTAINS;
        public String value = "";
    }
}
