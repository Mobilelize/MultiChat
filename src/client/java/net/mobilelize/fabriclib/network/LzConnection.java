package net.mobilelize.fabriclib.network;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.world.LzWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Server connection and tab-list player info.
 * 1.21 (Yarn): ClientPlayNetworkHandler — client.getNetworkHandler()
 * 26.1 (Mojang): ClientPacketListener — client.getConnection()
 */
public final class LzConnection {

    private LzConnection() {}

    // 1.21: client.getNetworkHandler()   26.1: client.getConnection()
    @Nullable
    private static ClientPlayNetworkHandler handler() {
        return LzClient.mc().getNetworkHandler();
    }

    /** All players visible in the tab list. Empty if not connected. */
    public static Collection<LzPlayerInfo> onlinePlayers() {
        var h = handler();
        if (h == null) return Collections.emptyList();
        // 1.21: handler.getPlayerList()   26.1: connection.getOnlinePlayers()
        return h.getPlayerList().stream()
            .map(LzPlayerInfo::new)
            .collect(Collectors.toList());
    }

    /** Tab-list info for a player by UUID. */
    public static Optional<LzPlayerInfo> playerInfo(UUID uuid) {
        var h = handler();
        if (h == null) return Optional.empty();
        // 1.21: handler.getPlayerListEntry(uuid)   26.1: connection.getPlayerInfo(uuid)
        var entry = h.getPlayerListEntry(uuid);
        return Optional.ofNullable(entry).map(LzPlayerInfo::new);
    }

    /** Tab-list info for a player by name (case-insensitive). */
    public static Optional<LzPlayerInfo> playerInfoByName(String name) {
        var h = handler();
        if (h == null) return Optional.empty();
        return h.getPlayerList().stream()
            .filter(e -> e.getProfile().getName().equalsIgnoreCase(name))
            .map(LzPlayerInfo::new)
            .findFirst();
    }

    /** Ping (ms) by UUID. Returns {@code -1} if not found. */
    public static int pingByUuid(UUID uuid) {
        return playerInfo(uuid).map(LzPlayerInfo::ping).orElse(-1);
    }

    /** Ping (ms) by name (case-insensitive). Returns {@code -1} if not found. */
    public static int pingByName(String name) {
        return playerInfoByName(name).map(LzPlayerInfo::ping).orElse(-1);
    }

    /** Ping (ms) by entity ID. Returns {@code -1} if not found. */
    public static int pingByEntityId(int entityId) {
        return LzWorld.players().stream()
            .filter(p -> p.entityId() == entityId)
            .map(p -> pingByUuid(p.uuid()))
            .findFirst().orElse(-1);
    }

    /**
     * Sends a chat message as the local player. No-op if not connected.
     * 1.21: handler.sendChatMessage(msg)   26.1: connection.chat(msg)  (VERIFY)
     */
    public static void sendChatMessage(String message) {
        var h = handler();
        if (h != null) h.sendChatMessage(message);
    }

    /**
     * Sends a command as the local player (without leading slash). No-op if not connected.
     * 1.21: handler.sendChatCommand(cmd)   26.1: connection.sendCommand(cmd)  (VERIFY)
     */
    public static void sendChatCommand(String command) {
        var h = handler();
        if (h != null) h.sendChatCommand(command);
    }
}
