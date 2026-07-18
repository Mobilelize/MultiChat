package net.mobilelize.fabriclib.input;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Utilities for input/key-code lookups.
 * 1.21 (Yarn): net.minecraft.client.util.InputUtil
 * 26.1 (Mojang): com.mojang.blaze3d.platform.InputConstants
 */
public final class LzInputUtil {

    private LzInputUtil() {}

    // 1.21.8: InputUtil.fromKeyCode(int, int)   1.21.9+: fromKeyCode(KeyInput) — scanCode dropped
    // 26.1: InputConstants.Type.KEYSYM.getOrCreate(keyCode)
    public static InputConstants.Key fromKeyCode(int keyCode, int scanCode) {
        return InputConstants.Type.KEYSYM.getOrCreate(keyCode);
    }
}
