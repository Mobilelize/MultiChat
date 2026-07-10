package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.multichat.client.config.NoteEditorScreen;
import net.mobilelize.multichat.client.config.RenameTabScreen;
import net.mobilelize.multichat.client.config.TabEditScreen;
import net.mobilelize.multichat.client.search.SearchOverlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContextMenu {

    // ── Item ID constants (used in hiddenContextMenuItems config) ─────────────
    public static final String ID_TOGGLE_CONTEXT    = "toggle_context";
    public static final String ID_CLEAR_MESSAGES    = "clear_messages";
    public static final String ID_CLEAR_BADGE       = "clear_badge";
    public static final String ID_TOGGLE_BADGE      = "toggle_badge";
    public static final String ID_SEARCH_IN_TAB     = "search_in_tab";
    public static final String ID_EDIT_TAB          = "edit_tab";
    public static final String ID_RENAME_TAB        = "rename_tab";
    public static final String ID_SET_ACTIVE_NOTE   = "set_active_note";
    public static final String ID_TOGGLE_TIMESTAMPS = "toggle_timestamps";
    public static final String ID_TOGGLE_COLLAPSE   = "toggle_collapse";
    public static final String ID_SCROLL_TOP        = "scroll_top";
    public static final String ID_SCROLL_BOTTOM     = "scroll_bottom";
    public static final String ID_EXPORT_ALL        = "export_all";
    public static final String ID_MOVE_LEFT         = "move_left";
    public static final String ID_MOVE_RIGHT        = "move_right";

    private static final int ITEM_H  = 11;
    private static final int PADDING = 4;

    private static boolean      open  = false;
    private static int          anchorX, anchorY;
    private static boolean      anchorRight;
    private static ChatPanel    panel;
    private static ChatTab      tab;
    private static int          tabIndex = -1;
    private static List<String> items;
    private static int          drawnX, drawnY;

    public static void open(ChatPanel p, ChatTab t, int x, int y) {
        open        = true;
        panel       = p;
        tab         = t;
        tabIndex    = ChatManager.getTabs().indexOf(t);
        anchorX     = x;
        anchorY     = y;
        anchorRight = p.anchor() == ChatPanel.Anchor.RIGHT;
        items       = buildItems(t);
    }

    public static void close()        { open = false; }
    public static boolean isOpen()    { return open; }
    public static boolean containsMouse(int mx, int my) {
        if (!open || items == null) return false;
        int menuW = menuWidth();
        int menuH = items.size() * ITEM_H + PADDING;
        return mx >= drawnX && mx <= drawnX + menuW && my >= drawnY && my <= drawnY + menuH;
    }

    // ── All item IDs in display order, for use in settings screen ─────────────
    public static final String[][] ALL_ITEMS = {
        { ID_TOGGLE_CONTEXT,    "Toggle Context"      },
        { ID_CLEAR_MESSAGES,    "Clear Messages"      },
        { ID_CLEAR_BADGE,       "Clear Badge"         },
        { ID_TOGGLE_BADGE,      "Toggle Badge"        },
        { ID_SEARCH_IN_TAB,     "Search in tab…"      },
        { ID_EDIT_TAB,          "Edit Tab"            },
        { ID_RENAME_TAB,        "Rename Tab"          },
        { ID_SET_ACTIVE_NOTE,   "Set Active Note Tab" },
        { ID_TOGGLE_TIMESTAMPS, "Toggle Timestamps"   },
        { ID_TOGGLE_COLLAPSE,   "Toggle Collapse"     },
        { ID_SCROLL_TOP,        "Scroll to Top"       },
        { ID_SCROLL_BOTTOM,     "Scroll to Bottom"    },
        { ID_EXPORT_ALL,        "Export All"          },
        { ID_MOVE_LEFT,         "Move Left"           },
        { ID_MOVE_RIGHT,        "Move Right"          },
    };

    private static boolean shown(String id) {
        return !MultichatClient.CONFIG.hiddenContextMenuItems.contains(id);
    }

    private static boolean isMoveable(ChatTab t) {
        return t.type() == TabType.FILTERED || t.type() == TabType.API
            || t.type() == TabType.SEARCH   || t.type() == TabType.NOTES;
    }

    private static int firstMoveableIdx() {
        for (int i = 0; i < ChatManager.getTabs().size(); i++)
            if (isMoveable(ChatManager.getTabs().get(i))) return i;
        return ChatManager.getTabs().size();
    }

    private static List<String> buildItems(ChatTab t) {
        List<String> list = new ArrayList<>();

        if (t.type() != TabType.SEARCH && t.type() != TabType.NOTES && shown(ID_TOGGLE_CONTEXT))
            list.add("Toggle Context");

        if (t.type() != TabType.NOTES && shown(ID_CLEAR_MESSAGES))
            list.add("Clear Messages");

        if (shown(ID_CLEAR_BADGE))   list.add("Clear Badge");

        if (t.type() != TabType.SEARCH && t.type() != TabType.NOTES && shown(ID_TOGGLE_BADGE))
            list.add("Toggle Badge");

        if ((t.type() == TabType.ALL || t.type() == TabType.FILTERED) && shown(ID_SEARCH_IN_TAB))
            list.add("Search in tab…");

        if ((t.type() == TabType.FILTERED || t.type() == TabType.API
                || t.type() == TabType.ALL || t.type() == TabType.NOTES) && shown(ID_EDIT_TAB))
            list.add("Edit Tab");

        if ((t.type() == TabType.FILTERED || t.type() == TabType.API
                || t.type() == TabType.NOTES) && shown(ID_RENAME_TAB))
            list.add("Rename Tab");

        if (t.type() == TabType.NOTES && shown(ID_SET_ACTIVE_NOTE))
            list.add(ChatManager.activeNoteTab == t ? "★ Active Note Tab" : "Set Active Note Tab");

        if ((t.type() == TabType.FILTERED || t.type() == TabType.API) && shown(ID_TOGGLE_TIMESTAMPS))
            list.add(t.showTimestamps() ? "Timestamps: ON" : "Timestamps: OFF");

        if ((t.type() == TabType.FILTERED || t.type() == TabType.API) && shown(ID_TOGGLE_COLLAPSE))
            list.add(t.collapseRepeating() ? "Collapse: ON" : "Collapse: OFF");

        if (shown(ID_SCROLL_TOP))    list.add("Scroll to Top");
        if (shown(ID_SCROLL_BOTTOM)) list.add("Scroll to Bottom");
        if (shown(ID_EXPORT_ALL))    list.add("Export All");

        if (isMoveable(t) && tabIndex > firstMoveableIdx() && shown(ID_MOVE_LEFT))
            list.add("Move Left");
        if (isMoveable(t) && tabIndex < ChatManager.getTabs().size() - 1 && shown(ID_MOVE_RIGHT))
            list.add("Move Right");

        // Close Search is never toggleable — it's the only way to close a search tab
        if (t.type() == TabType.SEARCH) list.add("Close Search");

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

        LzDraw.fill(context, mx, my, mx + menuW, my + menuH, Colors.MENU_BG);
        LzDraw.drawBorder(context, mx, my, mx + menuW, my + menuH, Colors.BORDER);

        for (int i = 0; i < items.size(); i++) {
            int iy = my + PADDING / 2 + i * ITEM_H;
            boolean hovered = mouseX >= mx && mouseX < mx + menuW
                           && mouseY >= iy && mouseY < iy + ITEM_H;
            if (hovered) LzDraw.fill(context, mx + 1, iy, mx + menuW - 1, iy + ITEM_H, Colors.ROW_HOVER_STRONG);
            LzDraw.drawText(context, items.get(i), mx + PADDING, iy + 1, Colors.TEXT_LIGHT);
        }

        LzDraw.popMatrix(context);
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

        String item = items.get(itemIdx);
        switch (item) {
            case "Toggle Context" -> {
                ChatManager.contextTab = (ChatManager.contextTab == tab) ? null : tab;
                MultichatClient.saveTabsToConfig();
            }
            case "Clear Messages" -> {
                tab.clearMessages();
                for (ChatPanel p : ChatManager.getAllPanels()) p.setScrolledLines(tab, 0);
                ChatSelection.clear();
            }
            case "Clear Badge"  -> tab.clearUnread();
            case "Toggle Badge" -> {
                tab.setShowBadge(!tab.showBadge());
                MultichatClient.saveTabsToConfig();
            }
            case "Search in tab…" -> LzClient.mc().setScreen(new SearchOverlay(LzClient.currentScreen(), tab));
            case "Edit Tab" -> {
                var parent = LzClient.currentScreen();
                if (tab.type() == TabType.NOTES)
                    LzClient.mc().setScreen(new NoteEditorScreen(parent, (NoteTab) tab));
                else
                    LzClient.mc().setScreen(TabEditScreen.create(parent, tab));
            }
            case "Rename Tab" ->
                LzClient.mc().setScreen(new RenameTabScreen(LzClient.currentScreen(), tab));
            case "Set Active Note Tab", "★ Active Note Tab" -> {
                ChatManager.activeNoteTab = (NoteTab) tab;
                MultichatClient.saveTabsToConfig();
            }
            case "Timestamps: ON", "Timestamps: OFF" -> {
                tab.setShowTimestamps(!tab.showTimestamps());
                MultichatClient.saveTabsToConfig();
            }
            case "Collapse: ON", "Collapse: OFF" -> {
                tab.setCollapseRepeating(!tab.collapseRepeating());
                MultichatClient.saveTabsToConfig();
            }
            case "Scroll to Top" -> {
                List<LzChatLine> msgs = panel.getTabLines(tab);
                int max = Math.max(0, (msgs != null ? msgs.size() : 0) - panel.resolveLines());
                panel.setScrolledLines(tab, max);
            }
            case "Scroll to Bottom" -> panel.setScrolledLines(tab, 0);
            case "Export All" -> {
                List<LzChatLine> msgs = panel.getTabLines(tab);
                if (msgs == null) break;
                StringBuilder sb = new StringBuilder();
                for (int i = msgs.size() - 1; i >= 0; i--) {
                    LzChatLine l = msgs.get(i);
                    if (!l.endOfEntry()) continue;
                    String text = l.entry() != null && l.entry().originalContent != null
                        ? l.entry().originalContent.mc().getString()
                        : MessageContextMenu.extractText(l);
                    if (!sb.isEmpty()) sb.append('\n');
                    sb.append(text);
                }
                if (!sb.isEmpty()) LzClient.setClipboard(sb.toString());
            }
            case "Move Left" -> {
                if (tabIndex > 0 && tabIndex < ChatManager.getTabs().size()) {
                    Collections.swap(ChatManager.getTabs(), tabIndex, tabIndex - 1);
                    MultichatClient.saveTabsToConfig();
                }
            }
            case "Move Right" -> {
                if (tabIndex >= 0 && tabIndex < ChatManager.getTabs().size() - 1) {
                    Collections.swap(ChatManager.getTabs(), tabIndex, tabIndex + 1);
                    MultichatClient.saveTabsToConfig();
                }
            }
            case "Close Search" -> {
                if (tab instanceof SearchTab st) {
                    for (ChatPanel p : ChatManager.getAllPanels()) {
                        if (!p.isEnabled()) continue;
                        if (p.getActiveTab() != tab) continue;
                        ChatTab prev = st.previousTab(p.id());
                        if (prev != null && p.getVisibleTabs().contains(prev)) {
                            p.setActiveTabId(p.getVisibleTabs().indexOf(prev));
                            p.scrollActiveTabIntoView();
                            prev.clearUnread();
                        }
                    }
                }
                ChatManager.getTabs().remove(tab);
            }
        }
        return true;
    }

    private static int menuWidth() {
        int max = 0;
        for (String s : items) max = Math.max(max, LzDraw.textWidth(s));
        return max + PADDING * 2;
    }
}
