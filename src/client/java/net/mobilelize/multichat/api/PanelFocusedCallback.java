package net.mobilelize.multichat.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.mobilelize.multichat.client.ChatPanel;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface PanelFocusedCallback {

    /**
     * Fires when the focused panel changes.
     *
     * @param panel    The panel that is now focused.
     * @param previous The panel that was focused before, or {@code null} if none.
     */
    void onPanelFocused(ChatPanel panel, @Nullable ChatPanel previous);

    Event<PanelFocusedCallback> EVENT = EventFactory.createArrayBacked(
        PanelFocusedCallback.class,
        handlers -> (panel, previous) -> {
            for (PanelFocusedCallback h : handlers) h.onPanelFocused(panel, previous);
        }
    );
}
