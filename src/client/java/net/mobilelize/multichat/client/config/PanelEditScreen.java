package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatManager;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.MultichatClient;

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
                java.util.Set<String> others = ChatManager.getAllPanels().stream()
                    .filter(p -> p != panel).map(ChatPanel::name).collect(java.util.stream.Collectors.toSet());
                String unique = trimmed;
                int n = 2;
                while (others.contains(unique)) unique = trimmed + " (" + n++ + ")";
                panel.setName(unique);
            })
            .build());

        if (!panel.primary()) {
            settings.addEntry(eb.startBooleanToggle(LzText.literal("Enabled").mc(), panel.isEnabled())
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

        settings.addEntry(eb.startIntSlider(LzText.literal("Tab BG Opacity").mc(), MultiChatConfig.pct(panel.tabBgOpacity()), -1, 100)
            .setDefaultValue(-1)
            .setTooltip(LzText.literal("Tab background opacity (0-100%). -1 = use default.").mc())
            .setSaveConsumer(v -> panel.setTabBgOpacity(MultiChatConfig.fromPct(v)))
            .build());

        settings.addEntry(eb.startIntSlider(LzText.literal("Tab Text Opacity").mc(), MultiChatConfig.pct(panel.tabFgOpacity()), -1, 100)
            .setDefaultValue(-1)
            .setTooltip(LzText.literal("Tab text opacity (0-100%). -1 = use default.").mc())
            .setSaveConsumer(v -> panel.setTabFgOpacity(MultiChatConfig.fromPct(v)))
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
                .setTooltip(LzText.literal("Visible line count when focused. -1 = use MC default.").mc())
                .setSaveConsumer(panel::setCustomHeightLines)
                .build());

            position.addEntry(eb.startBooleanToggle(LzText.literal("Lock Unfocused to Focused").mc(), panel.lockUnfocusedToFocused())
                .setDefaultValue(true)
                .setTooltip(LzText.literal("When ON, unfocused height matches focused height. Turn OFF to set a separate unfocused line count.").mc())
                .setSaveConsumer(panel::setLockUnfocusedToFocused)
                .build());

            position.addEntry(eb.startIntField(LzText.literal("Unfocused Height Lines").mc(), panel.customUnfocusedHeightLines())
                .setDefaultValue(-1)
                .setTooltip(LzText.literal("Line count when chat is closed. Only used when Lock Unfocused to Focused is OFF. -1 = use MC default.").mc())
                .setSaveConsumer(panel::setCustomUnfocusedHeightLines)
                .build());

            position.addEntry(eb.startIntSlider(LzText.literal("Custom Scale").mc(), MultiChatConfig.pct(panel.customScale()), -1, 100)
                .setDefaultValue(-1)
                .setTooltip(LzText.literal("Render scale (0-100% where 100 = 1.0x). -1 = use MC default.").mc())
                .setSaveConsumer(v -> panel.setCustomScale(v < 0 ? -1f : v / 100.0f))
                .build());

            position.addEntry(eb.startIntSlider(LzText.literal("BG Opacity").mc(), MultiChatConfig.pct(panel.customBgOpacity()), -1, 100)
                .setDefaultValue(-1)
                .setTooltip(LzText.literal("Background opacity (0-100%). -1 = use MC default.").mc())
                .setSaveConsumer(v -> panel.setCustomBgOpacity(MultiChatConfig.fromPct(v)))
                .build());

            position.addEntry(eb.startIntSlider(LzText.literal("Text Opacity").mc(), MultiChatConfig.pct(panel.customChatOpacity()), -1, 100)
                .setDefaultValue(-1)
                .setTooltip(LzText.literal("Text opacity (0-100%). -1 = use MC default.").mc())
                .setSaveConsumer(v -> panel.setCustomChatOpacity(MultiChatConfig.fromPct(v)))
                .build());
        }

        return builder.build();
    }

}
