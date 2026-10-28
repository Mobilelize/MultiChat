package net.mobilelize.fabriclib.game;

import net.minecraft.world.level.GameType;

/**
 * Stable game mode enum. Mods never import MC's GameMode/GameType directly.
 * 1.21 (Yarn): net.minecraft.world.GameMode
 * 26.1 (Mojang): net.minecraft.world.level.GameType
 */
public enum LzGameMode {

    SURVIVAL(GameType.SURVIVAL),
    CREATIVE(GameType.CREATIVE),
    ADVENTURE(GameType.ADVENTURE),
    SPECTATOR(GameType.SPECTATOR);

    private final GameType mcMode;

    LzGameMode(GameType mcMode) {
        this.mcMode = mcMode;
    }

    /** Returns the underlying MC GameType value for this version. */
    public GameType mc() {
        return mcMode;
    }

    public static LzGameMode fromMC(GameType mode) {
        return switch (mode) {
            case SURVIVAL  -> SURVIVAL;
            case CREATIVE  -> CREATIVE;
            case ADVENTURE -> ADVENTURE;
            case SPECTATOR -> SPECTATOR;
        };
    }
}
