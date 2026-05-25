package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.MultichatClient;

public class PanelEditScreen {

    public static Screen create(Screen parent, ChatPanel panel) {
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(LzText.literal("Edit Panel: " + panel.id).mc())
            .setSavingRunnable(MultichatClient::saveTabsToConfig);

        ConfigEntryBuilder eb  = builder.entryBuilder();
        ConfigCategory     cat = builder.getOrCreateCategory(LzText.literal("Settings").mc());

        // Position / existence — non-primary only
        if (!panel.primary) {
            cat.addEntry(eb.startBooleanToggle(LzText.literal("Enabled").mc(), panel.enabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Show this panel.").mc())
                .setSaveConsumer(v -> panel.enabled = v)
                .build());

            cat.addEntry(eb.startEnumSelector(LzText.literal("Anchor").mc(), ChatPanel.Anchor.class, panel.anchor)
                .setDefaultValue(ChatPanel.Anchor.LEFT)
                .setTooltip(LzText.literal("LEFT/RIGHT: snaps to that side with margin. CUSTOM: uses Custom X/Y.").mc())
                .setSaveConsumer(v -> panel.anchor = v)
                .build());

            cat.addEntry(eb.startIntField(LzText.literal("Margin").mc(), panel.margin)
                .setDefaultValue(0)
                .setTooltip(LzText.literal("Offset from the anchored edge (LEFT/RIGHT anchor only).").mc())
                .setSaveConsumer(v -> panel.margin = v)
                .build());

            cat.addEntry(eb.startIntField(LzText.literal("Custom X").mc(), panel.customX)
                .setDefaultValue(0)
                .setTooltip(LzText.literal("Screen pixel X when anchor is CUSTOM.").mc())
                .setSaveConsumer(v -> panel.customX = v)
                .build());

            cat.addEntry(eb.startIntField(LzText.literal("Custom Y").mc(), panel.customY)
                .setDefaultValue(0)
                .setTooltip(LzText.literal("Screen pixel Y (from top) when anchor is CUSTOM.").mc())
                .setSaveConsumer(v -> panel.customY = v)
                .build());

            cat.addEntry(eb.startIntField(LzText.literal("Custom Width").mc(), panel.customWidth)
                .setDefaultValue(-1)
                .setTooltip(LzText.literal("Chat width in panel units. -1 = use MC default.").mc())
                .setSaveConsumer(v -> panel.customWidth = v)
                .build());

            cat.addEntry(eb.startIntField(LzText.literal("Custom Height Lines").mc(), panel.customHeightLines)
                .setDefaultValue(-1)
                .setTooltip(LzText.literal("Visible line count. -1 = use MC default.").mc())
                .setSaveConsumer(v -> panel.customHeightLines = v)
                .build());

            cat.addEntry(eb.startDoubleField(LzText.literal("Custom Scale").mc(), panel.customScale < 0 ? -1.0 : panel.customScale)
                .setDefaultValue(-1.0)
                .setTooltip(LzText.literal("Render scale. -1 = use MC default.").mc())
                .setSaveConsumer(v -> panel.customScale = v.floatValue())
                .build());

            cat.addEntry(eb.startDoubleField(LzText.literal("BG Opacity").mc(), panel.customBgOpacity)
                .setDefaultValue(-1.0)
                .setTooltip(LzText.literal("Background opacity. -1 = use MC default.").mc())
                .setSaveConsumer(v -> panel.customBgOpacity = v)
                .build());

            cat.addEntry(eb.startDoubleField(LzText.literal("Chat Opacity").mc(), panel.customChatOpacity)
                .setDefaultValue(-1.0)
                .setTooltip(LzText.literal("Text opacity. -1 = use MC default.").mc())
                .setSaveConsumer(v -> panel.customChatOpacity = v)
                .build());
        }

        // Display options — all panels
        cat.addEntry(eb.startBooleanToggle(LzText.literal("Show Only When Focused").mc(), panel.showOnlyWhenFocused)
            .setDefaultValue(false)
            .setTooltip(LzText.literal("Only render this panel when the chat screen is open.").mc())
            .setSaveConsumer(v -> panel.showOnlyWhenFocused = v)
            .build());

        cat.addEntry(eb.startBooleanToggle(LzText.literal("Show Tabs").mc(), panel.showTabs)
            .setDefaultValue(true)
            .setTooltip(LzText.literal("Show the tab bar on this panel.").mc())
            .setSaveConsumer(v -> panel.showTabs = v)
            .build());

        cat.addEntry(eb.startBooleanToggle(LzText.literal("Tabs at Top").mc(), panel.tabsAtTop)
            .setDefaultValue(false)
            .setTooltip(LzText.literal("Show tabs above messages instead of below.").mc())
            .setSaveConsumer(v -> panel.tabsAtTop = v)
            .build());

        cat.addEntry(eb.startBooleanToggle(LzText.literal("Show Badges").mc(), panel.showBadges)
            .setDefaultValue(true)
            .setTooltip(LzText.literal("Show unread message counts on inactive tabs.").mc())
            .setSaveConsumer(v -> panel.showBadges = v)
            .build());

        cat.addEntry(eb.startColorField(LzText.literal("BG Color").mc(), panel.bgColor)
            .setDefaultValue(0)
            .setTooltip(LzText.literal("Background tint for message lines. 000000 = default black.").mc())
            .setSaveConsumer(v -> panel.bgColor = v & 0xFFFFFF)
            .build());

        return builder.build();
    }
}
