package net.mobilelize.fabriclib.player;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.mobilelize.fabriclib.math.LzVec3;

import java.util.UUID;

/**
 * Stable wrapper for any online player (local or remote).
 * 1.21 (Yarn): net.minecraft.client.network.AbstractClientPlayerEntity
 * 26.1 (Mojang): net.minecraft.client.player.AbstractClientPlayer
 */
public class LzPlayer {

    protected final AbstractClientPlayerEntity player;

    public LzPlayer(AbstractClientPlayerEntity player) { this.player = player; }

    // 1.21: player.getUuid()   26.1: player.getUUID()
    public UUID uuid()             { return player.getUuid(); }
    // unchanged: player.getGameProfile().getName()
    public String name()           { return player.getGameProfile().getName(); }
    // unchanged: player.getGameProfile().getId()
    public UUID profileId()        { return player.getGameProfile().getId(); }
    // 1.21: player.isMainPlayer()   26.1: player.isLocalPlayer()
    public boolean isLocalPlayer() { return player.isMainPlayer(); }
    // unchanged: player.getId()
    public int entityId()          { return player.getId(); }
    // 1.21: player.getPos()   26.1: player.position()
    public LzVec3 pos()            { return LzVec3.of(player.getPos()); }

    /** Raw MC player — escape hatch for advanced use. */
    public AbstractClientPlayerEntity mc() { return player; }

    /** True if {@code name} matches the standard MC username format (3–16 alphanumeric/underscore). */
    public static boolean isValidUsername(String name) {
        return name != null && name.matches("^[a-zA-Z0-9_]{3,16}$");
    }
}
