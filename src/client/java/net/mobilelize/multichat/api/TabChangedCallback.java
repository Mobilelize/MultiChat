package net.mobilelize.multichat.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;

@FunctionalInterface
public interface TabChangedCallback {

    void onTabChanged(ChatPanel panel, ChatTab newTab);

    Event<TabChangedCallback> EVENT = EventFactory.createArrayBacked(
        TabChangedCallback.class,
        handlers -> (panel, tab) -> {
            for (TabChangedCallback h : handlers) h.onTabChanged(panel, tab);
        }
    );
}
