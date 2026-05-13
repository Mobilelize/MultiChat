package net.mobilelize.multichat.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

@FunctionalInterface
public interface MessageSentCallback {

    /** Return {@code true} to cancel sending the message. */
    boolean onMessageSent(String message);

    Event<MessageSentCallback> EVENT = EventFactory.createArrayBacked(
        MessageSentCallback.class,
        handlers -> message -> {
            for (MessageSentCallback h : handlers) if (h.onMessageSent(message)) return true;
            return false;
        }
    );
}
