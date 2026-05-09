package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.render.ChatRenderer;

public class ConfigMenu {

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("MultiChat"))
                .setSavingRunnable(ConfigMenu::applyAndSave);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));

        general.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Show Unread Badges"),
                MultichatClient.CONFIG.showUnreadBadges)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Shows unread message counts on inactive tabs."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.showUnreadBadges = v)
                .build());

        ConfigCategory category = builder.getOrCreateCategory(Text.literal("Right Panel"));

        category.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Enable Right Panel"),
                MultichatClient.CONFIG.rightPanelEnabled)
                .setDefaultValue(false)
                .setTooltip(Text.literal("Shows a second chat panel on the right side of the screen."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.rightPanelEnabled = v)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Show Only In Chat"),
                MultichatClient.CONFIG.rightPanelShowOnlyWhenFocused)
                .setDefaultValue(false)
                .setTooltip(Text.literal("Right panel only renders when the chat screen is open."))
                .setSaveConsumer(v -> MultichatClient.CONFIG.rightPanelShowOnlyWhenFocused = v)
                .build());

        return builder.build();
    }

    private static void applyAndSave() {
        ChatRenderer.PANELS.get(1).enabled = MultichatClient.CONFIG.rightPanelEnabled;
        ChatRenderer.PANELS.get(1).showOnlyWhenFocused = MultichatClient.CONFIG.rightPanelShowOnlyWhenFocused;
        MultichatClient.CONFIG.save();
    }
}
