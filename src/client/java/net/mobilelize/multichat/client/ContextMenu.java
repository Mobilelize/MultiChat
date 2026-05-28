package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.multichat.client.render.ChatRenderer;
import net.mobilelize.multichat.client.search.SearchOverlay;

import java.util.ArrayList;
import java.util.List;

public class ContextMenu {

    private static final int ITEM_H  = 11;
    private static final int PADDING = 4;

    private static boolean  open  = false;
    private static int      anchorX, anchorY;
    private static boolean  anchorRight;
    private static ChatPanel panel;
    private static ChatTab   tab;
    private static List<String> items;
    private static int       drawnX, drawnY;

    public static void open(ChatPanel p, ChatTab t, int x, int y) {
        open        = true;
        panel       = p;
        tab         = t;
        anchorX     = x;
        anchorY     = y;
        anchorRight = p.anchor == ChatPanel.Anchor.RIGHT;
        items       = buildItems(t);
    }

    public static void close() { open = false; }
    public static boolean isOpen() { return open; }

    private static List<String> buildItems(ChatTab t) {
        List<String> list = new ArrayList<>();
        if (t.type != TabType.SEARCH) list.add("Toggle Context");
        list.add("Clear Messages");
        list.add("Clear Badge");
        if (t.type != TabType.SEARCH) list.add("Toggle Badge");
        if (t.type == TabType.ALL || t.type == TabType.FILTERED) list.add("Search in tab…");
        if (t.type == TabType.SEARCH) list.add("Close Search");
        return list;
    }

    private static int computeX(int menuW) {
        return anchorRight ? anchorX - menuW : anchorX;
    }

    private static int computeY(int menuH, int screenH) {
        int my = anchorY - menuH;
        return my < 0 ? anchorY : my;
    }

    public static void render(LzDrawContext context) {
        if (!open) return;

        int menuW = menuWidth();
        int menuH = items.size() * ITEM_H + PADDING;
        int mx    = computeX(menuW);
        int my    = computeY(menuH, LzDraw.scaledHeight(context));
        drawnX = mx;
        drawnY = my;

        var mc = LzClient.mc();
        double scale = mc.getWindow().getScaleFactor();
        int mouseX = (int)(mc.mouse.getX() / scale);
        int mouseY = (int)(mc.mouse.getY() / scale);

        LzDraw.pushMatrix(context);
        LzDraw.translate(context, 0, 0, 400);

        LzDraw.fill(context, mx, my, mx + menuW, my + menuH, 0xDD111111);
        LzDraw.drawBorder(context, mx, my, mx + menuW, my + menuH, 0xFF666666);

        for (int i = 0; i < items.size(); i++) {
            int iy = my + PADDING / 2 + i * ITEM_H;
            boolean hovered = mouseX >= mx && mouseX < mx + menuW
                           && mouseY >= iy && mouseY < iy + ITEM_H;
            if (hovered) LzDraw.fill(context, mx + 1, iy, mx + menuW - 1, iy + ITEM_H, 0x44FFFFFF);
            LzDraw.drawText(context, items.get(i), mx + PADDING, iy + 1, 0xFFEEEEEE);
        }

        LzDraw.popMatrix(context);
    }

    /** Returns true if the click was consumed (hit the menu or closed it). */
    public static boolean handleClick(int mouseX, int mouseY) {
        if (!open) return false;

        int menuW = menuWidth();
        int menuH = items.size() * ITEM_H + PADDING;
        int mx    = drawnX;
        int my    = drawnY;

        if (mouseX < mx || mouseX > mx + menuW || mouseY < my || mouseY > my + menuH) {
            close();
            return false;
        }

        int relY    = mouseY - my - PADDING / 2;
        int itemIdx = relY / ITEM_H;
        close();
        if (itemIdx < 0 || itemIdx >= items.size()) return true;

        String item = items.get(itemIdx);
        switch (item) {
            case "Toggle Context" -> {
                ChatRenderer.contextTab = (ChatRenderer.contextTab == tab) ? null : tab;
                MultichatClient.saveTabsToConfig();
            }
            case "Clear Messages" -> {
                tab.messages.clear();
                tab.resetCollapse();
                for (ChatPanel p : ChatRenderer.PANELS) p.setScrolledLines(tab, 0);
                ChatSelection.clear();
            }
            case "Clear Badge"    -> tab.unreadCount = 0;
            case "Toggle Badge"   -> {
                tab.showBadge = !tab.showBadge;
                MultichatClient.saveTabsToConfig();
            }
            case "Search in tab…" -> LzClient.mc().setScreen(new SearchOverlay(LzClient.currentScreen(), tab));
            case "Close Search"   -> ChatRenderer.TABS.remove(tab);
        }
        return true;
    }

    private static int menuWidth() {
        int max = 0;
        for (String s : items) max = Math.max(max, LzDraw.textWidth(s));
        return max + PADDING * 2;
    }
}
