package net.mobilelize.multichat.client.config;

import net.mobilelize.fabriclib.config.LzConfig;
import net.mobilelize.multichat.client.ChatPanel;
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

    // Tab appearance defaults (per-panel overrides these when >= 0)
    public double tabBgOpacity = -1;
    public double tabFgOpacity = -1;

    // General
    public boolean applySettingsOnRefilter = true;
    public boolean modDisabled             = false;
    public boolean showIndicators          = true;
    public boolean selectionEnabled          = true;
    public boolean selectionFreezeChat       = true;
    public boolean selectionCtrlSkipsClicks  = true;
    public boolean doubleClickSelectEnabled  = true;
    public boolean doubleClickNameSelect     = true;
    public String  timestampFormat         = "&d[&6HH&d:&6mm&d:&6ss&d]";
    public String  collapseCounterFormat  = "&e(x{count})";
    public boolean arrowKeySwitchTabs      = true;
    public boolean flashOnTabSwitch        = false;
    public boolean flashOnMouseTabSwitch        = false;
    public boolean switchFocusedPanelOnTabClick = true;
    public boolean flashOnPanelFocus       = true;
    public boolean showContextDisplay      = true;
    public boolean earlyMessageCapture    = true;
    public boolean panelContextEnabled    = true;
    public boolean logApiMessages         = true;
    public boolean notesAlwaysOpen        = false;

    // Quick Actions
    public boolean quickActionPasteToChat        = false;
    public boolean quickActionKeybindEnabled     = true;
    public int     quickActionKeybindKey         = 81; // GLFW_KEY_Q
    public boolean quickActionKeybindRequireCtrl = true;
    public java.util.List<QuickActionConfig> quickActions = newDefaultQuickActions();

    private static java.util.List<QuickActionConfig> newDefaultQuickActions() {
        var list = new java.util.ArrayList<QuickActionConfig>();
        var def  = new QuickActionConfig();
        def.name     = "Tell Hello";
        def.template = "/tell {selected} Hello";
        list.add(def);
        return list;
    }

    // Search
    public boolean      searchKeybindEnabled      = true;
    public int          searchKeybindKey          = 70;  // GLFW_KEY_F
    public boolean      searchKeybindRequireCtrl  = true;
    public SearchMode   searchDefaultMode         = SearchMode.CONTAINS;
    public boolean      searchDefaultCase         = false;
    public boolean      searchDefaultLive         = true;
    public boolean      searchDefaultBadge        = false;
    public boolean      searchDefaultTimestamps        = true;
    public boolean      searchDefaultCollapseIdentical = false;

    // Pings
    public boolean  pingEnabled             = true;
    public String   pingSound               = "minecraft:entity.experience_orb.pickup";
    public float    pingVolume              = 1.0f;
    public float    pingPitch               = 1.0f;
    public boolean  pingCondAlways          = false;
    public boolean  pingCondActiveTab       = false;
    public boolean  pingCondUnfocusedTab    = true;
    public boolean  pingCondGameUnfocused   = false;
    public boolean  pingCondAfk             = false;
    public boolean  pingCondRequireSilence  = false;
    public int      pingCooldownSec             = 2;
    public int      pingSilenceThresholdSec     = 30;
    public int      pingAfkThresholdSec         = 120;

    public java.util.Set<String> hiddenContextMenuItems = new java.util.HashSet<>();

    public List<TabConfig>     tabs     = new ArrayList<>();
    public List<ApiTabConfig>  apiTabs  = new ArrayList<>();
    public List<NoteTabConfig> noteTabs = new ArrayList<>();
    public String activeNoteTabName = "";

    // visibleOnPanels for the two fixed system tabs
    public List<Integer> allTabPanels     = new java.util.ArrayList<>(java.util.List.of(0, 1));
    public List<Integer> vanillaTabPanels = new java.util.ArrayList<>(java.util.List.of(0));

    // All-tab raw list settings
    public boolean allTabRawList      = true;
    public boolean allTabRefilterRaw  = true;
    public boolean allTabSearchRaw    = true;

    public static class PanelConfig {
        public int     id                  = 0;
        public String  name                = "";
        public boolean enabled             = true;
        public ChatPanel.Anchor anchor      = ChatPanel.Anchor.LEFT;
        public int     margin              = 0;
        public int     activeTab           = 0;
        public String  activeTabName       = "";
        public boolean showOnlyWhenFocused = false;
        public java.util.Map<String, Integer> noteScrollPositions = new java.util.HashMap<>();
        public boolean showBadges          = true;
        public boolean showTabs            = true;
        public boolean tabsAtTop           = false;
        public float   customX             = 0f;
        public float   customY             = 0f;
        public int     customWidth                = -1;
        public int     customHeightLines          = -1;
        public int     customUnfocusedHeightLines = -1;
        public boolean lockUnfocusedToFocused     = false;
        public float   customScale                = -1f;
        public int     bgColor             = 0;
        public double  customBgOpacity     = -1;
        public double  customChatOpacity   = -1;
        public double  tabBgOpacity        = -1;
        public double  tabFgOpacity        = -1;
        public String  panelContextTabName  = "";
        public boolean useActiveTabContext  = false;
    }

    public static class TabConfig {
        public String name = "";
        public boolean differenceMode       = false;
        public int     differenceLayerIndex = 0;
        public boolean differenceException  = false;
        public boolean showBadge         = true;
        public boolean showTimestamps    = false;
        public int     tabColor          = 0;
        public int     msgBgColor        = 0;
        public boolean collapseRepeating       = false;
        public boolean collapseMatchFormatting = false;
        public int     messageCap        = 0;
        public List<Integer> visibleOnPanels = new java.util.ArrayList<>();
        public WhitelistMode whitelistMode = WhitelistMode.ANY;
        public WhitelistMode blacklistMode = WhitelistMode.ANY;
        public String contextPrefix = "";
        public boolean contextCommandMode = true;
        public List<RuleConfig> whitelist = new ArrayList<>();
        public List<RuleConfig> blacklist = new ArrayList<>();
        // Pings — empty/negative means inherit from global
        public boolean  pingEnabled              = false;
        public String   pingSound                = "";
        public float    pingVolume               = -1f;
        public float    pingPitch                = -1f;
        public boolean  pingUseGlobalConditions  = true;
        public boolean  pingCondAlways           = false;
        public boolean  pingCondActiveTab        = false;
        public boolean  pingCondUnfocusedTab     = false;
        public boolean  pingCondGameUnfocused    = false;
        public boolean  pingCondAfk              = false;
        public boolean  pingCondRequireSilence   = false;
        public int      pingCooldownSec              = -1;
        public int      pingSilenceThresholdSec      = -1;
        public int      pingAfkThresholdSec          = -1;
    }

    public static class ApiTabConfig {
        public String id = "";
        public String displayName = "";
        public List<Integer> visibleOnPanels = new java.util.ArrayList<>();
        public int color = 0;
        public WhitelistMode whitelistMode = WhitelistMode.ANY;
        public WhitelistMode blacklistMode = WhitelistMode.ANY;
        public String contextPrefix = "";
        public boolean contextCommandMode = true;
        public List<RuleConfig> whitelist = new ArrayList<>();
        public List<RuleConfig> blacklist = new ArrayList<>();
        // Pings — same inherit semantics as TabConfig
        public boolean  pingEnabled              = false;
        public String   pingSound                = "";
        public float    pingVolume               = -1f;
        public float    pingPitch                = -1f;
        public boolean  pingUseGlobalConditions  = true;
        public boolean  pingCondAlways           = false;
        public boolean  pingCondActiveTab        = false;
        public boolean  pingCondUnfocusedTab     = false;
        public boolean  pingCondGameUnfocused    = false;
        public boolean  pingCondAfk              = false;
        public boolean  pingCondRequireSilence   = false;
        public int      pingCooldownSec          = -1;
        public int      pingSilenceThresholdSec  = -1;
        public int      pingAfkThresholdSec      = -1;
    }

    public static class QuickActionConfig {
        public String name     = "";
        public String template = "";
    }

    public static class RuleConfig {
        public FilterType      type      = FilterType.CONTAINS;
        public String          value     = "";
        public IndicatorFilter indicator = IndicatorFilter.ANY;
    }

    public static class NoteEntryConfig {
        public String textJson = "";
        public int    tick     = 0;
    }

    public static class NoteTabConfig {
        public String              name            = "";
        public List<Integer>       visibleOnPanels = new java.util.ArrayList<>();
        public int                 tabColor        = 0;
        public List<NoteEntryConfig> entries       = new ArrayList<>();
    }

    public static int pct(double v)    { return v < 0 ? -1 : Math.min(100, Math.max(0, (int) Math.round(v * 100))); }
    public static int pct(float v)     { return v < 0 ? -1 : Math.min(100, Math.max(0, Math.round(v * 100))); }
    public static double fromPct(int v) { return v < 0 ? -1.0 : v / 100.0; }

    public static int pingVolPct(float v)        { return v < 0 ? -1 : Math.min(200, Math.max(0, Math.round(v * 100))); }
    public static float fromPingVolPct(int v)    { return v < 0 ? -1.0f : v / 100.0f; }
    public static int pingPitchPct(float v)      { return v < 0 ? -1 : Math.min(200, Math.max(50, Math.round(v * 100))); }
    public static float fromPingPitchPct(int v)  { return v < 0 ? -1.0f : v / 100.0f; }
}
