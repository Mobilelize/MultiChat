package net.mobilelize.multichat.client;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public enum TabVisibility {
    LEFT, RIGHT, BOTH, NONE;

    /** Migration helper — converts legacy enum to the panel-ID set model. */
    public static Set<Integer> toPanelIds(TabVisibility v) {
        if (v == null) return new LinkedHashSet<>(List.of(0, 1));
        return switch (v) {
            case BOTH  -> new LinkedHashSet<>(List.of(0, 1));
            case LEFT  -> new LinkedHashSet<>(List.of(0));
            case RIGHT -> new LinkedHashSet<>(List.of(1));
            case NONE  -> new LinkedHashSet<>();
        };
    }
}
