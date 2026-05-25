package net.mobilelize.multichat.client;

import net.minecraft.text.Text;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.multichat.client.filter.FilterRuleSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ChatTab {

    public String name;
    public final TabType type;
    public final Set<String> visibleOnPanels = new LinkedHashSet<>();
    public final List<LzChatLine> messages = new ArrayList<>();
    public int unreadCount = 0;
    public final FilterRuleSet filterRules = new FilterRuleSet();
    public String  apiId             = null;
    public boolean differenceMode    = false;
    public boolean showBadge         = true;
    public boolean showTimestamps    = false;
    public int     tabColor          = 0;
    public int     msgBgColor        = 0;   // 0 = use panel bgColor
    public boolean collapseRepeating        = false;
    public boolean collapseMatchFormatting  = false;
    public int     messageCap               = 0;
    public String  contextPrefix     = "";
    public boolean contextCommandMode = true;

    // Collapse tracking — reset whenever messages are cleared
    public String              collapseLastText    = null;
    public Text                collapseLastContent = null;
    public int                 collapseRepeat      = 0;
    public int                 collapseLineCount   = 0;
    public int                 collapseLastTick    = 0;
    public List<LzOrderedText> collapseLastLines   = null;

    public ChatTab(String name, TabType type, Collection<String> visibleOnPanels) {
        this.name = name;
        this.type = type;
        this.visibleOnPanels.addAll(visibleOnPanels);
    }

    public boolean isSystemTab() {
        return type == TabType.ALL || type == TabType.VANILLA;
    }

    public void resetCollapse() {
        collapseLastText    = null;
        collapseLastContent = null;
        collapseRepeat      = 0;
        collapseLineCount   = 0;
        collapseLastLines   = null;
    }
}
