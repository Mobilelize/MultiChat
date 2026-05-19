package net.mobilelize.multichat.api;

import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatSelection;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.ArrayList;
import java.util.List;

public class TabHandle {

    private final String fullId;
    private final ChatTab tab;

    TabHandle(String fullId, ChatTab tab) {
        this.fullId = fullId;
        this.tab    = tab;
    }

    public void sendMessage(LzText message, ChatTab... alsoAddTo) {
        if (LzChat.chatHud() == null) return;
        float scale     = (float) LzChat.chatScale();
        int   chatWidth = LzMath.ceil((float) LzChat.chatWidth() / scale);
        var lines = LzChat.breakLines(message, chatWidth);
        int tick = LzChat.ticks();

        List<LzChatLine> created = new ArrayList<>(lines.size());
        for (int j = 0; j < lines.size(); j++) {
            LzChatLine cl = LzChatLine.create(tick, lines.get(j), null, j == lines.size() - 1);
            tab.messages.addFirst(cl);
            created.add(cl);
        }
        adjustAfterAdd(tab, created.size());

        for (ChatTab target : alsoAddTo) {
            if (target == null) continue;
            for (int i = created.size() - 1; i >= 0; i--)
                target.messages.addFirst(created.get(i));
            adjustAfterAdd(target, created.size());
        }
    }

    public void sendMessage(String message, ChatTab... alsoAddTo) {
        sendMessage(LzText.literal(message), alsoAddTo);
    }

    public void setAsContext()   { ChatRenderer.contextTab = tab; }
    public void clearContext()   { if (ChatRenderer.contextTab == tab) ChatRenderer.contextTab = null; }

    public void setContextPrefix(String prefix)      { tab.contextPrefix = prefix != null ? prefix : ""; }
    public void setContextCommandMode(boolean asCmd) { tab.contextCommandMode = asCmd; }
    public String getContextPrefix()                 { return tab.contextPrefix; }

    public String getId()    { return fullId; }
    public ChatTab getTab()  { return tab; }

    private static void adjustAfterAdd(ChatTab tab, int added) {
        for (ChatPanel panel : List.of(ChatRenderer.LEFT, ChatRenderer.RIGHT)) {
            int scrolled = panel.getScrolledLines(tab);
            if (scrolled > 0) panel.setScrolledLines(tab, scrolled + added);
        }
        ChatSelection.shiftLines(tab, added);

        if (tab.messageCap > 0) {
            int before = tab.messages.size();
            while (tab.messages.size() > tab.messageCap) tab.messages.removeLast();
            int removed = before - tab.messages.size();
            if (removed > 0) {
                for (ChatPanel panel : List.of(ChatRenderer.LEFT, ChatRenderer.RIGHT)) {
                    int scrolled = panel.getScrolledLines(tab);
                    if (scrolled > 0)
                        panel.setScrolledLines(tab, Math.max(0, scrolled - removed));
                }
                ChatSelection.clampOrClear(tab);
            }
        }

        if (!isActiveInAnyPanel(tab)) tab.unreadCount++;
        if (!tab.messages.isEmpty())
            MessageAddedToTabCallback.EVENT.invoker().onMessageAdded(tab, tab.messages.getFirst());
    }

    private static boolean isActiveInAnyPanel(ChatTab tab) {
        for (ChatPanel panel : List.of(ChatRenderer.LEFT, ChatRenderer.RIGHT)) {
            List<ChatTab> visible = panel.getVisibleTabs();
            int idx = visible.indexOf(tab);
            if (idx >= 0 && panel.activeTab == idx) return true;
        }
        return false;
    }
}
