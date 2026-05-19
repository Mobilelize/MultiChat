package net.mobilelize.multichat.client.search;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.text.LzFormatting;
import net.mobilelize.fabriclib.text.LzStyle;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.SearchTab;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.client.filter.MessageRouter;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.List;

public class SearchOverlay extends LzScreen {

    private static final int W          = 314;
    private static final int H_BASE     = 178;
    private static final int HEADER_STD = 22;
    private static final int HEADER_SCP = 32;

    private final Screen  parent;
    private final ChatTab sourceTab;
    private final String  initialQuery;

    private TextFieldWidget queryField;
    private TextFieldWidget fromField;
    private TextFieldWidget toField;

    private SearchMode   searchMode;
    private boolean      caseSensitive;
    private TabVisibility visibility;
    private boolean      liveUpdate;

    public SearchOverlay(Screen parent, ChatTab sourceTab) {
        this(parent, sourceTab, "");
    }

    public SearchOverlay(Screen parent, ChatTab sourceTab, String initialQuery) {
        super("Advanced Search");
        this.parent        = parent;
        this.sourceTab     = sourceTab;
        this.initialQuery  = initialQuery;
        this.searchMode    = MultichatClient.CONFIG.searchDefaultMode;
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

        queryField = addDrawableChild(new TextFieldWidget(textRenderer, fx, top, fw, 20, LzText.empty().mc()));
        queryField.setPlaceholder(LzText.literal("Search query…").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
        queryField.setMaxLength(256);
        if (!initialQuery.isEmpty()) queryField.setText(initialQuery);
        this.setFocused(queryField);

        fromField = addDrawableChild(new TextFieldWidget(textRenderer, fx, top + 30, half, 20, LzText.empty().mc()));
        fromField.setPlaceholder(LzText.literal("e.g. 10m").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
        fromField.setMaxLength(32);

        toField = addDrawableChild(new TextFieldWidget(textRenderer, fx + half + 6, top + 30, half, 20, LzText.empty().mc()));
        toField.setPlaceholder(LzText.literal("e.g. 5m").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
        toField.setMaxLength(32);

        addDrawableChild(ButtonWidget.builder(
                LzText.literal("Mode: " + searchMode.name()).mc(),
                b -> { searchMode = cycleMode(); b.setMessage(LzText.literal("Mode: " + searchMode.name()).mc()); })
            .dimensions(fx, top + 58, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(
                LzText.literal("Case: " + (caseSensitive ? "ON" : "OFF")).mc(),
                b -> { caseSensitive = !caseSensitive; b.setMessage(LzText.literal("Case: " + (caseSensitive ? "ON" : "OFF")).mc()); })
            .dimensions(fx + btnW + 4, top + 58, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(
                LzText.literal("Vis: " + visibility.name()).mc(),
                b -> { visibility = cycleVis(); b.setMessage(LzText.literal("Vis: " + visibility.name()).mc()); })
            .dimensions(fx, top + 84, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(
                LzText.literal("Live Update: " + (liveUpdate ? "ON" : "OFF")).mc(),
                b -> { liveUpdate = !liveUpdate; b.setMessage(LzText.literal("Live Update: " + (liveUpdate ? "ON" : "OFF")).mc()); })
            .dimensions(fx + btnW + 4, top + 84, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(LzText.literal("Cancel").mc(), b -> close())
            .dimensions(cx - 86, top + 112, 80, 20).build());

        addDrawableChild(ButtonWidget.builder(LzText.literal("Create Search").mc(), b -> createSearch())
            .dimensions(cx + 6, top + 112, 100, 20).build());
    }

    private SearchMode cycleMode() {
        return switch (searchMode) {
            case CONTAINS -> SearchMode.WORD;
            case WORD     -> SearchMode.REGEX;
            case REGEX    -> SearchMode.CONTAINS;
        };
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

        SearchTab tab = new SearchTab(name, query, searchMode, caseSensitive,
            fromTicks, toTicks, sourceTab, liveUpdate, visibility);
        ChatRenderer.TABS.add(tab);
        MessageRouter.refilterSearch(tab);

        for (ChatPanel panel : List.of(ChatRenderer.LEFT, ChatRenderer.RIGHT)) {
            if (!panel.enabled) continue;
            int idx = panel.getVisibleTabs().indexOf(tab);
            if (idx >= 0) { panel.activeTab = idx; panel.scrollActiveTabIntoView(); break; }
        }

        close();
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        LzDraw.fill(ctx, 0, 0, width, height, 0x88000000);

        int H    = h();
        int cx   = width / 2;
        int bx   = cx - W / 2;
        int by   = height / 2 - H / 2;
        int top  = by + header();
        int fw   = W - 20;
        int fx   = cx - fw / 2;
        int half = (fw - 6) / 2;

        LzDraw.fill(ctx, bx, by, bx + W, by + H, 0xEE111111);
        LzDraw.drawBorder(ctx, bx, by, bx + W, by + H, 0xFF666666);
        LzDraw.drawCenteredText(ctx, LzText.wrapMc(title), cx, by + 7, 0xFFFFFF);

        if (sourceTab != null) {
            LzDraw.drawCenteredText(ctx,
                LzText.empty()
                    .append(LzText.literal("Scope: ").withStyle(LzStyle.ofFormatting(LzFormatting.GRAY)))
                    .append(LzText.literal(sourceTab.name).withStyle(LzStyle.ofFormatting(LzFormatting.YELLOW))),
                cx, by + 16, 0xFFFFFF);
        }

        LzDraw.drawText(ctx, "Query", fx, top - 9, 0xAAAAAA);
        LzDraw.drawText(ctx, "From (older)", fx, top + 21, 0xAAAAAA);
        LzDraw.drawText(ctx, "To (newer)", fx + half + 6, top + 21, 0xAAAAAA);
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
    public void close() { LzClient.mc().setScreen(parent); }

    @Override
    public boolean shouldPause() { return false; }
}
