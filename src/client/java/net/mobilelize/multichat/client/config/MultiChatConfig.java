package net.mobilelize.multichat.client.config;

import net.mobilelize.fabriclib.config.LzConfig;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.client.filter.FilterType;
import net.mobilelize.multichat.client.filter.IndicatorFilter;
import net.mobilelize.multichat.client.filter.WhitelistMode;

import java.util.ArrayList;
import java.util.List;

public class MultiChatConfig extends LzConfig {

    @Override
    public String directory() { return "config/multichat"; }

    public String contextTabName = "";
    public boolean leftPanelShowBadges = true;
    public boolean leftPanelShowTabs   = true;
    public boolean rightPanelEnabled   = false;
    public boolean rightPanelShowOnlyWhenFocused = false;
    public boolean rightPanelShowBadges = true;
    public boolean rightPanelShowTabs   = true;

    // General
    public boolean showIndicators          = true;
    public boolean selectionEnabled        = true;
    public boolean selectionCtrlSkipsClicks = true;
    public String  timestampFormat         = "&d[&6HH&d:&6mm&d:&6ss&d]";

    // Search
    public boolean      searchKeybindEnabled      = true;
    public int          searchKeybindKey          = 70;  // GLFW_KEY_F
    public boolean      searchKeybindRequireCtrl  = true;
    public boolean      searchDefaultRegex        = false;
    public boolean      searchDefaultCase         = false;
    public boolean      searchDefaultLive         = true;
    public TabVisibility searchDefaultVisibility  = TabVisibility.BOTH;

    public List<TabConfig> tabs = new ArrayList<>();

    public static class TabConfig {
        public String name = "";
        public boolean differenceMode    = false;
        public boolean showBadge         = true;
        public boolean showTimestamps    = false;
        public boolean collapseRepeating = false;
        public int     messageCap        = 0;
        public TabVisibility visibility = TabVisibility.BOTH;
        public WhitelistMode whitelistMode = WhitelistMode.ANY;
        public String contextPrefix = "";
        public boolean contextCommandMode = true;
        public List<RuleConfig> whitelist = new ArrayList<>();
        public List<RuleConfig> blacklist = new ArrayList<>();
    }

    public static class RuleConfig {
        public FilterType      type      = FilterType.CONTAINS;
        public String          value     = "";
        public IndicatorFilter indicator = IndicatorFilter.ANY;
    }
}
