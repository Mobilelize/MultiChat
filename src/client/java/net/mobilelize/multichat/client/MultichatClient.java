package net.mobilelize.multichat.client;

import net.fabricmc.api.ClientModInitializer;
import net.mobilelize.fabriclib.config.LzConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig;
import net.mobilelize.multichat.client.render.ChatRenderer;

public class MultichatClient implements ClientModInitializer {

    public static MultiChatConfig CONFIG;

    @Override
    public void onInitializeClient() {
        CONFIG = LzConfig.load(MultiChatConfig.class);
        ChatRenderer.PANELS.get(1).enabled = CONFIG.rightPanelEnabled;
        ChatRenderer.PANELS.get(1).showOnlyWhenFocused = CONFIG.rightPanelShowOnlyWhenFocused;
    }
}
