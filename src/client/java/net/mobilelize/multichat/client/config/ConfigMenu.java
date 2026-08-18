package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.DropdownMenuBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.client.LzSound;
import net.mobilelize.fabriclib.input.LzInputUtil;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatTheme;
import net.mobilelize.multichat.client.HighlightEngine;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.ThemeRegistry;
import net.mobilelize.multichat.client.ping.PingPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigMenu {

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(LzText.literal("MultiChat").mc())
                .setSavingRunnable(ConfigMenu::applyAndSave);

        ConfigEntryBuilder eb = builder.entryBuilder();

        // ── Appearance ────────────────────────────────────────────────────
        ConfigCategory appearance = builder.getOrCreateCategory(LzText.literal("Appearance").mc());

        List<ChatTheme> themes = ThemeRegistry.getAll();
        Map<String, String> themeNameToId = new HashMap<>();
        List<String> themeNames = new java.util.ArrayList<>();
        for (ChatTheme t : themes) {
            themeNameToId.put(t.displayName, t.id);
            themeNames.add(t.displayName);
        }
        appearance.addEntry(eb.startDropdownMenu(
                LzText.literal("Chat Theme").mc(),
                DropdownMenuBuilder.TopCellElementBuilder.of(ThemeRegistry.active().displayName, s -> s),
                DropdownMenuBuilder.CellCreatorBuilder.of())
                .setDefaultValue("Vanilla")
                .setSelections(themeNames)
                .setTooltip(LzText.literal("Color palette for the chat tab bar, scrollbar, and message highlights. Other mods can add their own via the API.").mc())
                .setSaveConsumer(v -> {
                    String id = themeNameToId.get(v);
                    if (id != null) ThemeRegistry.setActive(id);
                })
                .build());

        appearance.addEntry(eb.startColorField(
                LzText.literal("Default Panel BG Color").mc(),
                MultichatClient.CONFIG.defaultPanelBgColor)
                .setDefaultValue(0)
                .setTooltip(LzText.literal("Message background color for any panel that hasn't set its own BG Color. 0/black = inherit the active theme's default instead.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.defaultPanelBgColor = v & 0xFFFFFF)
                .build());

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
                LzText.literal("Draggable Scrollbar").mc(),
                MultichatClient.CONFIG.scrollbarDragEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Click and drag the scrollbar on the right edge of chat to scroll.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.scrollbarDragEnabled = v)
                .build());

        appearance.addEntry(eb.startBooleanToggle(
                LzText.literal("Scrollbar Hover Glow").mc(),
                MultichatClient.CONFIG.scrollbarGlowEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Glow the scrollbar when your mouse is near it, so it's clear it can be dragged.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.scrollbarGlowEnabled = v)
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

        selection.addEntry(eb.startBooleanToggle(
                LzText.literal("Copy Uses Original Message Text").mc(),
                MultichatClient.CONFIG.selectionUsesOriginalText)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("ON: copying a drag-selection reconstructs text from each message's original, pre-wrap content. OFF: copying stitches together the wrapped lines exactly as displayed.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.selectionUsesOriginalText = v)
                .build());

        selection.addEntry(eb.startBooleanToggle(
                LzText.literal("Copy Includes Object Placeholder").mc(),
                MultichatClient.CONFIG.copyIncludesObjectPlaceholder)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Messages containing an inline object (e.g. a chat_heads player-head icon) show a placeholder character (￼) where it was. ON: copying keeps that placeholder in the clipboard text. OFF: it's left out.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.copyIncludesObjectPlaceholder = v)
                .build());

        // ── Highlight Words ──────────────────────────────────────────────────
        ConfigCategory highlight = builder.getOrCreateCategory(LzText.literal("Highlight Words").mc());

        highlight.addEntry(eb.startTextDescription(LzText.literal(
                "On a keyword match, the entire message is reformatted: the keyword span uses the Keyword " +
                "style below, everything else uses the Neutral style. Your Keywords and API Words (added by " +
                "other mods) share the same style, but can be muted independently."
        ).mc()).build());

        highlight.addEntry(eb.startStrList(LzText.literal("Your Keywords").mc(), MultichatClient.CONFIG.userHighlightKeywords)
                .setSaveConsumer(v -> MultichatClient.CONFIG.userHighlightKeywords = v)
                .build());

        highlight.addEntry(eb.startStrList(LzText.literal("Your Exceptions").mc(), MultichatClient.CONFIG.highlightExceptions)
                .setTooltip(LzText.literal(
                        "Safe words that shield a keyword match overlapping them — works against both Your " +
                        "Keywords and API Words. E.g. keyword \"ban\" + exception \"urban\" stops it firing on " +
                        "\"urban\" — the rest of the message still highlights normally if another keyword hits " +
                        "elsewhere in it."
                ).mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.highlightExceptions = v)
                .build());

        highlight.addEntry(eb.startBooleanToggle(
                LzText.literal("Enable Your Exceptions").mc(),
                MultichatClient.CONFIG.exceptionsEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> MultichatClient.CONFIG.exceptionsEnabled = v)
                .build());

        // Not persisted to config — these come from other mods calling MultiChatApi.addHighlightRule
        // at runtime, so this list is a live snapshot, not player-owned data. Edits here don't stick;
        // the actual source of truth is whatever mods currently have registered.
        highlight.addEntry(eb.startStrList(LzText.literal("API Words").mc(), HighlightEngine.getApiKeywords())
                .setSaveConsumer(v -> {})
                .build());

        highlight.addEntry(eb.startStrList(LzText.literal("API Exceptions").mc(), MultichatClient.CONFIG.apiHighlightExceptions)
                .setTooltip(LzText.literal(
                        "Same effect as Your Exceptions above (protects against both keyword pools) — a separate " +
                        "list just so words you add to counter an API-registered rule stay organized on their own, " +
                        "since a mod can re-add its words on every connect and you can't remove them for good."
                ).mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.apiHighlightExceptions = v)
                .build());

        // Not persisted to config — these come from other mods calling MultiChatApi.addHighlightException
        // at runtime, same live-snapshot deal as API Words above. Edits here don't stick.
        highlight.addEntry(eb.startStrList(LzText.literal("API-Registered Exceptions").mc(), HighlightEngine.getApiExceptions())
                .setTooltip(LzText.literal(
                        "Exception words other mods registered directly, same effect as API Exceptions above."
                ).mc())
                .setSaveConsumer(v -> {})
                .build());

        highlight.addEntry(eb.startBooleanToggle(
                LzText.literal("Enable API Exceptions").mc(),
                MultichatClient.CONFIG.apiExceptionsEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> MultichatClient.CONFIG.apiExceptionsEnabled = v)
                .build());

        highlight.addEntry(eb.startBooleanToggle(
                LzText.literal("Enable Your Keywords").mc(),
                MultichatClient.CONFIG.userHighlightEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> MultichatClient.CONFIG.userHighlightEnabled = v)
                .build());

        highlight.addEntry(eb.startBooleanToggle(
                LzText.literal("Enable API Words").mc(),
                MultichatClient.CONFIG.apiHighlightEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Mute all highlight rules registered by other mods via the API.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.apiHighlightEnabled = v)
                .build());

        highlight.addEntry(eb.startBooleanToggle(
                LzText.literal("Play Sound on Match").mc(),
                MultichatClient.CONFIG.highlightSoundEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Plays a sound whenever a message matches a highlight keyword.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.highlightSoundEnabled = v)
                .build());

        highlight.addEntry(eb.startDropdownMenu(
                LzText.literal("Highlight Sound").mc(),
                DropdownMenuBuilder.TopCellElementBuilder.of(MultichatClient.CONFIG.highlightSound, s -> s),
                DropdownMenuBuilder.CellCreatorBuilder.of())
                .setDefaultValue("minecraft:entity.experience_orb.pickup")
                .setSelections(HighlightEngine.allSoundIds())
                .setSaveConsumer(v -> MultichatClient.CONFIG.highlightSound = v)
                .build());

        highlight.addEntry(SoundBrowser.build(eb, HighlightEngine.allSoundIds(), soundId -> {
            MultichatClient.CONFIG.highlightSound = soundId;
            LzSound.playMaster(soundId, MultichatClient.CONFIG.highlightSoundPitch, MultichatClient.CONFIG.highlightSoundVolume);
        }));

        highlight.addEntry(eb.startIntSlider(
                LzText.literal("Highlight Sound Volume").mc(),
                MultiChatConfig.pingVolPct(MultichatClient.CONFIG.highlightSoundVolume), 0, 200)
                .setDefaultValue(100)
                .setTooltip(LzText.literal("0–200%. 100 = normal volume.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.highlightSoundVolume = MultiChatConfig.fromPingVolPct(v))
                .build());

        highlight.addEntry(eb.startIntSlider(
                LzText.literal("Highlight Sound Pitch").mc(),
                MultiChatConfig.pingPitchPct(MultichatClient.CONFIG.highlightSoundPitch), 50, 200)
                .setDefaultValue(100)
                .setTooltip(LzText.literal("50–200. 100 = normal pitch.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.highlightSoundPitch = MultiChatConfig.fromPingPitchPct(v))
                .build());

        highlight.addEntry(eb.startBooleanToggle(
                LzText.literal("Test Highlight Sound").mc(), false)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Toggle ON then Save to play the current sound settings.").mc())
                .setSaveConsumer(v -> { if (v) LzSound.playMaster(MultichatClient.CONFIG.highlightSound, MultichatClient.CONFIG.highlightSoundPitch, MultichatClient.CONFIG.highlightSoundVolume); })
                .build());

        addHighlightStyleEntries(highlight, eb, "Keyword", MultichatClient.CONFIG.keywordStyle, 0xFFFF55, true);
        addHighlightStyleEntries(highlight, eb, "Neutral", MultichatClient.CONFIG.neutralStyle, 0xFF5555, false);

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

        // ── Performance ───────────────────────────────────────────────────
        ConfigCategory performance = builder.getOrCreateCategory(LzText.literal("Performance").mc());

        performance.addEntry(eb.startBooleanToggle(
                LzText.literal("Optimized Loading").mc(),
                MultichatClient.CONFIG.optimizedLoadingEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("When a setting/scale/width change forces a full tab rebuild, show a " +
                        "loading placeholder sized to the panel while the whole tab wraps on a background thread, " +
                        "then swap the real content in once it's ready — instead of freezing a frame doing it on " +
                        "the main thread. Turn off for the old behavior (always rewrap immediately, synchronously).").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.optimizedLoadingEnabled = v)
                .build());

        performance.addEntry(eb.startIntField(
                LzText.literal("Optimized Loading Threshold").mc(),
                MultichatClient.CONFIG.optimizedLoadingThreshold)
                .setDefaultValue(300)
                .setMin(0)
                .setTooltip(LzText.literal("Below this many messages in a tab, always rebuild it immediately/synchronously " +
                        "regardless of Optimized Loading above — small tabs are already instant, so offloading them just " +
                        "adds a background-thread round trip for nothing. 0 = always offload.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.optimizedLoadingThreshold = Math.max(0, v))
                .build());

        performance.addEntry(eb.startBooleanToggle(
                LzText.literal("Optimized Refiltering").mc(),
                MultichatClient.CONFIG.optimizedRefilterEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Same idea as Optimized Loading above, but for running/updating a Search " +
                        "or filtered tab's matching (a full history scan) instead of a tab rebuild's wrap step. Shows " +
                        "a loading placeholder while the scan runs on a background thread, then swaps the real " +
                        "results in once ready. Turn off for the old behavior (always scan immediately, synchronously).").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.optimizedRefilterEnabled = v)
                .build());

        performance.addEntry(eb.startIntField(
                LzText.literal("Optimized Refiltering Threshold").mc(),
                MultichatClient.CONFIG.optimizedRefilterThreshold)
                .setDefaultValue(300)
                .setMin(0)
                .setTooltip(LzText.literal("Below this many messages being scanned, always refilter immediately/" +
                        "synchronously regardless of Optimized Refiltering above. 0 = always offload.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.optimizedRefilterThreshold = Math.max(0, v))
                .build());

        performance.addEntry(eb.startIntField(
                LzText.literal("Global Message Cap").mc(),
                MultichatClient.CONFIG.globalMessageCap)
                .setDefaultValue(100000)
                .setMin(-1)
                .setTooltip(LzText.literal("Maximum number of messages kept in the All tab. Once it's exceeded, " +
                        "the oldest messages are deleted to make room for new ones. -1 or 0 = unlimited.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.globalMessageCap = Math.max(-1, v))
                .build());

        performance.addEntry(eb.startBooleanToggle(
                LzText.literal("Optimized Deleting").mc(),
                MultichatClient.CONFIG.optimizedDeletingEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("When a message cap (per-tab or the Global Message Cap above) needs " +
                        "to trim a large tab, batch the trimming instead of doing it on every single message — " +
                        "trims once a small overshoot margin is hit, or earlier if messages pause for a moment. " +
                        "Turn off for the old behavior (trim back to the cap exactly, every message).").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.optimizedDeletingEnabled = v)
                .build());

        performance.addEntry(eb.startIntField(
                LzText.literal("Optimized Deleting Threshold").mc(),
                MultichatClient.CONFIG.optimizedDeletingThreshold)
                .setDefaultValue(5000)
                .setMin(0)
                .setTooltip(LzText.literal("Below this many messages in a tab, always trim it exactly/immediately " +
                        "regardless of Optimized Deleting above — small tabs are already instant to trim. 0 = " +
                        "always batch.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.optimizedDeletingThreshold = Math.max(0, v))
                .build());

        performance.addEntry(eb.startBooleanToggle(
                LzText.literal("Trim Feedback").mc(),
                MultichatClient.CONFIG.trimFeedbackEnabled)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Send a chat message (prefixed [MultiChat-Trim], filterable) every " +
                        "time a message cap trims a tab, showing how many messages were deleted and how long " +
                        "it took. Separate from the debug commands toggle — perf-testing tool, off by default.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.trimFeedbackEnabled = v)
                .build());

        // ── Advanced ──────────────────────────────────────────────────────
        ConfigCategory advanced = builder.getOrCreateCategory(LzText.literal("Advanced").mc());

        advanced.addEntry(eb.startBooleanToggle(
                LzText.literal("Log API Messages").mc(),
                MultichatClient.CONFIG.logApiMessages)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Log messages sent via the MultiChat API to the game log.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.logApiMessages = v)
                .build());

        advanced.addEntry(eb.startBooleanToggle(
                LzText.literal("Normalize Filter Text").mc(),
                MultichatClient.CONFIG.normalizeFilterText)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Strip invisible/control characters and collapse whitespace before matching filter rules. Helps when messages contain hidden formatting characters that prevent filters from matching. Only affects routing — not collapse, count, or display.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.normalizeFilterText = v)
                .build());

        advanced.addEntry(eb.startBooleanToggle(
                LzText.literal("Strip Object Content").mc(),
                MultichatClient.CONFIG.stripObjectContent)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Strip inline \"object\" content (e.g. chat_heads' player-head sprite) from messages before matching filter rules. MC 1.21.9+ only. Only affects routing — not collapse, count, or display.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.stripObjectContent = v)
                .build());

        advanced.addEntry(eb.startBooleanToggle(
                LzText.literal("Disable MultiChat").mc(),
                MultichatClient.CONFIG.modDisabled)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Disables passive features: custom chat rendering, tab routing, keybinds, and mouse hooks. Commands and the settings GUI still work.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.modDisabled = v)
                .build());

        advanced.addEntry(eb.startBooleanToggle(
                LzText.literal("Debug Commands").mc(),
                MultichatClient.CONFIG.debugCommandsEnabled)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Reveals /multichat debug fill <count> and /multichat debug clear — bulk-generates " +
                        "(or removes) synthetic test messages in the All tab for perf testing (e.g. Optimized Loading/" +
                        "Refiltering against a huge history). The command doesn't show up or run at all while this is off.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.debugCommandsEnabled = v)
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
                DropdownMenuBuilder.TopCellElementBuilder.of(MultichatClient.CONFIG.pingSound, s -> s),
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
        HelpScreen.helpCategory(builder, eb);

        return builder.build();
    }

    private static void addHighlightStyleEntries(ConfigCategory cat, ConfigEntryBuilder eb, String label,
                                                  MultiChatConfig.HighlightStyle style, int defaultColor, boolean defaultBold) {
        cat.addEntry(eb.startColorField(LzText.literal(label + " Color").mc(), style.color)
            .setDefaultValue(defaultColor)
            .setSaveConsumer(v -> style.color = v)
            .build());
        cat.addEntry(eb.startBooleanToggle(LzText.literal(label + " Bold").mc(), style.bold)
            .setDefaultValue(defaultBold)
            .setSaveConsumer(v -> style.bold = v)
            .build());
        cat.addEntry(eb.startBooleanToggle(LzText.literal(label + " Italic").mc(), style.italic)
            .setDefaultValue(false)
            .setSaveConsumer(v -> style.italic = v)
            .build());
        cat.addEntry(eb.startBooleanToggle(LzText.literal(label + " Underline").mc(), style.underline)
            .setDefaultValue(false)
            .setSaveConsumer(v -> style.underline = v)
            .build());
        cat.addEntry(eb.startBooleanToggle(LzText.literal(label + " Strikethrough").mc(), style.strikethrough)
            .setDefaultValue(false)
            .setSaveConsumer(v -> style.strikethrough = v)
            .build());
        cat.addEntry(eb.startBooleanToggle(LzText.literal(label + " Obfuscated").mc(), style.obfuscated)
            .setDefaultValue(false)
            .setSaveConsumer(v -> style.obfuscated = v)
            .build());
    }

    private static void applyAndSave() {
        MultichatClient.saveTabsToConfig();
    }
}
