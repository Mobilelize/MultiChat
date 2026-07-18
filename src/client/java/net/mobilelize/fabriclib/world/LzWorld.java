package net.mobilelize.fabriclib.world;

import net.minecraft.client.world.ClientWorld;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.player.LzPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utilities for the client-side world.
 * 1.21 (Yarn): net.minecraft.client.world.ClientWorld
 * 26.1 (Mojang): net.minecraft.client.multiplayer.ClientLevel
 */
public final class LzWorld {

    private LzWorld() {}

    // 1.21: client.world   26.1: client.level
    @Nullable
    private static ClientWorld world() { return LzClient.mc().world; }

    public static boolean isLoaded() { return world() != null; }

    /** All players in the client world. Empty if no world is loaded. */
    public static List<LzPlayer> players() {
        var w = world();
        if (w == null) return Collections.emptyList();
        // 1.21: world.getPlayers()   26.1: level.players()
        return w.getPlayers().stream()
            .map(LzPlayer::new)
            .collect(Collectors.toList());
    }
}
