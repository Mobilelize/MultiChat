package net.mobilelize.multichat.api;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
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

    public void sendMessage(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) return;
        ChatHud chat = client.inGameHud.getChatHud();
        float scale     = (float) chat.getChatScale();
        int   chatWidth = MathHelper.ceil((float) chat.getWidth() / scale);
        List<OrderedText> lines = ChatMessages.breakRenderedChatMessageLines(message, chatWidth, client.textRenderer);
        int tick = client.inGameHud.getTicks();
        for (int j = 0; j < lines.size(); j++)
            tab.messages.add(0, new ChatHudLine.Visible(tick, lines.get(j), null, j == lines.size() - 1));
        if (tab.messageCap > 0)
            while (tab.messages.size() > tab.messageCap) tab.messages.remove(tab.messages.size() - 1);
        if (!isActiveInAnyPanel(tab)) tab.unreadCount++;
        MessageAddedToTabCallback.EVENT.invoker().onMessageAdded(tab, tab.messages.get(0));
    }

    public void sendMessage(String message) {
        sendMessage(Text.literal(message));
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
