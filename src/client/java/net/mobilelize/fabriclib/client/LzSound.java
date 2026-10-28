package net.mobilelize.fabriclib.client;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.mobilelize.fabriclib.registry.LzId;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side sound playback utilities.
 * 1.21 (Yarn): PositionedSoundInstance / SoundManager / GLFW
 * 26.1 (Mojang): VERIFY class names
 */
public final class LzSound {

    private LzSound() {}

    // 1.21: GLFW.GLFW_FOCUSED via glfwGetWindowAttrib   26.1: VERIFY
    public static boolean isWindowFocused() {
        return GLFW.glfwGetWindowAttrib(LzClient.windowHandle(), GLFW.GLFW_FOCUSED) != 0;
    }

    // 1.21: SoundManager.play(PositionedSoundInstance.master)   26.1: VERIFY method names
    public static void playMaster(String soundId, float volume, float pitch) {
        LzId.tryParse(soundId).ifPresent(id -> {
            SoundEvent event = Registries.SOUND_EVENT.get(id.mc());
            if (event == null) return;
            LzClient.execute(() -> LzClient.mc().getSoundManager().play(
                PositionedSoundInstance.master(event, volume, pitch)
            ));
        });
    }
}
