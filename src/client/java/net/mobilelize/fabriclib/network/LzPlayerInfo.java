package net.mobilelize.fabriclib.network;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
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

    private final PlayerInfo entry;

    public LzPlayerInfo(PlayerInfo entry) { this.entry = entry; }

    // 1.21: entry.getProfile().getId()
    public UUID uuid()           { return entry.getProfile().id(); }
    // 1.21: entry.getProfile().getName()
    public String name()         { return entry.getProfile().name(); }
    // unchanged: entry.getProfile()
    public GameProfile profile() { return entry.getProfile(); }
    // 1.21: entry.getLatency()   26.1: entry.getLatency()  (stable)
    public int ping()            { return entry.getLatency(); }
    // 1.21: entry.getGameMode()   26.1: entry.getGameMode()  (stable)
    public LzGameMode gameMode() { return LzGameMode.fromMC(entry.getGameMode()); }

    // 1.21: entry.getDisplayName()
    @Nullable public LzText displayName() {
        var t = entry.getTabListDisplayName();
        return t == null ? null : LzText.wrapMc(t);
    }

    // 1.21: entry.hasPublicKey()   26.1: entry.hasVerifiableChat()
    public boolean hasPublicKey() { return entry.hasVerifiableChat(); }

    /** Raw MC PlayerInfo — escape hatch (skin textures, scoreboard team, session, etc.). */
    public PlayerInfo mc() { return entry; }
}
