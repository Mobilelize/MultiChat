package net.mobilelize.fabriclib.game;

import net.minecraft.world.GameMode;

/**
 * Stable game mode enum. Mods never import MC's GameMode/GameType directly.
 * 1.21 (Yarn): net.minecraft.world.GameMode
 * 26.1 (Mojang): net.minecraft.world.level.GameType
 */
public enum LzGameMode {

    SURVIVAL(GameMode.SURVIVAL),
    CREATIVE(GameMode.CREATIVE),
    ADVENTURE(GameMode.ADVENTURE),
    SPECTATOR(GameMode.SPECTATOR);

    private final GameMode mcMode;

    LzGameMode(GameMode mcMode) {
        this.mcMode = mcMode;
    }

    /** Returns the underlying MC GameMode value for this version. */
    public GameMode mc() {
        return mcMode;
    }

    public static LzGameMode fromMC(GameMode mode) {
        return switch (mode) {
            case SURVIVAL  -> SURVIVAL;
            case CREATIVE  -> CREATIVE;
            case ADVENTURE -> ADVENTURE;
            case SPECTATOR -> SPECTATOR;
        };
    }
}
