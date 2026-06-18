package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.DropdownMenuBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.client.LzSound;
import net.mobilelize.fabriclib.input.LzInputUtil;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.config.MultiChatConfig;
import net.mobilelize.multichat.client.ping.PingPlayer;

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
                LzText.literal("Cursor Changing").mc(),
                MultichatClient.CONFIG.cursorChangingEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Change the mouse cursor when hovering over chat: hand for tabs and clickable links, I-beam for selectable text, hand for context menus.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.cursorChangingEnabled = v)
                .build());

        appearance.addEntry(eb.startBooleanToggle(
                LzText.literal("Show Context Display").mc(),
                MultichatClient.CONFIG.showContextDisplay)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Show the active context tab indicator above the chat input bar.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.showContextDisplay = v)
                .build());

        appearance.addEntry(eb.startIntSlider(
                LzText.literal("Tab BG Opacity").mc(),
                MultiChatConfig.pct(MultichatClient.CONFIG.tabBgOpacity), -1, 100)
                .setDefaultValue(-1)
                .setTooltip(LzText.literal("Default tab background opacity for all panels (0-100%). -1 = built-in default. Overridden per-panel in Panel Settings.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.tabBgOpacity = MultiChatConfig.fromPct(v))
                .build());

        appearance.addEntry(eb.startIntSlider(
                LzText.literal("Tab Text Opacity").mc(),
                MultiChatConfig.pct(MultichatClient.CONFIG.tabFgOpacity), -1, 100)
                .setDefaultValue(-1)
                .setTooltip(LzText.literal("Default tab text opacity for all panels (0-100%). -1 = built-in default. Overridden per-panel in Panel Settings.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.tabFgOpacity = MultiChatConfig.fromPct(v))
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
                LzText.literal("Freeze Chat While Selecting").mc(),
                MultichatClient.CONFIG.selectionFreezeChat)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("While dragging to select text (and at least one character is selected), new messages don't scroll the view — the selected content stays in place.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.selectionFreezeChat = v)
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

        // ── Quick Actions ─────────────────────────────────────────────────────
        ConfigCategory quickActions = builder.getOrCreateCategory(LzText.literal("Quick Actions").mc());

        quickActions.addEntry(eb.startBooleanToggle(
                LzText.literal("Paste to Chat Instead of Run").mc(),
                MultichatClient.CONFIG.quickActionPasteToChat)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("When on, Quick Actions paste the command into the chat field instead of sending it immediately.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.quickActionPasteToChat = v)
                .build());

        quickActions.addEntry(eb.startBooleanToggle(
                LzText.literal("Enable Keybind").mc(),
                MultichatClient.CONFIG.quickActionKeybindEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Open the Quick Actions overlay via keybind while in chat.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.quickActionKeybindEnabled = v)
                .build());

        quickActions.addEntry(eb.startKeyCodeField(
                LzText.literal("Quick Action Key").mc(),
                LzInputUtil.fromKeyCode(MultichatClient.CONFIG.quickActionKeybindKey, 0))
                .setDefaultValue(LzInputUtil.fromKeyCode(81, 0))
                .setAllowModifiers(false)
                .setTooltip(LzText.literal("Key to open Quick Actions (default: Q, combine with Require Ctrl for Ctrl+Q).").mc())
                .setKeySaveConsumer(key -> MultichatClient.CONFIG.quickActionKeybindKey = key.getCode())
                .build());

        quickActions.addEntry(eb.startBooleanToggle(
                LzText.literal("Require Ctrl").mc(),
                MultichatClient.CONFIG.quickActionKeybindRequireCtrl)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("When on, Ctrl must be held with the Quick Action key.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.quickActionKeybindRequireCtrl = v)
                .build());

        quickActions.addEntry(eb.startTextDescription(LzText.format(
                "&7To add, edit, or remove actions, open &fMultiChat Tabs → Quick Actions&7.\n" +
                "Templates support &e{selected}&7 — replaced with your selected chat text at run time."
        ).mc()).build());

        // ── Notes ─────────────────────────────────────────────────────────────
        ConfigCategory notesCategory = builder.getOrCreateCategory(LzText.literal("Notes").mc());

        notesCategory.addEntry(eb.startBooleanToggle(
                LzText.literal("Notes Always Open").mc(),
                MultichatClient.CONFIG.notesAlwaysOpen)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("On startup, switch each panel to its first Notes tab instead of restoring the last active tab.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.notesAlwaysOpen = v)
                .build());

        // ── Sound Pings ───────────────────────────────────────────────────────
        ConfigCategory pings = builder.getOrCreateCategory(LzText.literal("Sound Pings").mc());

        pings.addEntry(eb.startBooleanToggle(
                LzText.literal("Enable Sound Pings").mc(),
                MultichatClient.CONFIG.pingEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Master switch for audio pings on chat messages. Each tab also needs its own toggle in Tab Edit → Sound Pings.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.pingEnabled = v)
                .build());

        pings.addEntry(eb.startDropdownMenu(
                LzText.literal("Default Ping Sound").mc(),
                DropdownMenuBuilder.TopCellElementBuilder.<String>of(MultichatClient.CONFIG.pingSound, s -> s),
                DropdownMenuBuilder.CellCreatorBuilder.of())
                .setDefaultValue("minecraft:entity.experience_orb.pickup")
                .setSelections(PingPlayer.allSoundIds())
                .setSaveConsumer(v -> MultichatClient.CONFIG.pingSound = v)
                .build());

        pings.addEntry(SoundBrowser.build(eb, PingPlayer.allSoundIds(), soundId -> {
            MultichatClient.CONFIG.pingSound = soundId;
            LzSound.playMaster(soundId, MultichatClient.CONFIG.pingPitch, MultichatClient.CONFIG.pingVolume);
        }));

        pings.addEntry(eb.startIntSlider(
                LzText.literal("Ping Volume").mc(),
                MultiChatConfig.pingVolPct(MultichatClient.CONFIG.pingVolume), 0, 200)
                .setDefaultValue(100)
                .setTooltip(LzText.literal("0–200%. 100 = normal volume.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.pingVolume = MultiChatConfig.fromPingVolPct(v))
                .build());

        pings.addEntry(eb.startIntSlider(
                LzText.literal("Ping Pitch").mc(),
                MultiChatConfig.pingPitchPct(MultichatClient.CONFIG.pingPitch), 50, 200)
                .setDefaultValue(100)
                .setTooltip(LzText.literal("50–200. 100 = normal pitch.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.pingPitch = MultiChatConfig.fromPingPitchPct(v))
                .build());

        pings.addEntry(eb.startBooleanToggle(
                LzText.literal("Test Ping Sound").mc(), false)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Toggle ON then Save to play the current sound settings.").mc())
                .setSaveConsumer(v -> { if (v) LzSound.playMaster(MultichatClient.CONFIG.pingSound, MultichatClient.CONFIG.pingPitch, MultichatClient.CONFIG.pingVolume); })
                .build());

        pings.addEntry(eb.startTextDescription(LzText.literal(
                "Conditions — OR logic: ping fires if any enabled condition is true.\n" +
                "Require Silence is an AND gate on top of the selected conditions.").mc()).build());

        pings.addEntry(eb.startBooleanToggle(
                LzText.literal("Ping Always").mc(),
                MultichatClient.CONFIG.pingCondAlways)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Ping on every message regardless of focus or AFK state.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.pingCondAlways = v)
                .build());

        pings.addEntry(eb.startBooleanToggle(
                LzText.literal("Ping when Tab Active").mc(),
                MultichatClient.CONFIG.pingCondActiveTab)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Ping even when the tab is currently visible on a panel.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.pingCondActiveTab = v)
                .build());

        pings.addEntry(eb.startBooleanToggle(
                LzText.literal("Ping when Tab Unfocused").mc(),
                MultichatClient.CONFIG.pingCondUnfocusedTab)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Ping when the tab is not active on any panel.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.pingCondUnfocusedTab = v)
                .build());

        pings.addEntry(eb.startBooleanToggle(
                LzText.literal("Ping when Game Unfocused").mc(),
                MultichatClient.CONFIG.pingCondGameUnfocused)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Ping when the Minecraft window does not have OS focus.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.pingCondGameUnfocused = v)
                .build());

        pings.addEntry(eb.startBooleanToggle(
                LzText.literal("Ping when AFK").mc(),
                MultichatClient.CONFIG.pingCondAfk)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Ping when mouse has not moved for AFK Threshold seconds.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.pingCondAfk = v)
                .build());

        pings.addEntry(eb.startBooleanToggle(
                LzText.literal("Require Silence").mc(),
                MultichatClient.CONFIG.pingCondRequireSilence)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("AND gate: the gap between the previous message and this one must be >= Silence Threshold. Any message resets the clock — pure inter-message silence.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.pingCondRequireSilence = v)
                .build());

        pings.addEntry(eb.startIntField(
                LzText.literal("Silence Threshold (s)").mc(),
                MultichatClient.CONFIG.pingSilenceThresholdSec)
                .setDefaultValue(30)
                .setMin(1)
                .setTooltip(LzText.literal("Seconds of no messages required before Require Silence triggers a ping.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.pingSilenceThresholdSec = v)
                .build());

        pings.addEntry(eb.startIntField(
                LzText.literal("AFK Threshold (s)").mc(),
                MultichatClient.CONFIG.pingAfkThresholdSec)
                .setDefaultValue(120)
                .setMin(10)
                .setTooltip(LzText.literal("Seconds without mouse movement before player is considered AFK. Used by AFK ping mode.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.pingAfkThresholdSec = v)
                .build());

        pings.addEntry(eb.startIntField(
                LzText.literal("Ping Cooldown (s)").mc(),
                MultichatClient.CONFIG.pingCooldownSec)
                .setDefaultValue(2)
                .setMin(0)
                .setTooltip(LzText.literal("Minimum seconds between pings per tab. 0 = no cooldown.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.pingCooldownSec = v)
                .build());

        // ── Context Menus ─────────────────────────────────────────────────────
        ConfigCategory ctxMenus = builder.getOrCreateCategory(LzText.literal("Context Menus").mc());

        ctxMenus.addEntry(eb.startTextDescription(LzText.literal(
                "Toggle which items appear when you right-click a tab. " +
                "Items that only appear conditionally (e.g. Set Active Note Tab on note tabs) " +
                "are still filtered by their normal conditions when enabled.").mc()).build());

        for (String[] entry : net.mobilelize.multichat.client.ContextMenu.ALL_ITEMS) {
            String id    = entry[0];
            String label = entry[1];
            boolean enabled = !MultichatClient.CONFIG.hiddenContextMenuItems.contains(id);
            ctxMenus.addEntry(eb.startBooleanToggle(LzText.literal(label).mc(), enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> {
                    if (v) MultichatClient.CONFIG.hiddenContextMenuItems.remove(id);
                    else   MultichatClient.CONFIG.hiddenContextMenuItems.add(id);
                })
                .build());
        }

        // ── Help ──────────────────────────────────────────────────────────
        ConfigCategory help = builder.getOrCreateCategory(LzText.literal("Help").mc());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lTabs&r
                        &7Tabs organize chat into views. Each panel shows a list of tabs at the bottom (or top).
                          &f[All]&r&7  Receives every message.
                          &f[Vanilla]&r&7  The default Minecraft chat. Only visible on the main panel.
                          &f[Filtered]&r&7  User-defined tab. Shows messages matching its whitelist/blacklist rules.
                          &f[Search]&r&7  Temporary tab created by a search query.
                          &f[API]&r&7  Controlled by another mod via the MultiChat API."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lTab List Indicators&r
                        &7Shown after the tab name in the Tabs list screen.
                          &fWL:n&r&7 — whitelist rule count   &fBL:n&r&7 — blacklist rule count
                          &fT&r&7 — timestamps on   &fC&r&7 — collapse repeating on
                          &fD&r&7 — difference mode on (layer 0)   &fD5&r&7 / &fD-2&r&7 — difference mode, non-zero layer
                          &fE&r&7 — difference exception on   &fB&r&7 — badge hidden   &fP&r&7 — context prefix set   &fS&r&7 — sound ping on"""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lPanels&r
                        &7Panels are independent chat windows. The &fmain panel&r&7 is always on the left.
                        Additional panels can be positioned anywhere on screen.
                        Open &fMod Menu → MultiChat → Panels&r&7 or run &f/multichat panels&r&7 to manage panels.
                        Use &fPanel Layout&r&7 to drag and resize panels visually with snap-to-grid.
                        &fArrow keys&r&7 switch tabs on the focused panel. &fCtrl+Arrow&r&7 cycles the focused panel.
                        Each panel can have a &fPanel Context Tab&r&7 — set in Panel → Tabs → &fSet CTX&r&7.
                        When &fPanel Context on Switch&r&7 is enabled, focusing a panel via Ctrl+Arrow auto-activates its context tab."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lFilters (Whitelist / Blacklist)&r
                        &7Filtered tabs use rules to decide which messages they receive.
                          &fWhitelist&r&7 — message must match at least one rule (or all, if mode is ALL).
                          &fBlacklist&r&7 — matching messages are excluded even if whitelisted.
                        Rule types: &fCONTAINS, STARTS_WITH, ENDS_WITH, REGEX, EXACT.&r
                        &7Indicator filter: &fANY, CHAT, SYSTEM&r&7, etc. — filters by message source type."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lDifference Mode&r
                        &7Routes messages through a priority pipeline based on the active tab on each enabled panel.
                          &fLayer index&r&7 — higher number = higher priority. Tabs at the same index share a layer.
                          &fPipeline order&r&7 — highest layer checked first. If any tab at that layer matches,
                            all matching tabs at that layer receive the message and the pipeline stops.
                            Lower layers never see that message.
                          &fNon-diff tab wins&r&7 — if the active tab on any panel is a normal (non-diff) filtered tab
                            and it matches, the entire diff pipeline is suppressed.
                          &fDifference Exception&r&7 — tab receives messages normally but never blocks the pipeline.
                            Other diff tabs are unaffected by what an exception tab matches.
                        Layers use the &factive tab per panel&r&7 — switching panels changes which tabs participate.
                        Negative layer indices are allowed (useful for low-priority catch-all tabs)."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lContext Tab&r
                        &7Right-click a tab → &fToggle Context&r&7 to make it active, or use &f/multichat context <tab>&r&7.
                        While a context tab is set, messages you send are prefixed automatically.
                          &fContext Prefix&r&7 — text prepended to your message (set in Tab Edit).
                          &fCommand Mode ON&r&7 — sent as a command (e.g. prefix &f/tell Steve&r&7 + your message).
                          &fCommand Mode OFF&r&7 — sent as plain chat text.
                        The active context is shown above the chat input bar.
                        &fPanel Context&r&7 — assign a context tab to a panel in Panel → Tabs → &fSet CTX&r&7.
                        Switching to that panel via &fCtrl+Arrow&r&7 then auto-activates its context tab."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lText Selection & Search&r
                        &7Click and drag in chat to select text. &fCtrl+C&r&7 copies the selection.
                        &fCtrl+F&r&7 (or your configured key) opens Search. With text selected, it pre-fills the query.
                        Right-click any message for: &fCopy, Search for this, Jump to All, Delete.&r
                        &7Search supports &fCONTAINS, REGEX, FUZZY&r&7 modes and live updates as new messages arrive."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lFormat Codes&r
                        &7Used in Timestamp Format and Collapse Counter Format fields.
                          &f&#RRGGBB&r&7 — hex color   \
                        &aa&r&7 — green   &bb&r&7 — aqua   &cc&r&7 — red   &dd&r&7 — purple
                          &ee&r&7 — yellow   &ff&r&7 — white   &77 — gray   &88 — dark gray
                          &l&fBold&r&7   &o&fItalic&r&7   &n&fUnderline&r&7   &m&fStrike&r&7   \
                        &fr&7 — reset   k — obfuscate"""
        ).mc()).build());

        return builder.build();
    }

    private static void applyAndSave() {
        MultichatClient.saveTabsToConfig();
    }
}
