package net.mobilelize.multichat.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.multichat.client.ChatTab;

@FunctionalInterface
public interface MessageAddedToTabCallback {

    void onMessageAdded(ChatTab tab, ChatTab.MessageEntry message);

    Event<MessageAddedToTabCallback> EVENT = EventFactory.createArrayBacked(
        MessageAddedToTabCallback.class,
        handlers -> (tab, message) -> {
            for (MessageAddedToTabCallback h : handlers) h.onMessageAdded(tab, message);
        }
    );
}
