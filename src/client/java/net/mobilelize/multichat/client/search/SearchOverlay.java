package net.mobilelize.multichat.client.search;

import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.client.LzTextField;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzFormatting;
import net.mobilelize.fabriclib.text.LzStyle;
import net.mobilelize.fabriclib.text.LzText;
import net.minecraft.client.input.KeyInput;
import net.mobilelize.multichat.client.*;

public class SearchOverlay extends LzScreen {

    private static final int W          = 314;
    private static final int H_BASE     = 204;
    private static final int HEADER_STD = 22;
    private static final int HEADER_SCP = 32;

    private final Screen  parent;
    private final ChatTab sourceTab;
    private final String  initialQuery;

    private LzTextField queryField;
    private LzTextField fromField;
    private LzTextField toField;

    private SearchMode searchMode;
    private boolean    caseSensitive;
    private boolean    liveUpdate;
    private boolean    showBadge;
    private boolean    showTimestamps;
    private boolean    collapseIdentical;

    public SearchOverlay(Screen parent, ChatTab sourceTab) {
        this(parent, sourceTab, "");
    }

    public SearchOverlay(Screen parent, ChatTab sourceTab, String initialQuery) {
        super("Advanced Search");
        this.parent        = parent;
        this.sourceTab     = sourceTab;
        this.initialQuery  = initialQuery;
        this.searchMode       = MultichatClient.CONFIG.searchDefaultMode;
        this.caseSensitive    = MultichatClient.CONFIG.searchDefaultCase;
        this.liveUpdate       = MultichatClient.CONFIG.searchDefaultLive;
        this.showBadge        = MultichatClient.CONFIG.searchDefaultBadge;
        this.showTimestamps   = MultichatClient.CONFIG.searchDefaultTimestamps;
        this.collapseIdentical = MultichatClient.CONFIG.searchDefaultCollapseIdentical;
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

        queryField = addDrawableChild(new LzTextField(fx, top, fw, 20));
        queryField.setPlaceholder(LzText.literal("Search query…").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
        queryField.setMaxLength(256);
        if (!initialQuery.isEmpty()) queryField.setText(initialQuery);
        this.setFocused(queryField);

        fromField = addDrawableChild(new LzTextField(fx, top + 30, half, 20));
        fromField.setPlaceholder(LzText.literal("e.g. 10m").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
        fromField.setMaxLength(32);

        toField = addDrawableChild(new LzTextField(fx + half + 6, top + 30, half, 20));
        toField.setPlaceholder(LzText.literal("e.g. 5m").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
        toField.setMaxLength(32);

        addDrawableChild(LzButton.of("Mode: " + searchMode.name(), fx, top + 58, btnW, 20,
            b -> { searchMode = cycleMode(); b.setMessage(LzText.literal("Mode: " + searchMode.name())); }));

        addDrawableChild(LzButton.of("Case: " + (caseSensitive ? "ON" : "OFF"), fx + btnW + 4, top + 58, btnW, 20,
            b -> { caseSensitive = !caseSensitive; b.setMessage(LzText.literal("Case: " + (caseSensitive ? "ON" : "OFF"))); }));

        addDrawableChild(LzButton.of("Badge: " + (showBadge ? "ON" : "OFF"), fx, top + 84, btnW, 20,
            b -> { showBadge = !showBadge; b.setMessage(LzText.literal("Badge: " + (showBadge ? "ON" : "OFF"))); }));

        addDrawableChild(LzButton.of("Live Update: " + (liveUpdate ? "ON" : "OFF"), fx + btnW + 4, top + 84, btnW, 20,
            b -> { liveUpdate = !liveUpdate; b.setMessage(LzText.literal("Live Update: " + (liveUpdate ? "ON" : "OFF"))); }));

        addDrawableChild(LzButton.of("Timestamps: " + (showTimestamps ? "ON" : "OFF"), fx, top + 110, btnW, 20,
            b -> { showTimestamps = !showTimestamps; b.setMessage(LzText.literal("Timestamps: " + (showTimestamps ? "ON" : "OFF"))); }));

        addDrawableChild(LzButton.of("Collapse: " + (collapseIdentical ? "ON" : "OFF"), fx + btnW + 4, top + 110, btnW, 20,
            b -> { collapseIdentical = !collapseIdentical; b.setMessage(LzText.literal("Collapse: " + (collapseIdentical ? "ON" : "OFF"))); }));

        addDrawableChild(LzButton.of("Cancel",        cx - 86, top + 138, 80,  20, b -> close()));
        addDrawableChild(LzButton.of("Create Search", cx + 6,  top + 138, 100, 20, b -> createSearch()));
    }

    private SearchMode cycleMode() {
        return switch (searchMode) {
            case CONTAINS    -> SearchMode.STARTS_WITH;
            case STARTS_WITH -> SearchMode.WORD;
            case WORD        -> SearchMode.FUZZY;
            case FUZZY       -> SearchMode.REGEX;
            case REGEX       -> SearchMode.CONTAINS;
        };
    }

    private void createSearch() {
        String query     = queryField.getText().trim();
        int    fromTicks = TimeParser.parseTicks(fromField.getText().trim());
        int    toTicks   = TimeParser.parseTicks(toField.getText().trim());

        String name = "Search: " + (query.isEmpty() ? "(all)"
                      : query.length() > 14 ? query.substring(0, 14) + "…" : query);

        ChatManager.getTabs().removeIf(t -> t.type() == net.mobilelize.multichat.client.TabType.SEARCH);

        java.util.Set<Integer> allPanelIds = new java.util.LinkedHashSet<>();
        for (ChatPanel p : ChatManager.getAllPanels()) { if (p.isEnabled()) allPanelIds.add(p.id()); }

        SearchTab tab = new SearchTab(name, query, searchMode, caseSensitive,
            fromTicks, toTicks, sourceTab, liveUpdate, allPanelIds);
        tab.setShowBadge(showBadge);
        tab.setShowTimestamps(showTimestamps);
        tab.setCollapseIdentical(collapseIdentical);
        ChatManager.getTabs().add(tab);
        ChatManager.refilterSearch(tab);

        for (ChatPanel panel : ChatManager.getAllPanels()) {
            if (!panel.isEnabled()) continue;
            int idx = panel.getVisibleTabs().indexOf(tab);
            if (idx >= 0) {
                ChatTab prev = panel.getActiveTab();
                if (prev != null) tab.storePreviousTab(panel.id(), prev);
                panel.setActiveTabId(idx);
                panel.deferScrollActiveTabIntoView();
                break;
            }
        }

        close();
    }

    @Override
    public void lzRenderBackground(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        LzDraw.fill(ctx, 0, 0, width, height, Colors.OVERLAY_DARK);

        int H    = h();
        int cx   = width / 2;
        int bx   = cx - W / 2;
        int by   = height / 2 - H / 2;
        int top  = by + header();
        int fw   = W - 20;
        int fx   = cx - fw / 2;
        int half = (fw - 6) / 2;

        LzDraw.fill(ctx, bx, by, bx + W, by + H, Colors.OVERLAY);
        LzDraw.drawBorder(ctx, bx, by, bx + W, by + H, Colors.BORDER);
        LzDraw.drawCenteredText(ctx, LzText.wrapMc(title), cx, by + 7, Colors.TEXT_WHITE);

        if (sourceTab != null) {
            LzDraw.drawCenteredText(ctx,
                LzText.empty()
                    .append(LzText.literal("Scope: ").withStyle(LzStyle.ofFormatting(LzFormatting.GRAY)))
                    .append(LzText.literal(sourceTab.name()).withStyle(LzStyle.ofFormatting(LzFormatting.YELLOW))),
                cx, by + 16, Colors.TEXT_WHITE);
        }

        LzDraw.drawText(ctx, "Query", fx, top - 9, Colors.TEXT_DIMMED);
        LzDraw.drawText(ctx, "msgs older than", fx, top + 21, Colors.TEXT_DIMMED);
        LzDraw.drawText(ctx, "msgs newer than", fx + half + 6, top + 21, Colors.TEXT_DIMMED);
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        super.lzRender(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == Keys.ESCAPE) { close(); return true; }
        if (input.key() == Keys.ENTER || input.key() == Keys.NUMPAD_ENTER) { createSearch(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public void close() { LzClient.mc().setScreen(parent); }

    @Override
    public boolean shouldPause() { return false; }
}
