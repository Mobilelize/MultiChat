package net.mobilelize.multichat.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import net.mobilelize.fabriclib.config.LzConfig;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.NoteTab;
import net.mobilelize.multichat.client.search.SearchMode;

import java.util.*;

public class MultiChatConfig extends LzConfig {

    @Override
    public String directory() { return "config/multichat"; }

    // Keybinds are stored by vanilla key name (as in options.txt), not by numeric code —
    // the numbering changed from GLFW key codes to SDL scancodes in 26.3.
    public static final String QUICK_ACTION_KEY_DEFAULT = "key.keyboard.q";
    public static final String SEARCH_KEY_DEFAULT       = "key.keyboard.f";

    @Override
    protected void onLoad() {
        quickActionKeybindKey = migrateKeyName(quickActionKeybindKey, QUICK_ACTION_KEY_DEFAULT);
        searchKeybindKey      = migrateKeyName(searchKeybindKey, SEARCH_KEY_DEFAULT);
    }

    /** Current key code for a stored key name, comparable with {@code KeyEvent.key()}. */
    public static int keyCode(String keyName) {
        return InputConstants.getKey(keyName).getValue();
    }

    // Pre-26.3 configs saved raw GLFW key codes (Gson reads them into these fields as "81" etc.)
    private static String migrateKeyName(String stored, String fallback) {
        if (stored == null || stored.isBlank()) return fallback;
        if (!stored.chars().allMatch(Character::isDigit)) return stored;
        String name = glfwKeyName(Integer.parseInt(stored));
        return name != null ? name : fallback;
    }

    private static String glfwKeyName(int glfw) {
        if (glfw >= 65 && glfw <= 90)   return "key.keyboard." + (char) ('a' + glfw - 65);   // A-Z
        if (glfw >= 48 && glfw <= 57)   return "key.keyboard." + (char) ('0' + glfw - 48);   // 0-9
        if (glfw >= 290 && glfw <= 314) return "key.keyboard.f" + (glfw - 289);              // F1-F25
        return switch (glfw) {
            case 32  -> "key.keyboard.space";
            case 258 -> "key.keyboard.tab";
            case 262 -> "key.keyboard.right";
            case 263 -> "key.keyboard.left";
            case 264 -> "key.keyboard.down";
            case 265 -> "key.keyboard.up";
            case 268 -> "key.keyboard.home";
            case 269 -> "key.keyboard.end";
            case 96  -> "key.keyboard.grave.accent";
            default  -> null;
        };
    }

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

    // ── Quick Actions ─────────────────────────────────────────────────────────
    public boolean quickActionPasteToChat        = false;
    public boolean quickActionKeybindEnabled     = true;
    public String  quickActionKeybindKey         = QUICK_ACTION_KEY_DEFAULT;
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
    public String     searchKeybindKey               = SEARCH_KEY_DEFAULT;
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
