package net.mobilelize.multichat.client.config;

import net.mobilelize.fabriclib.config.LzConfig;
import net.mobilelize.multichat.client.filter.FilterType;
import net.mobilelize.multichat.client.filter.IndicatorFilter;
import net.mobilelize.multichat.client.filter.WhitelistMode;
import net.mobilelize.multichat.client.search.SearchMode;

import java.util.ArrayList;
import java.util.List;

public class MultiChatConfig extends LzConfig {

    @Override
    public String directory() { return "config/multichat"; }

    public String            contextTabName = "";
    public List<PanelConfig> panels         = new ArrayList<>();

    // General
    public boolean applySettingsOnRefilter = true;
    public boolean modDisabled             = false;
    public boolean showIndicators          = true;
    public boolean selectionEnabled        = true;
    public boolean selectionCtrlSkipsClicks = true;
    public String  timestampFormat         = "&d[&6HH&d:&6mm&d:&6ss&d]";
    public String  collapseCounterFormat  = "&e(x{count})";
    public boolean arrowKeySwitchTabs      = true;
    public boolean arrowKeyCtrlForRight    = false;
    public boolean showContextDisplay      = true;

    // Search
    public boolean      searchKeybindEnabled      = true;
    public int          searchKeybindKey          = 70;  // GLFW_KEY_F
    public boolean      searchKeybindRequireCtrl  = true;
    public SearchMode   searchDefaultMode         = SearchMode.CONTAINS;
    public boolean      searchDefaultCase         = false;
    public boolean      searchDefaultLive         = true;
    public List<Integer> searchDefaultPanels = new java.util.ArrayList<>(List.of(0, 1));
    public boolean      searchDefaultTimestamps        = true;
    public boolean      searchDefaultCollapseIdentical = false;

    public List<TabConfig>    tabs    = new ArrayList<>();
    public List<ApiTabConfig> apiTabs = new ArrayList<>();

    public static class PanelConfig {
        public int     id                  = 0;
        public String  name                = "";
        public boolean enabled             = true;
        public String  anchor              = "LEFT";
        public int     margin              = 0;
        public int     activeTab           = 0;
        public boolean showOnlyWhenFocused = false;
        public boolean showBadges          = true;
        public boolean showTabs            = true;
        public boolean tabsAtTop           = false;
        public int     customX             = 0;
        public int     customY             = 0;
        public int     customWidth         = -1;
        public int     customHeightLines   = -1;
        public float   customScale         = -1f;
        public int     bgColor             = 0;
        public double  customBgOpacity     = -1;
        public double  customChatOpacity   = -1;
    }

    public static class TabConfig {
        public String name = "";
        public boolean differenceMode    = false;
        public boolean showBadge         = true;
        public boolean showTimestamps    = false;
        public int     tabColor          = 0;
        public int     msgBgColor        = 0;
        public boolean collapseRepeating       = false;
        public boolean collapseMatchFormatting = false;
        public int     messageCap        = 0;
        public List<Integer> visibleOnPanels = new java.util.ArrayList<>();
        public WhitelistMode whitelistMode = WhitelistMode.ANY;
        public String contextPrefix = "";
        public boolean contextCommandMode = true;
        public List<RuleConfig> whitelist = new ArrayList<>();
        public List<RuleConfig> blacklist = new ArrayList<>();
    }

    public static class ApiTabConfig {
        public String id = "";
        public String displayName = "";
        public List<Integer> visibleOnPanels = new java.util.ArrayList<>();
        public int color = 0;
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
