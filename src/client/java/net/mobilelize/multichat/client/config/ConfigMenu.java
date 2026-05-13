package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.client.render.ChatRenderer;

public class ConfigMenu {

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("MultiChat"))
                .setSavingRunnable(ConfigMenu::applyAndSave);

        ConfigEntryBuilder eb = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));

        general.addEntry(eb.startBooleanToggle(
                Text.literal("Show Indicators"),
                MultichatClient.CONFIG.showIndicators)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Show the colored indicator bar and icon on the left of chat messages."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.showIndicators = v)
                .build());

        general.addEntry(eb.startBooleanToggle(
                Text.literal("Enable Text Selection"),
                MultichatClient.CONFIG.selectionEnabled)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Click and drag in chat to select text. Ctrl+C to copy."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.selectionEnabled = v)
                .build());

        general.addEntry(eb.startBooleanToggle(
                Text.literal("Ctrl Skips Click Events"),
                MultichatClient.CONFIG.selectionCtrlSkipsClicks)
                .setDefaultValue(true)
                .setTooltip(Text.literal("When Ctrl is held, clicking on links or commands starts selection instead of activating the click."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.selectionCtrlSkipsClicks = v)
                .build());

        general.addEntry(eb.startStrField(
                Text.literal("Timestamp Format"),
                MultichatClient.CONFIG.timestampFormat)
                .setDefaultValue("&d[&6HH&d:&6mm&d:&6ss&d]")
                .setTooltip(Text.literal("Java DateTimeFormatter pattern. Supports color codes (e.g. &7[HH:mm:ss]&r)."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.timestampFormat = v)
                .build());

        ConfigCategory leftPanel = builder.getOrCreateCategory(Text.literal("Left Panel"));

        leftPanel.addEntry(eb.startBooleanToggle(
                Text.literal("Show Tabs"),
                MultichatClient.CONFIG.leftPanelShowTabs)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Show the tab bar on the left panel."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.leftPanelShowTabs = v)
                .build());

        leftPanel.addEntry(eb.startBooleanToggle(
                Text.literal("Show Badges"),
                MultichatClient.CONFIG.leftPanelShowBadges)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Show unread message counts on inactive tabs in the left panel."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.leftPanelShowBadges = v)
                .build());

        ConfigCategory rightPanel = builder.getOrCreateCategory(Text.literal("Right Panel"));

        rightPanel.addEntry(eb.startBooleanToggle(
                Text.literal("Enable Right Panel"),
                MultichatClient.CONFIG.rightPanelEnabled)
                .setDefaultValue(false)
                .setTooltip(Text.literal("Shows a second chat panel on the right side of the screen."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.rightPanelEnabled = v)
                .build());

        rightPanel.addEntry(eb.startBooleanToggle(
                Text.literal("Show Only In Chat"),
                MultichatClient.CONFIG.rightPanelShowOnlyWhenFocused)
                .setDefaultValue(false)
                .setTooltip(Text.literal("Right panel only renders when the chat screen is open."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.rightPanelShowOnlyWhenFocused = v)
                .build());

        rightPanel.addEntry(eb.startBooleanToggle(
                Text.literal("Show Badges"),
                MultichatClient.CONFIG.rightPanelShowBadges)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Show unread message counts on inactive tabs in the right panel."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.rightPanelShowBadges = v)
                .build());

        rightPanel.addEntry(eb.startBooleanToggle(
                Text.literal("Show Tabs"),
                MultichatClient.CONFIG.rightPanelShowTabs)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Show the tab bar on the right panel."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.rightPanelShowTabs = v)
                .build());

        ConfigCategory search = builder.getOrCreateCategory(Text.literal("Search"));

        search.addEntry(eb.startBooleanToggle(
                Text.literal("Enable Search Keybind"),
                MultichatClient.CONFIG.searchKeybindEnabled)
                .setDefaultValue(true)
                .setTooltip(Text.literal("When disabled, the search keybind does nothing."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchKeybindEnabled = v)
                .build());

        search.addEntry(eb.startKeyCodeField(
                Text.literal("Search Key"),
                InputUtil.fromKeyCode(MultichatClient.CONFIG.searchKeybindKey, 0))
                .setDefaultValue(InputUtil.fromKeyCode(70, 0))
                .setAllowModifiers(false)
                .setTooltip(Text.literal("Key to open search (combine with Require Ctrl for e.g. Ctrl+F)."))
                .setKeySaveConsumer(key -> MultichatClient.CONFIG.searchKeybindKey = key.getCode())
                .build());

        search.addEntry(eb.startBooleanToggle(
                Text.literal("Require Ctrl"),
                MultichatClient.CONFIG.searchKeybindRequireCtrl)
                .setDefaultValue(true)
                .setTooltip(Text.literal("When on, Ctrl must be held with the search key. Off = single key only."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchKeybindRequireCtrl = v)
                .build());

        search.addEntry(eb.startBooleanToggle(
                Text.literal("Default: Regex"),
                MultichatClient.CONFIG.searchDefaultRegex)
                .setDefaultValue(false)
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchDefaultRegex = v)
                .build());

        search.addEntry(eb.startBooleanToggle(
                Text.literal("Default: Case Sensitive"),
                MultichatClient.CONFIG.searchDefaultCase)
                .setDefaultValue(false)
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchDefaultCase = v)
                .build());

        search.addEntry(eb.startBooleanToggle(
                Text.literal("Default: Live Update"),
                MultichatClient.CONFIG.searchDefaultLive)
                .setDefaultValue(true)
                .setTooltip(Text.literal("When on, new messages matching the query are added to the search tab in real-time."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchDefaultLive = v)
                .build());

        search.addEntry(eb.startEnumSelector(
                Text.literal("Default: Visibility"),
                TabVisibility.class,
                MultichatClient.CONFIG.searchDefaultVisibility)
                .setDefaultValue(TabVisibility.BOTH)
                .setEnumNameProvider(v -> Text.literal(v.name()))
                .setTooltip(Text.literal("Which panel the search tab appears in by default."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.searchDefaultVisibility = v)
                .build());

        return builder.build();
    }

    private static void applyAndSave() {
        ChatRenderer.LEFT.showBadges           = MultichatClient.CONFIG.leftPanelShowBadges;
        ChatRenderer.LEFT.showTabs             = MultichatClient.CONFIG.leftPanelShowTabs;
        ChatRenderer.RIGHT.enabled             = MultichatClient.CONFIG.rightPanelEnabled;
        ChatRenderer.RIGHT.showOnlyWhenFocused = MultichatClient.CONFIG.rightPanelShowOnlyWhenFocused;
        ChatRenderer.RIGHT.showBadges          = MultichatClient.CONFIG.rightPanelShowBadges;
        ChatRenderer.RIGHT.showTabs            = MultichatClient.CONFIG.rightPanelShowTabs;
        MultichatClient.CONFIG.save();
    }
}
