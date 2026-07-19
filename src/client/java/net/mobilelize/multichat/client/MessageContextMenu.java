package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.multichat.client.quickaction.QuickActionsOverlay;
import net.mobilelize.multichat.client.render.ChatRenderer;
import net.mobilelize.multichat.client.search.SearchOverlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MessageContextMenu {

    private static final int ITEM_H  = 11;
    private static final int PADDING = 4;

    private static boolean     open   = false;
    private static int         anchorX, anchorY;
    private static LzChatLine  line;
    private static ChatPanel   sourcePanel;
    private static List<String> items;
    private static int         drawnX, drawnY;

    public static void open(LzChatLine clicked, ChatPanel panel, int x, int y) {
        open        = true;
        line        = clicked;
        sourcePanel = panel;
        anchorX     = x;
        anchorY     = y;
        items       = buildItems(panel);
    }

    public static void close()        { open = false; }
    public static boolean isOpen()    { return open; }
    public static boolean containsMouse(int mx, int my) {
        if (!open || items == null) return false;
        int menuW = menuWidth();
        int menuH = items.size() * ITEM_H + PADDING;
        return mx >= drawnX && mx <= drawnX + menuW && my >= drawnY && my <= drawnY + menuH;
    }

    private static List<String> buildItems(ChatPanel panel) {
        List<String> list = new ArrayList<>();
        list.add("Copy");
        list.add("Copy with Timestamp");
        if (ChatSelection.hasSelection()) list.add("Copy Selected");
        list.add("Search for this");
        if (ChatSelection.hasSelection()) list.add("Quick Actions...");
        if (ChatManager.activeNoteTab != null) list.add("Add to Notes");
        ChatTab active = panel.getActiveTab();
        if (active != null && active.type() != TabType.ALL && active.type() != TabType.NOTES) list.add("Jump to All");
        if (active != null && active.type() != TabType.ALL && active.type() != TabType.NOTES) list.add("Delete message");
        return list;
    }

    public static void render(LzDrawContext ctx) {
        if (!open) return;
        int menuW = menuWidth();
        int menuH = items.size() * ITEM_H + PADDING;
        int mx    = (anchorX + menuW > LzDraw.scaledWidth(ctx)) ? anchorX - menuW : anchorX;
        int my    = anchorY - menuH;
        if (my < 0) my = anchorY;
        drawnX = mx;
        drawnY = my;

        var mc = LzClient.mc();
        double scale = mc.getWindow().getScaleFactor();
        int mouseX = (int)(mc.mouse.getX() / scale);
        int mouseY = (int)(mc.mouse.getY() / scale);

        LzDraw.pushMatrix(ctx);
        LzDraw.translate(ctx, 0, 0, 400);
        LzDraw.fill(ctx, mx, my, mx + menuW, my + menuH, Colors.MENU_BG);
        LzDraw.drawBorder(ctx, mx, my, mx + menuW, my + menuH, Colors.BORDER);
        for (int i = 0; i < items.size(); i++) {
            int iy = my + PADDING / 2 + i * ITEM_H;
            boolean hovered = mouseX >= mx && mouseX < mx + menuW
                           && mouseY >= iy && mouseY < iy + ITEM_H;
            if (hovered) LzDraw.fill(ctx, mx + 1, iy, mx + menuW - 1, iy + ITEM_H, Colors.ROW_HOVER_STRONG);
            LzDraw.drawText(ctx, items.get(i), mx + PADDING, iy + 1, Colors.TEXT_LIGHT);
        }
        LzDraw.popMatrix(ctx);
    }

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
        int itemIdx = relY < 0 ? -1 : relY / ITEM_H;
        close();
        if (itemIdx < 0 || itemIdx >= items.size()) return true;

        switch (items.get(itemIdx)) {
            case "Copy" -> {
                String text = extractFullMessage(line, sourcePanel);
                LzClient.setClipboard(text);
                flashGreen(line, sourcePanel);
            }
            case "Copy with Timestamp" -> {
                String text = extractFullMessage(line, sourcePanel);
                String ts   = ChatManager.getPlainTimestamp(line.addedTime());
                LzClient.setClipboard(ts.isEmpty() ? text : ts + " " + text);
                flashGreen(line, sourcePanel);
            }
            case "Copy Selected" -> {
                String sel = ChatSelection.getSelectedText().replaceAll("\n", " ").trim();
                LzClient.setClipboard(sel);
            }
            case "Search for this" -> {
                String text = ChatSelection.hasSelection()
                    ? ChatSelection.getSelectedText().replaceAll("\n", "").trim()
                    : extractFullMessage(line, sourcePanel).trim();
                LzClient.mc().setScreen(new SearchOverlay(LzClient.currentScreen(), null, text));
            }
            case "Quick Actions..." -> {
                String sel = ChatSelection.hasSelection()
                    ? ChatSelection.getSelectedText().replaceAll("\n", " ").trim() : "";
                LzClient.mc().setScreen(new QuickActionsOverlay(LzClient.currentScreen(), sel));
            }
            case "Add to Notes" -> addToNotes();
            case "Jump to All" -> jumpToAll();
            case "Delete message" -> deleteMessage(sourcePanel);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Jump
    // ------------------------------------------------------------------

    private static void jumpToAll() {
        ChatTab allTab = ChatManager.getTabs().stream()
            .filter(t -> t.type() == TabType.ALL).findFirst().orElse(null);
        if (allTab == null) return;

        ChatPanel targetPanel = sourcePanel.getVisibleTabs().contains(allTab) ? sourcePanel
            : ChatManager.getAllPanels().stream()
                .sorted(Comparator.comparingInt(ChatPanel::id))
                .filter(p -> p.getVisibleTabs().contains(allTab))
                .findFirst().orElse(null);
        if (targetPanel == null) return;

        List<LzChatLine> allMsgs = targetPanel.getTabLines(allTab);
        if (allMsgs == null) return;
        int allIdx = allMsgs.indexOf(line);

        // Entry-reference search: shared entry means we find the exact message even when
        // multiple messages have identical tick+text.
        if (allIdx < 0 && line.entry() != null) {
            LzChatEntry target = line.entry();
            for (int i = 0; i < allMsgs.size(); i++) {
                if (allMsgs.get(i).endOfEntry() && allMsgs.get(i).entry() == target) {
                    allIdx = i;
                    break;
                }
            }
        }

        // Fallback: tick + text
        if (allIdx < 0) {
            String text = extractText(line);
            int tick = line.addedTime();
            for (int i = 0; i < allMsgs.size(); i++) {
                LzChatLine v = allMsgs.get(i);
                if (v.addedTime() == tick && extractText(v).equals(text)) { allIdx = i; break; }
            }
        }
        if (allIdx < 0) {
            int tick = line.addedTime();
            for (int i = 0; i < allMsgs.size(); i++) {
                if (allMsgs.get(i).addedTime() == tick && allMsgs.get(i).endOfEntry()) { allIdx = i; break; }
            }
        }
        if (allIdx < 0) return;

        List<ChatTab> panelTabs = targetPanel.getVisibleTabs();
        int allPanelIdx = panelTabs.indexOf(allTab);
        if (allPanelIdx < 0) return;

        targetPanel.setActiveTabId(allPanelIdx);
        targetPanel.scrollActiveTabIntoView();
        allTab.clearUnread();
        int maxScroll = Math.max(0, allMsgs.size() - targetPanel.resolveLines());
        targetPanel.setScrolledLines(allTab, LzMath.clamp(allIdx, 0, maxScroll));

        LzChatEntry targetEntry = allMsgs.get(allIdx).entry();
        if (targetEntry != null) {
            ChatRenderer.highlightEntries   = java.util.Collections.singleton(targetEntry);
            ChatRenderer.highlightUntilTick = ChatRenderer.currentTick + 60;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void deleteMessage(ChatPanel panel) {
        ChatTab active = panel.getActiveTab();
        if (active == null || active.type() == TabType.ALL) return;
        LzChatEntry targetEntry = line.entry();
        List<ChatTab.MessageEntry> messages = active.messages();
        int idx = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).entry() == targetEntry) { idx = i; break; }
        }
        if (idx < 0) return;

        java.util.Map<ChatPanel, Integer> preSizes = new java.util.HashMap<>();
        for (ChatPanel p : ChatManager.getAllPanels()) {
            if (p.getScrolledLines(active) > 0)
                { List<LzChatLine> _l = p.getTabLines(active); preSizes.put(p, _l != null ? _l.size() : 0); }
        }
        messages.remove(idx);
        for (ChatPanel p : ChatManager.getAllActivePanels()) p.rebuildTabLines(active);
        for (ChatPanel p : ChatManager.getAllPanels()) {
            int scrolled = p.getScrolledLines(active);
            if (scrolled <= 0 || !preSizes.containsKey(p)) continue;
            List<LzChatLine> _l = p.getTabLines(active);
            int removed = preSizes.get(p) - (_l != null ? _l.size() : 0);
            if (removed > 0) p.setScrolledLines(active, Math.max(0, scrolled - removed));
        }
        ChatSelection.clampOrClear(active);
    }

    private static void addToNotes() {
        NoteTab noteTab = ChatManager.activeNoteTab;
        if (noteTab == null) return;
        LzChatEntry entry = line.entry();
        net.mobilelize.fabriclib.text.LzText text = (entry != null && entry.originalContent != null)
            ? entry.originalContent
            : net.mobilelize.fabriclib.text.LzText.literal(extractText(line));
        noteTab.addNote(text, line.addedTime()); // addNote → ChatManager.addNewMessage handles scroll
        MultichatClient.saveTabsToConfig();
        flashGreen(line, sourcePanel);
    }

    private static void flashGreen(LzChatLine target, ChatPanel panel) {
        LzChatEntry entry = target.entry();
        if (entry != null) {
            ChatRenderer.copyHighlightEntries = java.util.Collections.singleton(entry);
            ChatRenderer.copyHighlightUntil   = ChatRenderer.currentTick + 40;
        }
    }

    static String extractText(LzChatLine v) {
        StringBuilder sb = new StringBuilder();
        v.content().lzAccept((idx, style, cp) -> { sb.appendCodePoint(cp); return true; });
        return sb.toString();
    }

    private static String extractFullMessage(LzChatLine clicked, ChatPanel panel) {
        if (clicked.entry() != null) return clicked.entry().originalContent.mc().getString();

        ChatTab active = panel.getActiveTab();
        if (active == null) return extractText(clicked);

        List<LzChatLine> msgs = panel.getTabLines(active);
        if (msgs == null) return extractText(clicked);
        int clickedIdx = msgs.indexOf(clicked);
        if (clickedIdx < 0) return extractText(clicked);

        int groupStart = clickedIdx;
        while (groupStart > 0 && !msgs.get(groupStart).endOfEntry()) groupStart--;
        int groupEnd = groupStart + 1;
        while (groupEnd < msgs.size() && !msgs.get(groupEnd).endOfEntry()) groupEnd++;
        groupEnd--;

        StringBuilder sb = new StringBuilder();
        for (int i = groupEnd; i >= groupStart; i--) {
            String part = extractText(msgs.get(i)).strip();
            if (!part.isEmpty()) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(part);
            }
        }
        return sb.toString();
    }

    private static int menuWidth() {
        int max = 0;
        for (String s : items) max = Math.max(max, LzDraw.textWidth(s));
        return max + PADDING * 2;
    }
}
