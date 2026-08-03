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
    public boolean         infoTabOnPanel2 = true;

    // ── Tab appearance globals (per-panel override when >= 0) ─────────────────
    public double tabBgOpacity = -1;
    public double tabFgOpacity = -1;

    // ── General ───────────────────────────────────────────────────────────────
    public boolean modDisabled                  = false;
    public boolean normalizeFilterText          = false;
    public boolean showIndicators               = true;
    public boolean cursorChangingEnabled         = true;
    public boolean selectionEnabled             = true;
    public boolean selectionFreezeChat          = true;
    public boolean selectionCtrlSkipsClicks     = true;
    public boolean doubleClickSelectEnabled     = true;
    public boolean doubleClickNameSelect        = true;
    public boolean selectionUsesOriginalText    = true;
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

    // ── Highlight Words ──────────────────────────────────────────────────────
    // One shared style pair — both Your Keywords and API Words render with the same colors/formatting.
    public HighlightStyle  keywordStyle          = new HighlightStyle(0xFFFF55, true);  // yellow, bold
    public HighlightStyle  neutralStyle          = new HighlightStyle(0xFF5555, false); // red

    public boolean         userHighlightEnabled  = true;
    public List<String>    userHighlightKeywords = new ArrayList<>();

    public boolean         apiHighlightEnabled   = true; // player-side mute for mod-registered rules

    /** Full per-span text formatting, not just color — mirrors every LzStyle.with* toggle. */
    public static class HighlightStyle {
        public int     color;
        public boolean bold;
        public boolean italic;
        public boolean underline;
        public boolean strikethrough;
        public boolean obfuscated;

        public HighlightStyle() {}
        public HighlightStyle(int color, boolean bold) {
            this.color = color;
            this.bold  = bold;
        }
    }

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

    // Persisted across restarts — every modId that has ever called MultiChatApi.addHighlightRule,
    // in any past session. Lets a mod ask "have I registered before?" so it can seed its default
    // keyword list exactly once instead of re-adding it (and silently undoing a user's removal)
    // on every single client start.
    public Set<String> registeredHighlightMods = new HashSet<>();

    // ── Null-safety ──────────────────────────────────────────────────────────
    // Guards every reference-type field (List/Set/String/enum) against a saved config file
    // missing a value — e.g. an older config.json from before a field existed. Primitives
    // (boolean/int/float/double) are skipped: they can't be null, so there's nothing to guard.
    // activeNoteTabId (UUID) is also skipped: null there is a valid, meaningful state (no active note tab).
    @Override
    protected void onLoad() {
        if (panels == null) panels = new ArrayList<>();
        if (tabs == null) tabs = new ArrayList<>();
        if (noteTabs == null) noteTabs = new ArrayList<>();
        if (timestampFormat == null) timestampFormat = "&d[&6HH&d:&6mm&d:&6ss&d]";
        if (collapseCounterFormat == null) collapseCounterFormat = "&e(x{count})";
        if (userHighlightKeywords == null) userHighlightKeywords = new ArrayList<>();
        if (keywordStyle == null) keywordStyle = new HighlightStyle(0xFFFF55, true);
        if (neutralStyle == null) neutralStyle = new HighlightStyle(0xFF5555, false);
        if (quickActions == null) quickActions = newDefaultQuickActions();
        if (searchDefaultMode == null) searchDefaultMode = SearchMode.CONTAINS;
        if (pingSound == null) pingSound = "minecraft:entity.experience_orb.pickup";
        if (hiddenContextMenuItems == null) hiddenContextMenuItems = new HashSet<>();
        if (registeredHighlightMods == null) registeredHighlightMods = new HashSet<>();
    }

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
