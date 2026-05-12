package net.mobilelize.multichat.client;

import net.minecraft.client.gui.hud.ChatHudLine;
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
    public boolean differenceMode = false;
    public boolean showBadge = true;
    public String  contextPrefix = "";
    public boolean contextCommandMode = true;

    public ChatTab(String name, TabType type, TabVisibility visibility) {
        this.name = name;
        this.type = type;
        this.visibility = visibility;
    }
}
