package net.mobilelize.multichat.client;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import net.mobilelize.multichat.client.filter.FilterRuleSet;

import java.util.ArrayList;
import java.util.List;

public class ChatTab {

    public final String name;
    public final TabType type;
    public TabVisibility visibility;
    public final List<ChatHudLine.Visible> messages = new ArrayList<>();
    public int unreadCount = 0;
    public final FilterRuleSet filterRules = new FilterRuleSet();
    public boolean differenceMode    = false;
    public boolean showBadge         = true;
    public boolean collapseRepeating = false;
    public int     messageCap        = 0;
    public String  contextPrefix     = "";
    public boolean contextCommandMode = true;

    // Collapse tracking — reset whenever messages are cleared
    public String            collapseLastText   = null;
    public int               collapseRepeat     = 0;
    public int               collapseLineCount  = 0;
    public int               collapseLastTick   = 0;
    public List<OrderedText> collapseLastLines  = null;

    public ChatTab(String name, TabType type, TabVisibility visibility) {
        this.name = name;
        this.type = type;
        this.visibility = visibility;
    }

    public void resetCollapse() {
        collapseLastText  = null;
        collapseRepeat    = 0;
        collapseLineCount = 0;
        collapseLastLines = null;
    }
}
