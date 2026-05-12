package net.mobilelize.multichat.client.search;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.SearchTab;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.client.filter.MessageRouter;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.List;

public class SearchOverlay extends Screen {

    private static final int W          = 314;
    private static final int H_BASE     = 178;
    private static final int HEADER_STD = 22;   // no scope label
    private static final int HEADER_SCP = 32;   // with scope label

    private final Screen  parent;
    private final ChatTab sourceTab;
    private final String  initialQuery;

    private TextFieldWidget queryField;
    private TextFieldWidget fromField;
    private TextFieldWidget toField;

    private boolean      useRegex;
    private boolean      caseSensitive;
    private TabVisibility visibility;
    private boolean      liveUpdate;

    public SearchOverlay(Screen parent, ChatTab sourceTab) {
        this(parent, sourceTab, "");
    }

    public SearchOverlay(Screen parent, ChatTab sourceTab, String initialQuery) {
        super(Text.literal("Advanced Search"));
        this.parent        = parent;
        this.sourceTab     = sourceTab;
        this.initialQuery  = initialQuery;
        this.useRegex      = MultichatClient.CONFIG.searchDefaultRegex;
        this.caseSensitive = MultichatClient.CONFIG.searchDefaultCase;
        this.liveUpdate    = MultichatClient.CONFIG.searchDefaultLive;
        this.visibility    = MultichatClient.CONFIG.searchDefaultVisibility;
    }

    private int header() { return sourceTab != null ? HEADER_SCP : HEADER_STD; }
    private int h()      { return H_BASE + (header() - HEADER_STD); }

    @Override
    protected void init() {
        int cx   = width / 2;
        int top  = height / 2 - h() / 2 + header();
        int fw   = W - 20;
        int fx   = cx - fw / 2;
        int half = (fw - 6) / 2;
        int btnW = (fw - 4) / 2;

        queryField = addDrawableChild(new TextFieldWidget(textRenderer, fx, top, fw, 20, Text.empty()));
        queryField.setPlaceholder(Text.literal("Search query…").formatted(net.minecraft.util.Formatting.DARK_GRAY));
        queryField.setMaxLength(256);
        if (!initialQuery.isEmpty()) queryField.setText(initialQuery);
        this.setFocused(queryField);

        fromField = addDrawableChild(new TextFieldWidget(textRenderer, fx, top + 30, half, 20, Text.empty()));
        fromField.setPlaceholder(Text.literal("e.g. 10m").formatted(net.minecraft.util.Formatting.DARK_GRAY));
        fromField.setMaxLength(32);

        toField = addDrawableChild(new TextFieldWidget(textRenderer, fx + half + 6, top + 30, half, 20, Text.empty()));
        toField.setPlaceholder(Text.literal("e.g. 5m").formatted(net.minecraft.util.Formatting.DARK_GRAY));
        toField.setMaxLength(32);

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Regex: " + (useRegex ? "ON" : "OFF")),
                b -> { useRegex = !useRegex; b.setMessage(Text.literal("Regex: " + (useRegex ? "ON" : "OFF"))); })
            .dimensions(fx, top + 58, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Case: " + (caseSensitive ? "ON" : "OFF")),
                b -> { caseSensitive = !caseSensitive; b.setMessage(Text.literal("Case: " + (caseSensitive ? "ON" : "OFF"))); })
            .dimensions(fx + btnW + 4, top + 58, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Vis: " + visibility.name()),
                b -> { visibility = cycleVis(); b.setMessage(Text.literal("Vis: " + visibility.name())); })
            .dimensions(fx, top + 84, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Live Update: " + (liveUpdate ? "ON" : "OFF")),
                b -> { liveUpdate = !liveUpdate; b.setMessage(Text.literal("Live Update: " + (liveUpdate ? "ON" : "OFF"))); })
            .dimensions(fx + btnW + 4, top + 84, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
            .dimensions(cx - 86, top + 112, 80, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Create Search"), b -> createSearch())
            .dimensions(cx + 6, top + 112, 100, 20).build());
    }

    private TabVisibility cycleVis() {
        return switch (visibility) {
            case BOTH  -> TabVisibility.LEFT;
            case LEFT  -> TabVisibility.RIGHT;
            case RIGHT -> TabVisibility.BOTH;
            default    -> TabVisibility.BOTH;
        };
    }

    private void createSearch() {
        String query     = queryField.getText().trim();
        int    fromTicks = TimeParser.parseTicks(fromField.getText().trim());
        int    toTicks   = TimeParser.parseTicks(toField.getText().trim());

        String name = "Search: " + (query.isEmpty() ? "(all)"
                      : query.length() > 14 ? query.substring(0, 14) + "…" : query);

        ChatRenderer.TABS.removeIf(t -> t.type == net.mobilelize.multichat.client.TabType.SEARCH);

        SearchTab tab = new SearchTab(name, query, useRegex, caseSensitive,
            fromTicks, toTicks, sourceTab, liveUpdate, visibility);
        ChatRenderer.TABS.add(tab);
        MessageRouter.refilterSearch(tab);

        for (ChatPanel panel : List.of(ChatRenderer.LEFT, ChatRenderer.RIGHT)) {
            if (!panel.enabled) continue;
            int idx = panel.getVisibleTabs().indexOf(tab);
            if (idx >= 0) { panel.activeTab = idx; break; }
        }

        close();
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x88000000);

        int H    = h();
        int cx   = width / 2;
        int bx   = cx - W / 2;
        int by   = height / 2 - H / 2;
        int top  = by + header();
        int fw   = W - 20;
        int fx   = cx - fw / 2;
        int half = (fw - 6) / 2;

        ctx.fill(bx, by, bx + W, by + H, 0xEE111111);
        ctx.drawBorder(bx, by, W, H, 0xFF666666);
        ctx.drawCenteredTextWithShadow(textRenderer, title, cx, by + 7, 0xFFFFFF);

        if (sourceTab != null) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Scope: ").formatted(net.minecraft.util.Formatting.GRAY).append(Text.literal(sourceTab.name).formatted(net.minecraft.util.Formatting.YELLOW)), cx, by + 16, 0xFFFFFF);
        }

        ctx.drawTextWithShadow(textRenderer, Text.literal("Query"), fx, top - 9, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, Text.literal("From (older)"), fx, top + 21, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, Text.literal("To (newer)"), fx + half + 6, top + 21, 0xAAAAAA);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { close(); return true; }
        if (keyCode == 257 || keyCode == 335) { createSearch(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() { client.setScreen(parent); }

    @Override
    public boolean shouldPause() { return false; }
}
