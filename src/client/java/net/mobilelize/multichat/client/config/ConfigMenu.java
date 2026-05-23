package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.client.render.ChatRenderer;

public class ConfigMenu {

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(LzText.literal("MultiChat").mc())
                .setSavingRunnable(ConfigMenu::applyAndSave);

        ConfigEntryBuilder eb = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(LzText.literal("General").mc());

        general.addEntry(eb.startBooleanToggle(
                LzText.literal("Apply Settings on Refilter").mc(),
                MultichatClient.CONFIG.applySettingsOnRefilter)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("When ON, refiltering a tab also applies its timestamps and collapse settings to rebuilt messages.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.applySettingsOnRefilter = v)
                .build());

        general.addEntry(eb.startBooleanToggle(
                LzText.literal("Show Indicators").mc(),
                MultichatClient.CONFIG.showIndicators)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Show the colored indicator bar and icon on the left of chat messages.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.showIndicators = v)
                .build());

        general.addEntry(eb.startBooleanToggle(
                LzText.literal("Enable Text Selection").mc(),
                MultichatClient.CONFIG.selectionEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Click and drag in chat to select text. Ctrl+C to copy.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.selectionEnabled = v)
                .build());

        general.addEntry(eb.startBooleanToggle(
                LzText.literal("Ctrl Skips Click Events").mc(),
                MultichatClient.CONFIG.selectionCtrlSkipsClicks)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("When Ctrl is held, clicking on links or commands starts selection instead of activating the click.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.selectionCtrlSkipsClicks = v)
                .build());

        general.addEntry(eb.startStrField(
                LzText.literal("Timestamp Format").mc(),
                MultichatClient.CONFIG.timestampFormat)
                .setDefaultValue("&d[&6HH&d:&6mm&d:&6ss&d]")
                .setTooltip(LzText.literal("Java DateTimeFormatter pattern. Supports color codes (e.g. &7[HH:mm:ss]&r).").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.timestampFormat = v)
                .build());

        general.addEntry(eb.startStrField(
                LzText.literal("Collapse Counter Format").mc(),
                MultichatClient.CONFIG.collapseCounterFormat)
                .setDefaultValue("&e(x{count})")
                .setTooltip(LzText.literal("Format for the repeat counter on collapsed messages. Use {count} for the number. Supports color codes (e.g. &8 (x{count})).").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.collapseCounterFormat = v)
                .build());

        general.addEntry(eb.startBooleanToggle(
                LzText.literal("Show Context Display").mc(),
                MultichatClient.CONFIG.showContextDisplay)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Show the active context tab indicator above the chat input bar.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.showContextDisplay = v)
                .build());

        general.addEntry(eb.startBooleanToggle(
                LzText.literal("Arrow Keys Switch Tabs").mc(),
                MultichatClient.CONFIG.arrowKeySwitchTabs)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Arrow keys switch tabs when chat input is empty. Ctrl targets the other panel.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.arrowKeySwitchTabs = v)
                .build());

        general.addEntry(eb.startBooleanToggle(
                LzText.literal("Swap Arrow Key Panels").mc(),
                MultichatClient.CONFIG.arrowKeyCtrlForRight)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("When enabled, arrow keys switch the right panel and Ctrl+arrow switches the left panel.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.arrowKeyCtrlForRight = v)
                .build());

        general.addEntry(eb.startBooleanToggle(
                LzText.literal("Disable MultiChat").mc(),
                MultichatClient.CONFIG.modDisabled)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Disables passive features: custom chat rendering, tab routing, keybinds, and mouse hooks. Commands and the settings GUI still work.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.modDisabled = v)
                .build());

        ConfigCategory leftPanel = builder.getOrCreateCategory(LzText.literal("Left Panel").mc());

        leftPanel.addEntry(eb.startBooleanToggle(
                LzText.literal("Show Tabs").mc(),
                MultichatClient.CONFIG.leftPanelShowTabs)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Show the tab bar on the left panel.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.leftPanelShowTabs = v)
                .build());

        leftPanel.addEntry(eb.startBooleanToggle(
                LzText.literal("Tabs at Top").mc(),
                MultichatClient.CONFIG.leftPanelTabsAtTop)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Show the tab bar above messages instead of below them.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.leftPanelTabsAtTop = v)
                .build());

        leftPanel.addEntry(eb.startBooleanToggle(
                LzText.literal("Show Badges").mc(),
                MultichatClient.CONFIG.leftPanelShowBadges)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Show unread message counts on inactive tabs in the left panel.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.leftPanelShowBadges = v)
                .build());

        ConfigCategory rightPanel = builder.getOrCreateCategory(LzText.literal("Right Panel").mc());

        rightPanel.addEntry(eb.startBooleanToggle(
                LzText.literal("Enable Right Panel").mc(),
                MultichatClient.CONFIG.rightPanelEnabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Shows a second chat panel on the right side of the screen.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.rightPanelEnabled = v)
                .build());

        rightPanel.addEntry(eb.startBooleanToggle(
                LzText.literal("Show Only In Chat").mc(),
                MultichatClient.CONFIG.rightPanelShowOnlyWhenFocused)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Right panel only renders when the chat screen is open.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.rightPanelShowOnlyWhenFocused = v)
                .build());

        rightPanel.addEntry(eb.startBooleanToggle(
                LzText.literal("Show Badges").mc(),
                MultichatClient.CONFIG.rightPanelShowBadges)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Show unread message counts on inactive tabs in the right panel.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.rightPanelShowBadges = v)
                .build());

        rightPanel.addEntry(eb.startBooleanToggle(
                LzText.literal("Show Tabs").mc(),
                MultichatClient.CONFIG.rightPanelShowTabs)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Show the tab bar on the right panel.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.rightPanelShowTabs = v)
                .build());

        rightPanel.addEntry(eb.startBooleanToggle(
                LzText.literal("Tabs at Top").mc(),
                MultichatClient.CONFIG.rightPanelTabsAtTop)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Show the tab bar above messages instead of below them.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.rightPanelTabsAtTop = v)
                .build());

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
                InputUtil.fromKeyCode(MultichatClient.CONFIG.searchKeybindKey, 0))
                .setDefaultValue(InputUtil.fromKeyCode(70, 0))
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

        search.addEntry(eb.startEnumSelector(
                LzText.literal("Default: Visibility").mc(),
                TabVisibility.class,
                MultichatClient.CONFIG.searchDefaultVisibility)
                .setDefaultValue(TabVisibility.BOTH)
                .setEnumNameProvider(v -> LzText.literal(v.name()).mc())
                .setTooltip(LzText.literal("Which panel the search tab appears in by default.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchDefaultVisibility = v)
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
                .setTooltip(LzText.literal("Collapse duplicate messages in search results by default. Only the newest instance is shown with a count.").mc())
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchDefaultCollapseIdentical = v)
                .build());

        return builder.build();
    }

    private static void applyAndSave() {
        ChatRenderer.LEFT.showBadges           = MultichatClient.CONFIG.leftPanelShowBadges;
        ChatRenderer.LEFT.showTabs             = MultichatClient.CONFIG.leftPanelShowTabs;
        ChatRenderer.LEFT.tabsAtTop            = MultichatClient.CONFIG.leftPanelTabsAtTop;
        ChatRenderer.RIGHT.enabled             = MultichatClient.CONFIG.rightPanelEnabled;
        ChatRenderer.RIGHT.showOnlyWhenFocused = MultichatClient.CONFIG.rightPanelShowOnlyWhenFocused;
        ChatRenderer.RIGHT.showBadges          = MultichatClient.CONFIG.rightPanelShowBadges;
        ChatRenderer.RIGHT.showTabs            = MultichatClient.CONFIG.rightPanelShowTabs;
        ChatRenderer.RIGHT.tabsAtTop           = MultichatClient.CONFIG.rightPanelTabsAtTop;
        MultichatClient.CONFIG.save();
    }
}
