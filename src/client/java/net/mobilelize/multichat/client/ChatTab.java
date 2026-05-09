package net.mobilelize.multichat.client;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.mobilelize.multichat.client.filter.FilterRuleSet;

import java.util.ArrayList;
import java.util.List;

public class ChatTab {

    public final String name;
    public final TabType type;
    public final List<ChatHudLine.Visible> messages = new ArrayList<>();
    public int scrolledLines = 0;
    public int unreadCount = 0;
    /** Only used when type == FILTERED. */
    public final FilterRuleSet filterRules = new FilterRuleSet();
    /** Only used when type == FILTERED. Shows messages matched by NO other filtered tab. */
    public boolean differenceMode = false;

    public ChatTab(String name, TabType type) {
        this.name = name;
        this.type = type;
    }
}
