package net.mobilelize.multichat.client.config;

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
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.List;

public class TabListScreen extends LzScreen {

    private static final int LIST_TOP   = 32;
    private static final int ROW_HEIGHT = 22;
    private static final int SB_W       = 6;

    private final Screen parent;
    private TextFieldWidget nameField;
    private int     scrollOffset       = 0;
    private boolean scrollDragging     = false;
    private int     scrollDragStartY   = 0;
    private int     scrollDragStartOff = 0;

    public TabListScreen(Screen parent) {
        super("MultiChat — Tabs");
        this.parent = parent;
    }

    private List<ChatTab> allTabs() { return ChatRenderer.TABS; }

    private int listBottom() { return height - 62; }
    private int maxScroll()  { return Math.max(0, allTabs().size() * ROW_HEIGHT - (listBottom() - LIST_TOP)); }

    @Override
    protected void init() {
        scrollOffset = Math.min(scrollOffset, maxScroll());

        nameField = new TextFieldWidget(textRenderer, width / 2 - 100, height - 50, 158, 20, LzText.empty().mc());
        nameField.setPlaceholder(LzText.literal("New tab name...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
        this.addSelectableChild(nameField);

        addDrawableChild(ButtonWidget.builder(LzText.literal("Add").mc(), btn -> addTab())
            .dimensions(width / 2 + 62, height - 50, 40, 20).build());

        addDrawableChild(ButtonWidget.builder(LzText.literal("Settings").mc(),
                btn -> LzClient.mc().setScreen(ConfigMenu.create(this)))
            .dimensions(width / 2 - 102, height - 26, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(LzText.literal("Done").mc(), btn -> close())
            .dimensions(width / 2 + 2, height - 26, 100, 20).build());
    }

    private void addTab() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;
        if (ChatRenderer.TABS.stream().anyMatch(t -> t.type == TabType.FILTERED && t.name.equalsIgnoreCase(name))) return;
        ChatRenderer.TABS.add(new ChatTab(name, TabType.FILTERED, TabVisibility.BOTH));
        MultichatClient.saveTabsToConfig();
        clearAndInit();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        LzDraw.drawCenteredText(ctx, LzText.wrapMc(title), width / 2, 12, 0xFFFFFF);
        nameField.render(ctx, mouseX, mouseY, delta);

        LzDraw.enableScissor(ctx, 0, LIST_TOP, width, listBottom());

        List<ChatTab> tabs = allTabs();
        int lx = width / 2 - 130;

        for (int i = 0; i < tabs.size(); i++) {
            ChatTab tab = tabs.get(i);
            int y = LIST_TOP + i * ROW_HEIGHT - scrollOffset;
            if (y + ROW_HEIGHT <= LIST_TOP || y >= listBottom()) continue;

            int rowY1 = y + 1, rowY2 = y + ROW_HEIGHT - 1;
            int btnY1 = y + 3, btnY2 = y + ROW_HEIGHT - 3;
            int textY = y + (ROW_HEIGHT - 8) / 2;

            int rowBg = switch (tab.type) {
                case FILTERED -> 0x55FFFFFF;
                case SEARCH   -> 0x4400FFAA;
                case API      -> 0x44FF9900;
                default       -> 0x44AAAAFF;
            };
            int rowBdr = switch (tab.type) {
                case FILTERED -> 0x88AAAAAA;
                case SEARCH   -> 0x8800CC88;
                case API      -> 0x88CC6600;
                default       -> 0x886688CC;
            };
            LzDraw.fill(ctx, lx, rowY1, lx + 260, rowY2, rowBg);
            LzDraw.drawBorder(ctx, lx, rowY1, lx + 260, rowY2, rowBdr);

            int nameColor = switch (tab.type) {
                case FILTERED -> 0xFFFFFF;
                case SEARCH   -> 0xAAFFDD;
                case API      -> 0xFFCC88;
                default       -> 0xAADDFF;
            };
            LzDraw.drawText(ctx,
                LzText.empty()
                    .append(LzText.literal(tab.name + " "))
                    .append(LzText.literal("[" + tab.visibility.name().toLowerCase() + "]")
                        .withStyle(LzStyle.ofFormatting(LzFormatting.GRAY))),
                lx + 6, textY, nameColor);

            boolean canEdit   = tab.type == TabType.FILTERED || tab.type == TabType.API;
            boolean canRemove = tab.type == TabType.FILTERED || tab.type == TabType.SEARCH;
            boolean hoverEdit   = canEdit   && mouseX >= lx + 186 && mouseX < lx + 228 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            boolean hoverRemove = canRemove && mouseX >= lx + 231 && mouseX < lx + 259 && mouseY >= y && mouseY < y + ROW_HEIGHT;

            LzDraw.fill(ctx, lx + 186, btnY1, lx + 228, btnY2, canEdit ? (hoverEdit ? 0xAA888888 : 0x66555555) : 0x33333333);
            LzDraw.drawCenteredText(ctx, LzText.literal("Edit"), lx + 207, textY, canEdit ? 0xFFFFFF : 0x555555);

            if (canRemove) {
                LzDraw.fill(ctx, lx + 231, btnY1, lx + 259, btnY2, hoverRemove ? 0xAAFF4444 : 0x66882222);
                LzDraw.drawCenteredText(ctx, LzText.literal("X"), lx + 245, textY, 0xFFFFFF);
            }
        }

        LzDraw.disableScissor(ctx);

        int max = maxScroll();
        if (max > 0) {
            int sbX    = lx + 265;
            int trackH = listBottom() - LIST_TOP;
            int totalH = tabs.size() * ROW_HEIGHT;
            int thumbH = Math.max(20, trackH * trackH / totalH);
            int thumbY = LIST_TOP + (int)((long) scrollOffset * (trackH - thumbH) / max);

            LzDraw.fill(ctx, sbX, LIST_TOP, sbX + SB_W, listBottom(), 0x44FFFFFF);
            LzDraw.fill(ctx, sbX + 1, thumbY, sbX + SB_W - 1, thumbY + thumbH,
                scrollDragging ? 0xFFDDDDDD : 0xBBAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        int lx   = width / 2 - 130;
        int lxSb = lx + 265;
        int max  = maxScroll();
        if (button == 0 && max > 0 && mx >= lxSb && mx < lxSb + SB_W && my >= LIST_TOP && my < listBottom()) {
            int trackH = listBottom() - LIST_TOP;
            int totalH = allTabs().size() * ROW_HEIGHT;
            int thumbH = Math.max(20, trackH * trackH / totalH);
            int thumbY = LIST_TOP + (int)((long) scrollOffset * (trackH - thumbH) / max);
            if (my >= thumbY && my < thumbY + thumbH) {
                scrollDragging     = true;
                scrollDragStartY   = (int) my;
                scrollDragStartOff = scrollOffset;
            } else {
                int travel = trackH - thumbH;
                if (travel > 0)
                    scrollOffset = Math.max(0, Math.min(max,
                        (int)((my - LIST_TOP - thumbH / 2.0) * max / travel)));
            }
            return true;
        }

        if (my < LIST_TOP || my >= listBottom()) return false;

        int relY = (int)(my - LIST_TOP) + scrollOffset;
        int idx  = relY / ROW_HEIGHT;
        List<ChatTab> tabs = allTabs();
        if (idx < 0 || idx >= tabs.size()) return false;

        ChatTab tab = tabs.get(idx);

        if (mx >= lx + 186 && mx < lx + 228) {
            if (tab.type == TabType.FILTERED || tab.type == TabType.API)
                LzClient.mc().setScreen(TabEditScreen.create(this, tab));
            return true;
        }
        if ((tab.type == TabType.FILTERED || tab.type == TabType.SEARCH) && mx >= lx + 231 && mx < lx + 259) {
            ChatRenderer.TABS.remove(tab);
            if (tab.type == TabType.FILTERED) MultichatClient.saveTabsToConfig();
            clearAndInit();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (scrollDragging) {
            int max    = maxScroll();
            int trackH = listBottom() - LIST_TOP;
            int totalH = allTabs().size() * ROW_HEIGHT;
            int thumbH = Math.max(20, trackH * trackH / totalH);
            int travel = trackH - thumbH;
            if (travel > 0)
                scrollOffset = Math.max(0, Math.min(max,
                    scrollDragStartOff + (int)((my - scrollDragStartY) * max / travel)));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        scrollDragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        if (my >= LIST_TOP && my < listBottom()) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll(), scrollOffset - verticalAmount * ROW_HEIGHT));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() { LzClient.mc().setScreen(parent); }
}
