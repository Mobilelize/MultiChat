package net.mobilelize.fabriclib.input;

import net.mobilelize.fabriclib.client.LzClient;

/**
 * Common GLFW key code constants and keyboard modifier queries.
 * 1.21 (Yarn): Screen.hasControlDown() / hasShiftDown() / hasAltDown()
 * 26.1 (Mojang): same methods, different package (VERIFY 26.1)
 */
public final class LzKeys {

    private LzKeys() {}

    // ── Navigation ────────────────────────────────────────────────────────

    public static final int BACKSPACE = 259;
    public static final int TAB       = 258;
    public static final int ENTER     = 257;
    public static final int KP_ENTER  = 335;
    public static final int ESCAPE    = 256;
    public static final int DELETE    = 261;
    public static final int RIGHT     = 262;
    public static final int LEFT      = 263;
    public static final int DOWN      = 264;
    public static final int UP        = 265;
    public static final int HOME      = 268;
    public static final int END       = 269;

    // ── Common letter keys ────────────────────────────────────────────────

    public static final int KEY_A = 65;
    public static final int KEY_C = 67;
    public static final int KEY_V = 86;
    public static final int KEY_X = 88;
    public static final int KEY_Z = 90;

    // ── Modifier queries ──────────────────────────────────────────────────

    // 1.21: Screen.hasControlDown()   26.1: Screen.hasControlDown() (static, stable)
    public static boolean isCtrlDown()  { return LzClient.mc().hasControlDown(); }
    // 1.21: Screen.hasShiftDown()   26.1: Screen.hasShiftDown() (static, stable)
    public static boolean isShiftDown() { return LzClient.mc().hasShiftDown(); }
    // 1.21: Screen.hasAltDown()   26.1: Screen.hasAltDown() (static, stable)
    public static boolean isAltDown()   { return LzClient.mc().hasAltDown(); }
}
