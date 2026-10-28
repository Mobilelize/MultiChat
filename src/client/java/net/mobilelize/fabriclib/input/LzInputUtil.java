package net.mobilelize.fabriclib.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLMouse;

/**
 * Utilities for input/key-code lookups.
 * 1.21 (Yarn): net.minecraft.client.util.InputUtil
 * 26.1 (Mojang): com.mojang.blaze3d.platform.InputConstants
 */
public final class LzInputUtil {

    private LzInputUtil() {}

    // 1.21.8: InputUtil.fromKeyCode(int, int)   1.21.9+: fromKeyCode(KeyInput) — scanCode dropped
    // 26.1: InputConstants.Type.KEYSYM.getOrCreate(keyCode)   26.3: KEYSYM renamed to KEYBOARD
    public static InputConstants.Key fromKeyCode(int keyCode, int scanCode) {
        return InputConstants.Type.KEYBOARD.getOrCreate(keyCode);
    }

    // Live poll of the physical left button. MouseHandler.isLeftPressed() is NOT usable here:
    // vanilla only updates it while no screen is open, so it stays false inside ChatScreen.
    // 1.21: glfwGetMouseButton(handle, GLFW_MOUSE_BUTTON_LEFT)   26.3: SDL_GetMouseState
    public static boolean isLeftMouseDown() {
        return (SDLMouse.nSDL_GetMouseState(0L, 0L) & SDLMouse.SDL_BUTTON_LMASK) != 0;
    }
}
