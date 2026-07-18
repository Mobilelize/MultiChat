package net.mobilelize.fabriclib.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client-side AFK detection via yaw/pitch change tracking.
 * "AFK" = player has not moved their mouse (look direction) for a threshold period.
 *
 * Call {@link #init()} once from your mod's client initializer.
 *
 * 1.21 (Yarn): entity.getYaw() / entity.getPitch()
 * 26.1 (Mojang): entity.getYRot() / entity.getXRot()  (VERIFY)
 */
public final class LzAfk {

    private LzAfk() {}

    private static boolean registered    = false;
    private static boolean playerWasNull = true;
    private static volatile long lastActivityMs = System.currentTimeMillis();
    private static float lastYaw   = 0f;
    private static float lastPitch = 0f;

    public static void init() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                playerWasNull = true;
                return;
            }
            if (playerWasNull) {
                playerWasNull  = false;
                lastYaw        = client.player.getYaw();
                lastPitch      = client.player.getPitch();
                lastActivityMs = System.currentTimeMillis();
                return;
            }
            float yaw   = client.player.getYaw();
            float pitch = client.player.getPitch();
            if (yaw != lastYaw || pitch != lastPitch) {
                lastYaw        = yaw;
                lastPitch      = pitch;
                lastActivityMs = System.currentTimeMillis();
            }
        });
    }

    /** Milliseconds since player last moved their mouse (look direction changed). */
    public static long timeSinceActivityMs() {
        return System.currentTimeMillis() - lastActivityMs;
    }

    /** True if player has not moved their look direction for at least {@code thresholdSec} seconds. */
    public static boolean isAfk(int thresholdSec) {
        return timeSinceActivityMs() >= thresholdSec * 1000L;
    }
}
