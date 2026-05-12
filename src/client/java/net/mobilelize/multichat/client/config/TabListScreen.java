package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.List;

public class TabListScreen extends Screen {

    private static final int LIST_TOP   = 32;
    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private TextFieldWidget nameField;
    private int scrollOffset = 0;

    public TabListScreen(Screen parent) {
        super(Text.literal("MultiChat — Tabs"));
        this.parent = parent;
    }

    private List<ChatTab> filteredTabs() {
        return ChatRenderer.TABS.stream().filter(t -> t.type == TabType.FILTERED).toList();
    }

    private int listBottom()  { return height - 62; }
    private int maxScroll()   { return Math.max(0, filteredTabs().size() * ROW_HEIGHT - (listBottom() - LIST_TOP)); }

    @Override
    protected void init() {
        scrollOffset = Math.min(scrollOffset, maxScroll());

        nameField = new TextFieldWidget(textRenderer, width / 2 - 100, height - 50, 158, 20, Text.empty());
        nameField.setPlaceholder(Text.literal("New tab name..."));
        this.addSelectableChild(nameField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Add"), btn -> addTab())
            .dimensions(width / 2 + 62, height - 50, 40, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Settings"),
                btn -> client.setScreen(ConfigMenu.create(this)))
            .dimensions(width / 2 - 154, height - 26, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> close())
            .dimensions(width / 2 - 50, height - 26, 100, 20).build());
    }

    private void addTab() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;
        if (ChatRenderer.TABS.stream().anyMatch(t -> t.name.equalsIgnoreCase(name))) return;
        ChatRenderer.TABS.add(new ChatTab(name, TabType.FILTERED, TabVisibility.BOTH));
        MultichatClient.saveTabsToConfig();
        clearAndInit();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        nameField.render(ctx, mouseX, mouseY, delta);

        ctx.enableScissor(0, LIST_TOP, width, listBottom());

        List<ChatTab> tabs = filteredTabs();
        int lx = width / 2 - 130;

        for (int i = 0; i < tabs.size(); i++) {
            ChatTab tab = tabs.get(i);
            int y = LIST_TOP + i * ROW_HEIGHT - scrollOffset;
            if (y + ROW_HEIGHT <= LIST_TOP || y >= listBottom()) continue;

            ctx.fill(lx, y, lx + 260, y + ROW_HEIGHT - 2, 0x44FFFFFF);

            ctx.drawTextWithShadow(textRenderer,
                Text.literal(tab.name + " §7[" + tab.visibility.name().toLowerCase() + "]"),
                lx + 4, y + 5, 0xFFFFFF);

            boolean hoverEdit   = mouseX >= lx + 188 && mouseX < lx + 230 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            boolean hoverRemove = mouseX >= lx + 232 && mouseX < lx + 260 && mouseY >= y && mouseY < y + ROW_HEIGHT;

            ctx.fill(lx + 188, y + 2, lx + 230, y + ROW_HEIGHT - 2, hoverEdit   ? 0xAA888888 : 0x55666666);
            ctx.fill(lx + 232, y + 2, lx + 260, y + ROW_HEIGHT - 2, hoverRemove ? 0xAAFF4444 : 0x55AA2222);

            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Edit"), lx + 209, y + 5, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("X"),    lx + 246, y + 5, 0xFFFFFF);
        }

        ctx.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (my < LIST_TOP || my >= listBottom()) return false;

        int lx   = width / 2 - 130;
        int relY = (int)(my - LIST_TOP) + scrollOffset;
        int idx  = relY / ROW_HEIGHT;
        List<ChatTab> tabs = filteredTabs();
        if (idx < 0 || idx >= tabs.size()) return false;

        ChatTab tab = tabs.get(idx);

        if (mx >= lx + 188 && mx < lx + 230) {
            client.setScreen(TabEditScreen.create(this, tab));
            return true;
        }
        if (mx >= lx + 232 && mx < lx + 260) {
            ChatRenderer.TABS.remove(tab);
            MultichatClient.saveTabsToConfig();
            clearAndInit();
            return true;
        }
        return false;
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
    public void close() {
        client.setScreen(parent);
    }
}
