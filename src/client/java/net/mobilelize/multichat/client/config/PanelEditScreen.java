package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.render.ChatRenderer;

public class PanelEditScreen {

    public static Screen create(Screen parent, ChatPanel panel) {
        String title = panel.name().isEmpty() ? String.valueOf(panel.id()) : panel.name();
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(LzText.literal("Edit Panel: " + title).mc())
            .setSavingRunnable(MultichatClient::saveTabsToConfig);

        ConfigEntryBuilder eb = builder.entryBuilder();

        ConfigCategory settings = builder.getOrCreateCategory(LzText.literal("Settings").mc());

        settings.addEntry(eb.startStrField(LzText.literal("Name").mc(), panel.name())
            .setDefaultValue(panel.name())
            .setTooltip(LzText.literal("Display name for this panel.").mc())
            .setSaveConsumer(v -> {
                String trimmed = v.trim().isEmpty() ? String.valueOf(panel.id()) : v.trim();
                java.util.Set<String> others = ChatRenderer.PANELS.stream()
                    .filter(p -> p != panel).map(ChatPanel::name).collect(java.util.stream.Collectors.toSet());
                String unique = trimmed;
                int n = 2;
                while (others.contains(unique)) unique = trimmed + " (" + n++ + ")";
                panel.setName(unique);
            })
            .build());

        if (!panel.primary()) {
            settings.addEntry(eb.startBooleanToggle(LzText.literal("Enabled").mc(), panel.enabled())
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Show this panel.").mc())
                .setSaveConsumer(panel::setEnabled)
                .build());
        }

        settings.addEntry(eb.startBooleanToggle(LzText.literal("Show Only When Focused").mc(), panel.showOnlyWhenFocused())
            .setDefaultValue(false)
            .setTooltip(LzText.literal("Only render this panel when the chat screen is open.").mc())
            .setSaveConsumer(panel::setShowOnlyWhenFocused)
            .build());

        settings.addEntry(eb.startBooleanToggle(LzText.literal("Show Tabs").mc(), panel.showTabs())
            .setDefaultValue(true)
            .setTooltip(LzText.literal("Show the tab bar on this panel.").mc())
            .setSaveConsumer(panel::setShowTabs)
            .build());

        settings.addEntry(eb.startBooleanToggle(LzText.literal("Tabs at Top").mc(), panel.tabsAtTop())
            .setDefaultValue(false)
            .setTooltip(LzText.literal("Show tabs above messages instead of below.").mc())
            .setSaveConsumer(panel::setTabsAtTop)
            .build());

        settings.addEntry(eb.startBooleanToggle(LzText.literal("Show Badges").mc(), panel.showBadges())
            .setDefaultValue(true)
            .setTooltip(LzText.literal("Show unread message counts on inactive tabs.").mc())
            .setSaveConsumer(panel::setShowBadges)
            .build());

        settings.addEntry(eb.startBooleanToggle(LzText.literal("Active Tab as Context").mc(), panel.useActiveTabContext())
            .setDefaultValue(false)
            .setTooltip(LzText.literal("Use this panel's active tab as its context tab.").mc())
            .setSaveConsumer(panel::setUseActiveTabContext)
            .build());

        settings.addEntry(eb.startColorField(LzText.literal("BG Color").mc(), panel.bgColor())
            .setDefaultValue(0)
            .setTooltip(LzText.literal("Background tint for message lines. 000000 = default black.").mc())
            .setSaveConsumer(panel::setBgColor)
            .build());

        if (!panel.primary()) {
            ConfigCategory position = builder.getOrCreateCategory(LzText.literal("Adjustments").mc());

            position.addEntry(eb.startEnumSelector(LzText.literal("Anchor").mc(), ChatPanel.Anchor.class, panel.anchor())
                .setDefaultValue(ChatPanel.Anchor.LEFT)
                .setTooltip(LzText.literal("LEFT/RIGHT: snaps to that wall. CUSTOM: position set via Panel Layout editor.").mc())
                .setSaveConsumer(panel::setAnchor)
                .build());

            position.addEntry(eb.startIntField(LzText.literal("Margin").mc(), panel.margin())
                .setDefaultValue(0)
                .setTooltip(LzText.literal("Offset from the anchored edge (LEFT/RIGHT anchor only).").mc())
                .setSaveConsumer(panel::setMargin)
                .build());

            position.addEntry(eb.startIntField(LzText.literal("Custom Width").mc(), panel.customWidth())
                .setDefaultValue(-1)
                .setTooltip(LzText.literal("Chat width in panel units. -1 = use MC default.").mc())
                .setSaveConsumer(panel::setCustomWidth)
                .build());

            position.addEntry(eb.startIntField(LzText.literal("Custom Height Lines").mc(), panel.customHeightLines())
                .setDefaultValue(-1)
                .setTooltip(LzText.literal("Visible line count. -1 = use MC default.").mc())
                .setSaveConsumer(panel::setCustomHeightLines)
                .build());

            position.addEntry(eb.startDoubleField(LzText.literal("Custom Scale").mc(),
                    panel.customScale() < 0 ? -1.0 : panel.customScale())
                .setDefaultValue(-1.0)
                .setTooltip(LzText.literal("Render scale. -1 = use MC default.").mc())
                .setSaveConsumer(v -> panel.setCustomScale(v.floatValue()))
                .build());

            position.addEntry(eb.startDoubleField(LzText.literal("BG Opacity").mc(), panel.customBgOpacity())
                .setDefaultValue(-1.0)
                .setTooltip(LzText.literal("Background opacity. -1 = use MC default.").mc())
                .setSaveConsumer(panel::setCustomBgOpacity)
                .build());

            position.addEntry(eb.startDoubleField(LzText.literal("Chat Opacity").mc(), panel.customChatOpacity())
                .setDefaultValue(-1.0)
                .setTooltip(LzText.literal("Text opacity. -1 = use MC default.").mc())
                .setSaveConsumer(panel::setCustomChatOpacity)
                .build());
        }

        return builder.build();
    }
}
