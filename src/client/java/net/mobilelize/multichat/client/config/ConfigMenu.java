package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.input.LzInputUtil;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.MultichatClient;

public class ConfigMenu {

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(LzText.literal("MultiChat").mc())
                .setSavingRunnable(ConfigMenu::applyAndSave);

        ConfigEntryBuilder eb = builder.entryBuilder();

        // ── Appearance ────────────────────────────────────────────────────
        ConfigCategory appearance = builder.getOrCreateCategory(LzText.literal("Appearance").mc());

        appearance.addEntry(eb.startBooleanToggle(
                LzText.literal("Show Indicators").mc(),
                MultichatClient.CONFIG.showIndicators)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Show the colored indicator bar and icon on the left of chat messages.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.showIndicators = v)
                .build());

        appearance.addEntry(eb.startBooleanToggle(
                LzText.literal("Show Context Display").mc(),
                MultichatClient.CONFIG.showContextDisplay)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Show the active context tab indicator above the chat input bar.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.showContextDisplay = v)
                .build());

        appearance.addEntry(eb.startDoubleField(
                LzText.literal("Tab BG Opacity").mc(),
                MultichatClient.CONFIG.tabBgOpacity)
                .setDefaultValue(-1.0)
                .setTooltip(LzText.literal("Default tab background opacity for all panels (0.0 = invisible, 1.0 = opaque). -1 = built-in default. Overridden per-panel in Panel Settings.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.tabBgOpacity = v)
                .build());

        appearance.addEntry(eb.startDoubleField(
                LzText.literal("Tab Text Opacity").mc(),
                MultichatClient.CONFIG.tabFgOpacity)
                .setDefaultValue(-1.0)
                .setTooltip(LzText.literal("Default tab text opacity for all panels (0.0 = invisible, 1.0 = opaque). -1 = built-in default. Overridden per-panel in Panel Settings.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.tabFgOpacity = v)
                .build());

        appearance.addEntry(eb.startStrField(
                LzText.literal("Timestamp Format").mc(),
                MultichatClient.CONFIG.timestampFormat)
                .setDefaultValue("&d[&6HH&d:&6mm&d:&6ss&d]")
                .setTooltip(LzText.literal("Java DateTimeFormatter pattern. Supports color codes (e.g. &7[HH:mm:ss]&r).").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.timestampFormat = v)
                .build());

        appearance.addEntry(eb.startStrField(
                LzText.literal("Collapse Counter Format").mc(),
                MultichatClient.CONFIG.collapseCounterFormat)
                .setDefaultValue("&e(x{count})")
                .setTooltip(LzText.literal("Format for the repeat counter on collapsed messages. Use {count} for the number. Supports color codes (e.g. &8 (x{count})).").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.collapseCounterFormat = v)
                .build());

        // ── Navigation ────────────────────────────────────────────────────
        ConfigCategory navigation = builder.getOrCreateCategory(LzText.literal("Navigation").mc());

        navigation.addEntry(eb.startBooleanToggle(
                LzText.literal("Arrow Keys Switch Tabs").mc(),
                MultichatClient.CONFIG.arrowKeySwitchTabs)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Arrow keys switch tabs on the focused panel. Ctrl+Arrow cycles which panel is focused.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.arrowKeySwitchTabs = v)
                .build());

        navigation.addEntry(eb.startBooleanToggle(
                LzText.literal("Panel Context on Switch").mc(),
                MultichatClient.CONFIG.panelContextEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("When switching panels with Ctrl+Arrow, auto-activate the context tab assigned to that panel (if any). Set per-panel in Panel → Tabs.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.panelContextEnabled = v)
                .build());

        navigation.addEntry(eb.startBooleanToggle(
                LzText.literal("Flash on Arrow Key Tab Switch").mc(),
                MultichatClient.CONFIG.flashOnTabSwitch)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Flash message backgrounds when switching tabs with arrow keys.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.flashOnTabSwitch = v)
                .build());

        navigation.addEntry(eb.startBooleanToggle(
                LzText.literal("Switch Focused Panel on Tab Click").mc(),
                MultichatClient.CONFIG.switchFocusedPanelOnTabClick)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Clicking a tab makes that panel the focused one (arrow keys and Ctrl+Arrow then operate on it).").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.switchFocusedPanelOnTabClick = v)
                .build());

        navigation.addEntry(eb.startBooleanToggle(
                LzText.literal("Flash on Mouse Tab Switch").mc(),
                MultichatClient.CONFIG.flashOnMouseTabSwitch)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Flash message backgrounds when clicking a different tab with the mouse (same as the arrow key flash, but for mouse).").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.flashOnMouseTabSwitch = v)
                .build());

        navigation.addEntry(eb.startBooleanToggle(
                LzText.literal("Flash on Panel Focus Switch").mc(),
                MultichatClient.CONFIG.flashOnPanelFocus)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Flash message backgrounds when switching focused panel with Ctrl+Arrow.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.flashOnPanelFocus = v)
                .build());

        // ── Selection ─────────────────────────────────────────────────────
        ConfigCategory selection = builder.getOrCreateCategory(LzText.literal("Selection").mc());

        selection.addEntry(eb.startBooleanToggle(
                LzText.literal("Enable Text Selection").mc(),
                MultichatClient.CONFIG.selectionEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Click and drag in chat to select text. Ctrl+C to copy.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.selectionEnabled = v)
                .build());

        selection.addEntry(eb.startBooleanToggle(
                LzText.literal("Ctrl Skips Click Events").mc(),
                MultichatClient.CONFIG.selectionCtrlSkipsClicks)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("When Ctrl is held, clicking on links or commands starts selection instead of activating the click.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.selectionCtrlSkipsClicks = v)
                .build());

        selection.addEntry(eb.startBooleanToggle(
                LzText.literal("Double Click Word Select").mc(),
                MultichatClient.CONFIG.doubleClickSelectEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Double-click a word in chat to select it.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.doubleClickSelectEnabled = v)
                .build());

        selection.addEntry(eb.startBooleanToggle(
                LzText.literal("Double Click Name Select").mc(),
                MultichatClient.CONFIG.doubleClickNameSelect)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("ON: selects letters/digits/underscores only — double-clicking inside <Player> selects Player. OFF: selects the whole non-whitespace token — <Player> is selected as one unit.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.doubleClickNameSelect = v)
                .build());

        // ── Advanced ──────────────────────────────────────────────────────
        ConfigCategory advanced = builder.getOrCreateCategory(LzText.literal("Advanced").mc());

        advanced.addEntry(eb.startBooleanToggle(
                LzText.literal("Server Network Message Capture").mc(),
                MultichatClient.CONFIG.earlyMessageCapture)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Use Fabric API to capture server network messages before other mods modify them. Fixes filter rules when other mods prepend text to messages (e.g. timestamps).").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.earlyMessageCapture = v)
                .build());

        advanced.addEntry(eb.startBooleanToggle(
                LzText.literal("Apply Settings on Refilter").mc(),
                MultichatClient.CONFIG.applySettingsOnRefilter)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("When ON, refiltering a tab also applies its timestamps and collapse settings to rebuilt messages.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.applySettingsOnRefilter = v)
                .build());

        advanced.addEntry(eb.startBooleanToggle(
                LzText.literal("Log API Messages").mc(),
                MultichatClient.CONFIG.logApiMessages)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Log messages sent via the MultiChat API to the game log.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.logApiMessages = v)
                .build());

        advanced.addEntry(eb.startBooleanToggle(
                LzText.literal("Disable MultiChat").mc(),
                MultichatClient.CONFIG.modDisabled)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Disables passive features: custom chat rendering, tab routing, keybinds, and mouse hooks. Commands and the settings GUI still work.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.modDisabled = v)
                .build());

        // ── Search ────────────────────────────────────────────────────────
        ConfigCategory search = builder.getOrCreateCategory(LzText.literal("Search").mc());

        search.addEntry(eb.startBooleanToggle(
                LzText.literal("Enable Search Keybind").mc(),
                MultichatClient.CONFIG.searchKeybindEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("When disabled, the search keybind does nothing.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchKeybindEnabled = v)
                .build());

        search.addEntry(eb.startKeyCodeField(
                LzText.literal("Search Key").mc(),
                LzInputUtil.fromKeyCode(MultichatClient.CONFIG.searchKeybindKey, 0))
                .setDefaultValue(LzInputUtil.fromKeyCode(70, 0))
                .setAllowModifiers(false)
                .setTooltip(LzText.literal("Key to open search (combine with Require Ctrl for e.g. Ctrl+F).").mc())
                .setKeySaveConsumer(key -> MultichatClient.CONFIG.searchKeybindKey = key.getCode())
                .build());

        search.addEntry(eb.startBooleanToggle(
                LzText.literal("Require Ctrl").mc(),
                MultichatClient.CONFIG.searchKeybindRequireCtrl)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("When on, Ctrl must be held with the search key. Off = single key only.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchKeybindRequireCtrl = v)
                .build());

        search.addEntry(eb.startEnumSelector(
                LzText.literal("Default: Search Mode").mc(),
                net.mobilelize.multichat.client.search.SearchMode.class,
                MultichatClient.CONFIG.searchDefaultMode)
                .setDefaultValue(net.mobilelize.multichat.client.search.SearchMode.CONTAINS)
                .setEnumNameProvider(v -> LzText.literal(v.name()).mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchDefaultMode = v)
                .build());

        search.addEntry(eb.startBooleanToggle(
                LzText.literal("Default: Case Sensitive").mc(),
                MultichatClient.CONFIG.searchDefaultCase)
                .setDefaultValue(false)
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchDefaultCase = v)
                .build());

        search.addEntry(eb.startBooleanToggle(
                LzText.literal("Default: Live Update").mc(),
                MultichatClient.CONFIG.searchDefaultLive)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("When on, new messages matching the query are added to the search tab in real-time.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchDefaultLive = v)
                .build());

        search.addEntry(eb.startBooleanToggle(
                LzText.literal("Default: Show Badge").mc(),
                MultichatClient.CONFIG.searchDefaultBadge)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Show unread count badge on search tabs by default.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchDefaultBadge = v)
                .build());

        search.addEntry(eb.startBooleanToggle(
                LzText.literal("Default: Timestamps").mc(),
                MultichatClient.CONFIG.searchDefaultTimestamps)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Show timestamps on search results by default.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchDefaultTimestamps = v)
                .build());

        search.addEntry(eb.startBooleanToggle(
                LzText.literal("Default: Collapse Identical").mc(),
                MultichatClient.CONFIG.searchDefaultCollapseIdentical)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Collapse duplicate messages in search results by default.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchDefaultCollapseIdentical = v)
                .build());

        // ── Help ──────────────────────────────────────────────────────────
        ConfigCategory help = builder.getOrCreateCategory(LzText.literal("Help").mc());

        help.addEntry(eb.startTextDescription(LzText.format(
            "&e&lTabs&r\n" +
            "&7Tabs organize chat into views. Each panel shows a list of tabs at the bottom (or top).\n" +
            "  &f[All]&r&7  Receives every message.\n" +
            "  &f[Vanilla]&r&7  The default Minecraft chat. Only visible on the main panel.\n" +
            "  &f[Filtered]&r&7  User-defined tab. Shows messages matching its whitelist/blacklist rules.\n" +
            "  &f[Search]&r&7  Temporary tab created by a search query.\n" +
            "  &f[API]&r&7  Controlled by another mod via the MultiChat API."
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
            "&e&lPanels&r\n" +
            "&7Panels are independent chat windows. The &fmain panel&r&7 is always on the left.\n" +
            "Additional panels can be positioned anywhere on screen.\n" +
            "Open &fMod Menu → MultiChat → Panels&r&7 or run &f/multichat panels&r&7 to manage panels.\n" +
            "Use &fPanel Layout&r&7 to drag and resize panels visually with snap-to-grid.\n" +
            "&fArrow keys&r&7 switch tabs on the focused panel. &fCtrl+Arrow&r&7 cycles the focused panel.\n" +
            "Each panel can have a &fPanel Context Tab&r&7 — set in Panel → Tabs → &fSet CTX&r&7.\n" +
            "When &fPanel Context on Switch&r&7 is enabled, focusing a panel via Ctrl+Arrow auto-activates its context tab."
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
            "&e&lFilters (Whitelist / Blacklist)&r\n" +
            "&7Filtered tabs use rules to decide which messages they receive.\n" +
            "  &fWhitelist&r&7 — message must match at least one rule (or all, if mode is ALL).\n" +
            "  &fBlacklist&r&7 — matching messages are excluded even if whitelisted.\n" +
            "Rule types: &fCONTAINS, STARTS_WITH, ENDS_WITH, REGEX, EXACT.&r\n" +
            "&7Indicator filter: &fANY, CHAT, SYSTEM&r&7, etc. — filters by message source type."
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
            "&e&lDifference Mode&r\n" +
            "&7A filtered tab with &fDifference Mode&r&7 on acts as a catch-all for unmatched messages.\n" +
            "When active, it receives any message that &fnone&r&7 of the other currently active panel tabs received.\n" +
            "Rules:\n" +
            "  &f•&r&7 Exactly &fone&r&7 active tab (the tab shown on a panel) may have Difference Mode enabled.\n" +
            "    If zero or two or more active tabs have it on, the feature does nothing.\n" +
            "  &f•&r&7 The check is against the &factive tab per panel&r&7 only — not all tabs in the list.\n" +
            "  &f•&r&7 Other tabs still receive their matched messages as normal.\n" +
            "Typical use: put your filters on other tabs, put a Difference tab on a second panel\n" +
            "to catch everything that slipped through."
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
            "&e&lContext Tab&r\n" +
            "&7Right-click a tab → &fToggle Context&r&7 to make it active, or use &f/multichat context <tab>&r&7.\n" +
            "While a context tab is set, messages you send are prefixed automatically.\n" +
            "  &fContext Prefix&r&7 — text prepended to your message (set in Tab Edit).\n" +
            "  &fCommand Mode ON&r&7 — sent as a command (e.g. prefix &f/tell Steve&r&7 + your message).\n" +
            "  &fCommand Mode OFF&r&7 — sent as plain chat text.\n" +
            "The active context is shown above the chat input bar.\n" +
            "&fPanel Context&r&7 — assign a context tab to a panel in Panel → Tabs → &fSet CTX&r&7.\n" +
            "Switching to that panel via &fCtrl+Arrow&r&7 then auto-activates its context tab."
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
            "&e&lText Selection & Search&r\n" +
            "&7Click and drag in chat to select text. &fCtrl+C&r&7 copies the selection.\n" +
            "&fCtrl+F&r&7 (or your configured key) opens Search. With text selected, it pre-fills the query.\n" +
            "Right-click any message for: &fCopy, Search for this, Jump to All, Delete.&r\n" +
            "&7Search supports &fCONTAINS, REGEX, FUZZY&r&7 modes and live updates as new messages arrive."
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
            "&e&lFormat Codes&r\n" +
            "&7Used in Timestamp Format and Collapse Counter Format fields.\n" +
            "  &f&#RRGGBB&r&7 — hex color   " +
            "&aa&r&7 — green   &bb&r&7 — aqua   &cc&r&7 — red   &dd&r&7 — purple\n" +
            "  &ee&r&7 — yellow   &ff&r&7 — white   &77 — gray   &88 — dark gray\n" +
            "  &l&fBold&r&7   &o&fItalic&r&7   &n&fUnderline&r&7   &m&fStrike&r&7   " +
            "&fr&7 — reset   k — obfuscate"
        ).mc()).build());

        return builder.build();
    }

    private static void applyAndSave() {
        MultichatClient.saveTabsToConfig();
    }
}
