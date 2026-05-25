package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.Keys;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PanelListScreen extends LzScreen {

    private static final int LIST_TOP   = 32;
    private static final int ROW_HEIGHT = 22;
    private static final int SB_W       = 6;

    private final Screen parent;
    private int     scrollOffset       = 0;
    private boolean scrollDragging     = false;
    private int     scrollDragStartY   = 0;
    private int     scrollDragStartOff = 0;

    public PanelListScreen(Screen parent) {
        super("MultiChat — Panels");
        this.parent = parent;
    }

    private List<ChatPanel> panels() { return ChatRenderer.PANELS; }
    private int listBottom()         { return height - 36; }
    private int maxScroll()          { return Math.max(0, panels().size() * ROW_HEIGHT - (listBottom() - LIST_TOP)); }

    @Override
    protected void init() {
        scrollOffset = Math.min(scrollOffset, maxScroll());

        addDrawableChild(ButtonWidget.builder(LzText.literal("Add Panel").mc(), btn -> addPanel())
            .dimensions(width / 2 - 102, height - 28, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(LzText.literal("Done").mc(), btn -> close())
            .dimensions(width / 2 + 4, height - 28, 98, 20).build());
    }

    private void addPanel() {
        Set<String> ids = ChatRenderer.PANELS.stream().map(p -> p.id).collect(Collectors.toSet());
        int n = 0;
        String id;
        do { id = "panel" + n++; } while (ids.contains(id));

        ChatPanel panel = new ChatPanel();
        panel.id      = id;
        panel.enabled = true;
        panel.anchor  = ChatPanel.Anchor.LEFT;
        ChatRenderer.PANELS.add(panel);
        MultichatClient.saveTabsToConfig();
        clearAndInit();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        LzDraw.drawCenteredText(ctx, LzText.wrapMc(title), width / 2, 12, 0xFFFFFF);

        int lx = width / 2 - 130;
        List<ChatPanel> panels = panels();

        LzDraw.enableScissor(ctx, 0, LIST_TOP, width, listBottom());

        for (int i = 0; i < panels.size(); i++) {
            ChatPanel panel = panels.get(i);
            int y = LIST_TOP + i * ROW_HEIGHT - scrollOffset;
            if (y + ROW_HEIGHT <= LIST_TOP || y >= listBottom()) continue;

            int rowY1 = y + 1, rowY2 = y + ROW_HEIGHT - 1;
            int btnY1 = y + 3, btnY2 = y + ROW_HEIGHT - 3;
            int textY = y + (ROW_HEIGHT - 8) / 2;

            int rowBg  = i == 0 ? 0x44AAAAFF : i == 1 ? 0x44BB88FF : 0x4488FFAA;
            int rowBdr = i == 0 ? 0x886688CC : i == 1 ? 0x88886699 : 0x8855AA77;

            LzDraw.fill(ctx, lx, rowY1, lx + 260, rowY2, rowBg);
            LzDraw.drawBorder(ctx, lx, rowY1, lx + 260, rowY2, rowBdr);

            // Status tags
            String tag = "[" + panel.anchor.name().toLowerCase() + "] "
                + (panel.enabled ? "§a[on]§r" : "§c[off]§r");
            if (panel.primary) tag = "[primary] " + tag;

            String nameStr = panel.id + " ";
            LzDraw.drawText(ctx, nameStr, lx + 6, textY, 0xFFFFFF);
            LzDraw.drawText(ctx, tag, lx + 6 + LzDraw.textWidth(nameStr), textY, 0xAAAAAA);

            // Edit button — always
            boolean hoverEdit = mouseX >= lx + 186 && mouseX < lx + 228 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            LzDraw.fill(ctx, lx + 186, btnY1, lx + 228, btnY2, hoverEdit ? 0xAA888888 : 0x66555555);
            LzDraw.drawCenteredText(ctx, LzText.literal("Edit"), lx + 207, textY, 0xFFFFFF);

            // Remove button — only for extra panels (index >= 2)
            if (i >= 2) {
                boolean hoverRemove = mouseX >= lx + 231 && mouseX < lx + 259 && mouseY >= y && mouseY < y + ROW_HEIGHT;
                LzDraw.fill(ctx, lx + 231, btnY1, lx + 259, btnY2, hoverRemove ? 0xAAFF4444 : 0x66882222);
                LzDraw.drawCenteredText(ctx, LzText.literal("X"), lx + 245, textY, 0xFFFFFF);
            }
        }

        LzDraw.disableScissor(ctx);

        // Scrollbar
        int max = maxScroll();
        if (max > 0) {
            int sbX    = lx + 265;
            int trackH = listBottom() - LIST_TOP;
            int totalH = panels.size() * ROW_HEIGHT;
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

        int lx  = width / 2 - 130;
        int lxSb = lx + 265;
        int max  = maxScroll();

        // Scrollbar
        if (button == 0 && max > 0 && mx >= lxSb && mx < lxSb + SB_W && my >= LIST_TOP && my < listBottom()) {
            int trackH = listBottom() - LIST_TOP;
            int totalH = panels().size() * ROW_HEIGHT;
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

        int relY  = (int)(my - LIST_TOP) + scrollOffset;
        int idx   = relY / ROW_HEIGHT;
        List<ChatPanel> panels = panels();
        if (idx < 0 || idx >= panels.size()) return false;

        ChatPanel panel = panels.get(idx);

        // Edit button
        if (mx >= lx + 186 && mx < lx + 228) {
            LzClient.mc().setScreen(PanelEditScreen.create(this, panel));
            return true;
        }

        // Remove button (index >= 2 only)
        if (idx >= 2 && mx >= lx + 231 && mx < lx + 259) {
            ChatRenderer.PANELS.remove(panel);
            MultichatClient.saveTabsToConfig();
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
            int totalH = panels().size() * ROW_HEIGHT;
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
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (my >= LIST_TOP && my < listBottom()) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll(), scrollOffset - v * ROW_HEIGHT));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Keys.ESCAPE) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() { LzClient.mc().setScreen(parent); }
}
