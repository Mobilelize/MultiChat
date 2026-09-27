package net.mobilelize.fabriclib.client;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.mobilelize.fabriclib.registry.LzId;

/**
 * Client-side sound playback utilities.
 * 1.21 (Yarn): PositionedSoundInstance / SoundManager / GLFW
 * 26.1 (Mojang): VERIFY class names
 */
public final class LzSound {

    private LzSound() {}

    // 1.21: GLFW.GLFW_FOCUSED via glfwGetWindowAttrib   26.3: GLFW removed (SDL3), use Window.isFocused()
    public static boolean isWindowFocused() {
        return LzClient.mc().getWindow().isFocused();
    }

    // 1.21: SoundManager.play(PositionedSoundInstance.master)   26.1: VERIFY method names
    public static void playMaster(String soundId, float volume, float pitch) {
        LzId.tryParse(soundId).flatMap(id -> BuiltInRegistries.SOUND_EVENT.get(id.mc()).map(Holder::value)).ifPresent(event ->
                LzClient.execute(() -> LzClient.mc().getSoundManager().play(
                        new SimpleSoundInstance(event.location(), SoundSource.MASTER, volume, pitch, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true)
                )));
    }
}
