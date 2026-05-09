package net.mobilelize.multichat.client.config;

import net.mobilelize.fabriclib.config.LzConfig;

public class MultiChatConfig extends LzConfig {

    @Override
    public String directory() { return "config/multichat"; }

    public boolean rightPanelEnabled = false;
    public boolean rightPanelShowOnlyWhenFocused = false;
}
