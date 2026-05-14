package net.mobilelize.multichat.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.util.math.MathHelper;
import net.mobilelize.multichat.client.render.ChatRenderer;
import net.mobilelize.multichat.client.search.SearchOverlay;
import net.mobilelize.multichat.client.ChatSelection;

import java.util.ArrayList;
import java.util.List;

public class MessageContextMenu {

    private static final int ITEM_H  = 11;
    private static final int PADDING = 4;

    private static boolean            open   = false;
    private static int                anchorX, anchorY;
    private static ChatHudLine.Visible line;
    private static ChatPanel          sourcePanel;
    private static List<String>        items;
    private static int                drawnX, drawnY;

    public static void open(ChatHudLine.Visible clicked, ChatPanel panel, int x, int y) {
        open        = true;
        line        = clicked;
        sourcePanel = panel;
        anchorX     = x;
        anchorY     = y;
        items       = buildItems(panel);
    }

    public static void close()        { open = false; }
    public static boolean isOpen()    { return open; }

    private static List<String> buildItems(ChatPanel panel) {
        List<String> list = new ArrayList<>();
        list.add("Copy");
        list.add("Search for this");
        ChatTab active = panel.getActiveTab();
        if (active != null && active.type != TabType.ALL) list.add("Jump to All");
        if (active != null && active.type != TabType.ALL) list.add("Delete message");
        return list;
    }

    public static void render(DrawContext ctx, MinecraftClient client, int mouseX, int mouseY) {
        if (!open) return;
        int menuW = menuWidth(client);
        int menuH = items.size() * ITEM_H + PADDING;
        int mx    = (anchorX + menuW > ctx.getScaledWindowWidth()) ? anchorX - menuW : anchorX;
        int my    = anchorY - menuH;
        if (my < 0) my = anchorY;
        drawnX = mx;
        drawnY = my;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400);
        ctx.fill(mx, my, mx + menuW, my + menuH, 0xDD111111);
        ctx.drawBorder(mx, my, menuW, menuH, 0xFF666666);
        for (int i = 0; i < items.size(); i++) {
            int iy = my + PADDING / 2 + i * ITEM_H;
            boolean hovered = mouseX >= mx && mouseX < mx + menuW
                           && mouseY >= iy && mouseY < iy + ITEM_H;
            if (hovered) ctx.fill(mx + 1, iy, mx + menuW - 1, iy + ITEM_H, 0x44FFFFFF);
            ctx.drawTextWithShadow(client.textRenderer, items.get(i), mx + PADDING, iy + 1, 0xFFEEEEEE);
        }
        ctx.getMatrices().pop();
    }

    public static boolean handleClick(int mouseX, int mouseY, MinecraftClient client) {
        if (!open) return false;
        int menuW = menuWidth(client);
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

        switch (items.get(itemIdx)) {
            case "Copy" -> {
                String text = extractFullMessage(line, sourcePanel);
                client.keyboard.setClipboard(text);
                flashGreen(line, sourcePanel);
            }
            case "Search for this" -> {
                String text = ChatSelection.hasSelection()
                    ? ChatSelection.getSelectedText().replace('\n', ' ').trim()
                    : extractFullMessage(line, sourcePanel).trim();
                client.setScreen(new SearchOverlay(client.currentScreen, null, text));
            }
            case "Jump to All" -> jumpToAll(client);
            case "Delete message" -> deleteMessage(sourcePanel);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Jump
    // ------------------------------------------------------------------

    private static void jumpToAll(MinecraftClient client) {
        ChatTab allTab = ChatRenderer.TABS.stream()
            .filter(t -> t.type == TabType.ALL).findFirst().orElse(null);
        if (allTab == null) return;

        // Reference equality first (works for SEARCH tabs sharing ALL's objects).
        int allIdx = allTab.messages.indexOf(line);

        // Tick + text fallback for FILTERED tabs.
        if (allIdx < 0) {
            String text = extractText(line);
            int tick = line.addedTime();
            for (int i = 0; i < allTab.messages.size(); i++) {
                ChatHudLine.Visible v = allTab.messages.get(i);
                if (v.addedTime() == tick && extractText(v).equals(text)) {
                    allIdx = i;
                    break;
                }
            }
        }
        if (allIdx < 0) return;

        List<ChatTab> panelTabs = sourcePanel.getVisibleTabs();
        int allPanelIdx = panelTabs.indexOf(allTab);
        if (allPanelIdx < 0) return;

        var chat = client.inGameHud.getChatHud();
        sourcePanel.activeTab = allPanelIdx;
        allTab.unreadCount = 0;
        int maxScroll = Math.max(0, allTab.messages.size() - chat.getVisibleLineCount());
        sourcePanel.setScrolledLines(allTab, MathHelper.clamp(allIdx, 0, maxScroll));

        // Collect full message group from the ALL tab for highlight.
        List<ChatHudLine.Visible> allMsgs = allTab.messages;
        int groupStart = allIdx;
        while (groupStart > 0 && !allMsgs.get(groupStart).endOfEntry()) groupStart--;
        int groupEnd = groupStart + 1;
        while (groupEnd < allMsgs.size() && !allMsgs.get(groupEnd).endOfEntry()) groupEnd++;
        groupEnd--;
        java.util.Set<ChatHudLine.Visible> group = new java.util.HashSet<>();
        for (int i = groupStart; i <= groupEnd; i++) group.add(allMsgs.get(i));
        ChatRenderer.highlightLines     = group;
        ChatRenderer.highlightUntilTick = ChatRenderer.currentTick + 60;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void deleteMessage(ChatPanel panel) {
        ChatTab active = panel.getActiveTab();
        if (active == null || active.type == TabType.ALL) return;
        List<ChatHudLine.Visible> msgs = active.messages;
        int idx = msgs.indexOf(line);
        if (idx < 0) return;
        int groupStart = idx;
        while (groupStart > 0 && !msgs.get(groupStart).endOfEntry()) groupStart--;
        int groupEnd = groupStart + 1;
        while (groupEnd < msgs.size() && !msgs.get(groupEnd).endOfEntry()) groupEnd++;
        groupEnd--;
        for (int i = groupEnd; i >= groupStart; i--) msgs.remove(i);
    }

    private static void flashGreen(ChatHudLine.Visible target, ChatPanel panel) {
        ChatTab active = panel.getActiveTab();
        if (active == null) return;
        List<ChatHudLine.Visible> msgs = active.messages;
        int clickedIdx = msgs.indexOf(target);
        if (clickedIdx < 0) return;

        int groupStart = clickedIdx;
        while (groupStart > 0 && !msgs.get(groupStart).endOfEntry()) groupStart--;
        int groupEnd = groupStart + 1;
        while (groupEnd < msgs.size() && !msgs.get(groupEnd).endOfEntry()) groupEnd++;
        groupEnd--;

        java.util.Set<ChatHudLine.Visible> group = new java.util.HashSet<>();
        for (int i = groupStart; i <= groupEnd; i++) group.add(msgs.get(i));
        ChatRenderer.copyHighlightLines = group;
        ChatRenderer.copyHighlightUntil = ChatRenderer.currentTick + 40;
    }

    static String extractText(ChatHudLine.Visible v) {
        StringBuilder sb = new StringBuilder();
        v.content().accept((idx, style, cp) -> { sb.appendCodePoint(cp); return true; });
        return sb.toString();
    }

    private static String extractFullMessage(ChatHudLine.Visible clicked, ChatPanel panel) {
        ChatTab active = panel.getActiveTab();
        if (active == null) return extractText(clicked);

        List<ChatHudLine.Visible> msgs = active.messages;
        int clickedIdx = msgs.indexOf(clicked);
        if (clickedIdx < 0) return extractText(clicked);

        // Groups: endOfEntry=true marks the lowest index of a group (prepend order).
        // Walk up to find group start (endOfEntry line at or before clickedIdx).
        int groupStart = clickedIdx;
        while (groupStart > 0 && !msgs.get(groupStart).endOfEntry()) groupStart--;
        // Walk down to find group end (next endOfEntry or list end).
        int groupEnd = groupStart + 1;
        while (groupEnd < msgs.size() && !msgs.get(groupEnd).endOfEntry()) groupEnd++;
        groupEnd--; // inclusive

        // Lines are stored reversed: groupStart = last visual line, groupEnd = first visual line.
        StringBuilder sb = new StringBuilder();
        for (int i = groupEnd; i >= groupStart; i--) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(extractText(msgs.get(i)));
        }
        return sb.toString();
    }

    private static int menuWidth(MinecraftClient client) {
        int max = 0;
        for (String s : items) max = Math.max(max, client.textRenderer.getWidth(s));
        return max + PADDING * 2;
    }
}
