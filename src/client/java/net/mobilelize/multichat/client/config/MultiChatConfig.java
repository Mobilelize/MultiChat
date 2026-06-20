package net.mobilelize.multichat.client.config;

import net.mobilelize.fabriclib.config.LzConfig;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.NoteTab;
import net.mobilelize.multichat.client.search.SearchMode;

import java.util.*;

public class MultiChatConfig extends LzConfig {

    @Override
    public String directory() { return "config/multichat"; }

    // ── Panels / Tabs ─────────────────────────────────────────────────────────
    public List<ChatPanel> panels          = new ArrayList<>();
    public List<ChatTab>   tabs            = new ArrayList<>(); // FILTERED, API, ALL, VANILLA
    public List<NoteTab>   noteTabs        = new ArrayList<>(); // NOTES — separate for Gson subtype
    public UUID            activeNoteTabId = null;

    // ── Tab appearance globals (per-panel override when >= 0) ─────────────────
    public double tabBgOpacity = -1;
    public double tabFgOpacity = -1;

    // ── General ───────────────────────────────────────────────────────────────
    public boolean modDisabled                  = false;
    public boolean showIndicators               = true;
    public boolean cursorChangingEnabled         = true;
    public boolean selectionEnabled             = true;
    public boolean selectionFreezeChat          = true;
    public boolean selectionCtrlSkipsClicks     = true;
    public boolean doubleClickSelectEnabled     = true;
    public boolean doubleClickNameSelect        = true;
    public String  timestampFormat              = "&d[&6HH&d:&6mm&d:&6ss&d]";
    public String  collapseCounterFormat        = "&e(x{count})";
    public boolean arrowKeySwitchTabs           = true;
    public boolean flashOnTabSwitch             = false;
    public boolean flashOnMouseTabSwitch        = false;
    public boolean switchFocusedPanelOnTabClick = true;
    public boolean flashOnPanelFocus            = true;
    public boolean showContextDisplay           = true;
    public boolean panelContextEnabled          = true;
    public boolean logApiMessages               = true;

    // ── Quick Actions ─────────────────────────────────────────────────────────
    public boolean quickActionPasteToChat        = false;
    public boolean quickActionKeybindEnabled     = true;
    public int     quickActionKeybindKey         = 81; // GLFW_KEY_Q
    public boolean quickActionKeybindRequireCtrl = true;
    public List<QuickActionConfig> quickActions  = newDefaultQuickActions();

    private static List<QuickActionConfig> newDefaultQuickActions() {
        var list = new java.util.ArrayList<QuickActionConfig>();
        var def  = new QuickActionConfig();
        def.name     = "Tell Hello";
        def.template = "/tell {selected} Hello";
        list.add(def);
        return list;
    }

    // ── Search ────────────────────────────────────────────────────────────────
    public boolean    searchKeybindEnabled           = true;
    public int        searchKeybindKey               = 70; // GLFW_KEY_F
    public boolean    searchKeybindRequireCtrl       = true;
    public SearchMode searchDefaultMode              = SearchMode.CONTAINS;
    public boolean    searchDefaultCase              = false;
    public boolean    searchDefaultLive              = true;
    public boolean    searchDefaultBadge             = false;
    public boolean    searchDefaultTimestamps        = true;
    public boolean    searchDefaultCollapseIdentical = false;

    // ── Pings (global defaults) ───────────────────────────────────────────────
    public boolean pingEnabled             = true;
    public String  pingSound               = "minecraft:entity.experience_orb.pickup";
    public float   pingVolume              = 1.0f;
    public float   pingPitch               = 1.0f;
    public boolean pingCondAlways          = false;
    public boolean pingCondActiveTab       = false;
    public boolean pingCondUnfocusedTab    = true;
    public boolean pingCondGameUnfocused   = false;
    public boolean pingCondAfk             = false;
    public boolean pingCondRequireSilence  = false;
    public int     pingCooldownSec         = 2;
    public int     pingSilenceThresholdSec = 30;
    public int     pingAfkThresholdSec     = 120;

    public Set<String> hiddenContextMenuItems = new HashSet<>();

    // ── Inner classes that have no natural home in a domain object ────────────
    public static class QuickActionConfig {
        public String name     = "";
        public String template = "";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    public static int    pct(double v)     { return v < 0 ? -1 : Math.min(100, Math.max(0, (int) Math.round(v * 100))); }
    public static int    pct(float v)      { return v < 0 ? -1 : Math.min(100, Math.max(0, Math.round(v * 100))); }
    public static double fromPct(int v)    { return v < 0 ? -1.0 : v / 100.0; }

    public static int   pingVolPct(float v)       { return v < 0 ? -1 : Math.min(200, Math.max(0,   Math.round(v * 100))); }
    public static float fromPingVolPct(int v)     { return v < 0 ? -1.0f : v / 100.0f; }
    public static int   pingPitchPct(float v)     { return v < 0 ? -1 : Math.min(200, Math.max(50,  Math.round(v * 100))); }
    public static float fromPingPitchPct(int v)   { return v < 0 ? -1.0f : v / 100.0f; }
}
