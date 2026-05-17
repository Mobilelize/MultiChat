package net.mobilelize.multichat.api;

import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.List;

public class TabHandle {

    private final String fullId;
    private final ChatTab tab;

    TabHandle(String fullId, ChatTab tab) {
        this.fullId = fullId;
        this.tab    = tab;
    }

    public void sendMessage(LzText message) {
        if (LzChat.chatHud() == null) return;
        float scale     = (float) LzChat.chatScale();
        int   chatWidth = LzMath.ceil((float) LzChat.chatWidth() / scale);
        var lines = LzChat.breakLines(message, chatWidth);
        int tick = LzChat.ticks();
        for (int j = 0; j < lines.size(); j++)
            tab.messages.add(0, LzChatLine.create(tick, lines.get(j), null, j == lines.size() - 1));
        if (tab.messageCap > 0)
            while (tab.messages.size() > tab.messageCap) tab.messages.remove(tab.messages.size() - 1);
        if (!isActiveInAnyPanel(tab)) tab.unreadCount++;
        MessageAddedToTabCallback.EVENT.invoker().onMessageAdded(tab, tab.messages.get(0));
    }

    public void sendMessage(String message) {
        sendMessage(LzText.literal(message));
    }

    public void setAsContext()   { ChatRenderer.contextTab = tab; }
    public void clearContext()   { if (ChatRenderer.contextTab == tab) ChatRenderer.contextTab = null; }

    public void setContextPrefix(String prefix)      { tab.contextPrefix = prefix != null ? prefix : ""; }
    public void setContextCommandMode(boolean asCmd) { tab.contextCommandMode = asCmd; }
    public String getContextPrefix()                 { return tab.contextPrefix; }

    public String getId()    { return fullId; }
    public ChatTab getTab()  { return tab; }

    private static boolean isActiveInAnyPanel(ChatTab tab) {
        for (ChatPanel panel : List.of(ChatRenderer.LEFT, ChatRenderer.RIGHT)) {
            List<ChatTab> visible = panel.getVisibleTabs();
            int idx = visible.indexOf(tab);
            if (idx >= 0 && panel.activeTab == idx) return true;
        }
        return false;
    }
}
