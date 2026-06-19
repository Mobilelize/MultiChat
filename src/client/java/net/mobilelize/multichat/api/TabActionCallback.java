package net.mobilelize.multichat.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

@FunctionalInterface
public interface TabActionCallback {

    void onAction(String actionId);

    Event<TabActionCallback> EVENT = EventFactory.createArrayBacked(
        TabActionCallback.class,
        handlers -> actionId -> {
            for (TabActionCallback h : handlers) h.onAction(actionId);
        }
    );
}
