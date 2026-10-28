package net.mobilelize.multichat.client;

import com.mojang.blaze3d.platform.InputConstants;

// Aliases for vanilla key codes — never hardcode ints here, the numbering changed
// from GLFW key codes to SDL scancodes in 26.3.
public final class Keys {
    private Keys() {}

    public static final int ESCAPE       = InputConstants.KEY_ESCAPE;
    public static final int ENTER        = InputConstants.KEY_RETURN;
    public static final int RIGHT        = InputConstants.KEY_RIGHT;
    public static final int LEFT         = InputConstants.KEY_LEFT;
    public static final int DOWN         = InputConstants.KEY_DOWN;
    public static final int UP           = InputConstants.KEY_UP;
    public static final int C            = InputConstants.KEY_C;
    public static final int NUMPAD_ENTER = InputConstants.KEY_NUMPADENTER;
}
