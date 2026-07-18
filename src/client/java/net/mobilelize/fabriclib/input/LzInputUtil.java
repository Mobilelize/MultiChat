package net.mobilelize.fabriclib.input;

import net.minecraft.client.util.InputUtil;

/**
 * Utilities for input/key-code lookups.
 * 1.21 (Yarn): net.minecraft.client.util.InputUtil
 * 26.1 (Mojang): VERIFY class / method rename
 */
public final class LzInputUtil {

    private LzInputUtil() {}

    // 1.21.8: InputUtil.fromKeyCode(int, int)   1.21.9+: fromKeyCode(KeyInput) — scanCode dropped
    public static InputUtil.Key fromKeyCode(int keyCode, int scanCode) {
        return InputUtil.Type.KEYSYM.createFromCode(keyCode);
    }
}
