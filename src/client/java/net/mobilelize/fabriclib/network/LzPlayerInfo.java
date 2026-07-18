package net.mobilelize.fabriclib.network;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.mobilelize.fabriclib.game.LzGameMode;
import net.mobilelize.fabriclib.text.LzText;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Stable wrapper for a player's network/tab-list info.
 * 1.21 (Yarn): net.minecraft.client.network.PlayerListEntry
 * 26.1 (Mojang): net.minecraft.client.multiplayer.PlayerInfo
 */
public final class LzPlayerInfo {

    private final PlayerListEntry entry;

    public LzPlayerInfo(PlayerListEntry entry) { this.entry = entry; }

    // 1.21: entry.getProfile().getId()
    public UUID uuid()           { return entry.getProfile().getId(); }
    // 1.21: entry.getProfile().getName()
    public String name()         { return entry.getProfile().getName(); }
    // unchanged: entry.getProfile()
    public GameProfile profile() { return entry.getProfile(); }
    // 1.21: entry.getLatency()   26.1: stable
    public int ping()            { return entry.getLatency(); }
    // 1.21: entry.getGameMode()   26.1: stable
    public LzGameMode gameMode() { return LzGameMode.fromMC(entry.getGameMode()); }

    // 1.21: entry.getDisplayName()
    @Nullable public LzText displayName() {
        var t = entry.getDisplayName();
        return t == null ? null : LzText.wrapMc(t);
    }

    // unchanged: entry.hasPublicKey()
    public boolean hasPublicKey() { return entry.hasPublicKey(); }

    /** Raw MC PlayerListEntry — escape hatch (skin textures, scoreboard team, session, etc.). */
    public PlayerListEntry mc() { return entry; }
}
