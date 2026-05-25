package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.MultichatClient;

import java.util.List;
import java.util.Optional;

public class PanelEditScreen {

    public static Screen create(Screen parent, ChatPanel panel) {
        String title = panel.name.isEmpty() ? String.valueOf(panel.id) : panel.name;
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(LzText.literal("Edit Panel: " + title).mc())
            .setSavingRunnable(MultichatClient::saveTabsToConfig);

        ConfigEntryBuilder eb = builder.entryBuilder();

        // ── Settings ──────────────────────────────────────────────────────
        ConfigCategory settings = builder.getOrCreateCategory(LzText.literal("Settings").mc());

        settings.addEntry(eb.startStrField(LzText.literal("Name").mc(), panel.name)
            .setDefaultValue(panel.name)
            .setTooltip(LzText.literal("Display name for this panel.").mc())
            .setSaveConsumer(v -> panel.name = v.trim().isEmpty() ? String.valueOf(panel.id) : v.trim())
            .build());

        if (!panel.primary) {
            settings.addEntry(eb.startBooleanToggle(LzText.literal("Enabled").mc(), panel.enabled)
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Show this panel.").mc())
                .setSaveConsumer(v -> panel.enabled = v)
                .build());
        }

        settings.addEntry(eb.startBooleanToggle(LzText.literal("Show Only When Focused").mc(), panel.showOnlyWhenFocused)
            .setDefaultValue(false)
            .setTooltip(LzText.literal("Only render this panel when the chat screen is open.").mc())
            .setSaveConsumer(v -> panel.showOnlyWhenFocused = v)
            .build());

        settings.addEntry(eb.startBooleanToggle(LzText.literal("Show Tabs").mc(), panel.showTabs)
            .setDefaultValue(true)
            .setTooltip(LzText.literal("Show the tab bar on this panel.").mc())
            .setSaveConsumer(v -> panel.showTabs = v)
            .build());

        settings.addEntry(eb.startBooleanToggle(LzText.literal("Tabs at Top").mc(), panel.tabsAtTop)
            .setDefaultValue(false)
            .setTooltip(LzText.literal("Show tabs above messages instead of below.").mc())
            .setSaveConsumer(v -> panel.tabsAtTop = v)
            .build());

        settings.addEntry(eb.startBooleanToggle(LzText.literal("Show Badges").mc(), panel.showBadges)
            .setDefaultValue(true)
            .setTooltip(LzText.literal("Show unread message counts on inactive tabs.").mc())
            .setSaveConsumer(v -> panel.showBadges = v)
            .build());

        settings.addEntry(eb.startColorField(LzText.literal("BG Color").mc(), panel.bgColor)
            .setDefaultValue(0)
            .setTooltip(LzText.literal("Background tint for message lines. 000000 = default black.").mc())
            .setSaveConsumer(v -> panel.bgColor = v & 0xFFFFFF)
            .build());

        // ── Tabs ──────────────────────────────────────────────────────────
        ConfigCategory tabs = builder.getOrCreateCategory(LzText.literal("Tabs").mc());
        tabs.addEntry(new LinkEntry(
            LzText.literal("Manage Tabs →").mc(),
            () -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.setScreen(new PanelTabsScreen(mc.currentScreen, panel));
            }
        ));

        // ── Position (non-primary only) ───────────────────────────────────
        if (!panel.primary) {
            ConfigCategory position = builder.getOrCreateCategory(LzText.literal("Position").mc());

            position.addEntry(eb.startEnumSelector(LzText.literal("Anchor").mc(), ChatPanel.Anchor.class, panel.anchor)
                .setDefaultValue(ChatPanel.Anchor.LEFT)
                .setTooltip(LzText.literal("LEFT/RIGHT: snaps to that side. CUSTOM: uses Custom X/Y.").mc())
                .setSaveConsumer(v -> panel.anchor = v)
                .build());

            position.addEntry(eb.startIntField(LzText.literal("Margin").mc(), panel.margin)
                .setDefaultValue(0)
                .setTooltip(LzText.literal("Offset from the anchored edge (LEFT/RIGHT anchor only).").mc())
                .setSaveConsumer(v -> panel.margin = v)
                .build());

            position.addEntry(eb.startIntField(LzText.literal("Custom X").mc(), panel.customX)
                .setDefaultValue(0)
                .setTooltip(LzText.literal("Screen pixel X when anchor is CUSTOM.").mc())
                .setSaveConsumer(v -> panel.customX = v)
                .build());

            position.addEntry(eb.startIntField(LzText.literal("Custom Y").mc(), panel.customY)
                .setDefaultValue(0)
                .setTooltip(LzText.literal("Screen pixel Y when anchor is CUSTOM.").mc())
                .setSaveConsumer(v -> panel.customY = v)
                .build());

            position.addEntry(eb.startIntField(LzText.literal("Custom Width").mc(), panel.customWidth)
                .setDefaultValue(-1)
                .setTooltip(LzText.literal("Chat width in panel units. -1 = use MC default.").mc())
                .setSaveConsumer(v -> panel.customWidth = v)
                .build());

            position.addEntry(eb.startIntField(LzText.literal("Custom Height Lines").mc(), panel.customHeightLines)
                .setDefaultValue(-1)
                .setTooltip(LzText.literal("Visible line count. -1 = use MC default.").mc())
                .setSaveConsumer(v -> panel.customHeightLines = v)
                .build());

            position.addEntry(eb.startDoubleField(LzText.literal("Custom Scale").mc(),
                    panel.customScale < 0 ? -1.0 : panel.customScale)
                .setDefaultValue(-1.0)
                .setTooltip(LzText.literal("Render scale. -1 = use MC default.").mc())
                .setSaveConsumer(v -> panel.customScale = v.floatValue())
                .build());

            position.addEntry(eb.startDoubleField(LzText.literal("BG Opacity").mc(), panel.customBgOpacity)
                .setDefaultValue(-1.0)
                .setTooltip(LzText.literal("Background opacity. -1 = use MC default.").mc())
                .setSaveConsumer(v -> panel.customBgOpacity = v)
                .build());

            position.addEntry(eb.startDoubleField(LzText.literal("Chat Opacity").mc(), panel.customChatOpacity)
                .setDefaultValue(-1.0)
                .setTooltip(LzText.literal("Text opacity. -1 = use MC default.").mc())
                .setSaveConsumer(v -> panel.customChatOpacity = v)
                .build());
        }

        return builder.build();
    }

    // Renders a centered button as a ClothConfig list entry
    private static final class LinkEntry extends AbstractConfigListEntry<Void> {
        private final ButtonWidget btn;

        LinkEntry(net.minecraft.text.Text label, Runnable action) {
            super(label, false);
            btn = ButtonWidget.builder(label, b -> action.run())
                .dimensions(0, 0, 200, 20).build();
        }

        @Override public Void getValue()                             { return null; }
        @Override public Optional<Void> getDefaultValue()           { return Optional.empty(); }
        @Override public void save()                                 {}
        @Override public boolean isEdited()                         { return false; }
        @Override public List<? extends Element>    children()      { return List.of(btn); }
        @Override public List<? extends Selectable> narratables()   { return List.of(btn); }

        @Override
        public void render(DrawContext ctx, int index, int y, int x,
                           int entryWidth, int entryHeight, int mouseX, int mouseY,
                           boolean isHovered, float delta) {
            btn.setX(x + entryWidth / 2 - 100);
            btn.setY(y + (entryHeight - 20) / 2);
            btn.render(ctx, mouseX, mouseY, delta);
        }
    }
}
