package net.mobilelize.multichat.client.config;

import net.mobilelize.fabriclib.config.LzConfig;

public class MultiChatConfig extends LzConfig {

    @Override
    public String directory() { return "config/multichat"; }

    public boolean rightPanelEnabled = false;
    public boolean rightPanelShowOnlyWhenFocused = false;
    public boolean showUnreadBadges = true;

    /** Saved tab definitions for LEFT panel (ALL and VANILLA tabs are implicit, not stored). */
    public TabConfig[] leftTabs = new TabConfig[0];
    /** Saved tab definitions for RIGHT panel. */
    public TabConfig[] rightTabs = new TabConfig[0];

    public static class TabConfig {
        public String name = "";
        public boolean differenceMode = false;
        public RuleConfig[] whitelist = new RuleConfig[0];
        public RuleConfig[] blacklist = new RuleConfig[0];
    }

    public static class RuleConfig {
        /** "all", "keyword", "regex", "sender", "message_type" */
        public String type = "keyword";
        public String value = "";
        /** Only set when type == "message_type". */
        public String messageType = "";
    }
}
