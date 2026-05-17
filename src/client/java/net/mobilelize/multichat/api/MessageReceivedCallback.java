package net.mobilelize.multichat.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.mobilelize.fabriclib.text.LzText;

@FunctionalInterface
public interface MessageReceivedCallback {

    /** Return {@code true} to cancel routing for this message. */
    boolean onMessageReceived(LzText message);

    Event<MessageReceivedCallback> EVENT = EventFactory.createArrayBacked(
        MessageReceivedCallback.class,
        handlers -> message -> {
            for (MessageReceivedCallback h : handlers) if (h.onMessageReceived(message)) return true;
            return false;
        }
    );
}
