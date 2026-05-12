package net.mobilelize.multichat.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
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
    private static String[]  items;

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

    private static String[] buildItems(ChatTab t) {
        List<String> list = new ArrayList<>();
        if (t.type != TabType.SEARCH) list.add("Toggle Context");
        list.add("Clear Messages");
        list.add("Clear Badge");
        if (t.type != TabType.SEARCH) list.add("Toggle Badge");
        if (t.type == TabType.ALL || t.type == TabType.FILTERED) list.add("Search in tab…");
        if (t.type == TabType.SEARCH) list.add("Close Search");
        return list.toArray(new String[0]);
    }

    private static int computeX(int menuW) {
        return anchorRight ? anchorX - menuW : anchorX;
    }

    private static int computeY(int menuH) {
        return anchorY - menuH;
    }

    public static void render(DrawContext context, MinecraftClient client, int mouseX, int mouseY) {
        if (!open) return;

        int menuW = menuWidth(client);
        int menuH = items.length * ITEM_H + PADDING;
        int mx    = computeX(menuW);
        int my    = computeY(menuH);

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);

        context.fill(mx, my, mx + menuW, my + menuH, 0xDD111111);
        context.drawBorder(mx, my, menuW, menuH, 0xFF666666);

        for (int i = 0; i < items.length; i++) {
            int iy = my + PADDING / 2 + i * ITEM_H;
            boolean hovered = mouseX >= mx && mouseX < mx + menuW
                           && mouseY >= iy && mouseY < iy + ITEM_H;
            if (hovered) context.fill(mx + 1, iy, mx + menuW - 1, iy + ITEM_H, 0x44FFFFFF);
            context.drawTextWithShadow(client.textRenderer, items[i], mx + PADDING, iy + 1, 0xFFEEEEEE);
        }

        context.getMatrices().pop();
    }

    /** Returns true if the click was consumed (hit the menu or closed it). */
    public static boolean handleClick(int mouseX, int mouseY, MinecraftClient client) {
        if (!open) return false;

        int menuW = menuWidth(client);
        int menuH = items.length * ITEM_H + PADDING;
        int mx    = computeX(menuW);
        int my    = computeY(menuH);

        if (mouseX < mx || mouseX > mx + menuW || mouseY < my || mouseY > my + menuH) {
            close();
            return false;
        }

        int relY    = mouseY - my - PADDING / 2;
        int itemIdx = relY / ITEM_H;
        close();
        if (itemIdx < 0 || itemIdx >= items.length) return true;

        String item = items[itemIdx];
        switch (item) {
            case "Toggle Context" -> {
                ChatRenderer.contextTab = (ChatRenderer.contextTab == tab) ? null : tab;
                MultichatClient.saveTabsToConfig();
            }
            case "Clear Messages" -> { tab.messages.clear(); tab.resetCollapse(); }
            case "Clear Badge"    -> tab.unreadCount = 0;
            case "Toggle Badge"   -> {
                tab.showBadge = !tab.showBadge;
                MultichatClient.saveTabsToConfig();
            }
            case "Search in tab…" -> {
                client.setScreen(new SearchOverlay(client.currentScreen, tab));
            }
            case "Close Search" -> ChatRenderer.TABS.remove(tab);
        }
        return true;
    }

    private static int menuWidth(MinecraftClient client) {
        int max = 0;
        for (String s : items) max = Math.max(max, client.textRenderer.getWidth(s));
        return max + PADDING * 2;
    }
}
