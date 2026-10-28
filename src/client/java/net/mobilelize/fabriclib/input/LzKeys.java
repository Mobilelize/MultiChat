package net.mobilelize.fabriclib.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.mobilelize.fabriclib.client.LzClient;

/**
 * Common key code constants and keyboard modifier queries.
 * 26.3: key codes are SDL scancodes — always alias InputConstants, never hardcode ints.
 * 1.21 (Yarn): Screen.hasControlDown() / hasShiftDown() / hasAltDown()
 * 26.1 (Mojang): same methods, different package (VERIFY 26.1)
 */
public final class LzKeys {

    private LzKeys() {}

    // ── Navigation ────────────────────────────────────────────────────────

    public static final int BACKSPACE = InputConstants.KEY_BACKSPACE;
    public static final int TAB       = InputConstants.KEY_TAB;
    public static final int ENTER     = InputConstants.KEY_RETURN;
    public static final int KP_ENTER  = InputConstants.KEY_NUMPADENTER;
    public static final int ESCAPE    = InputConstants.KEY_ESCAPE;
    public static final int DELETE    = InputConstants.KEY_DELETE;
    public static final int RIGHT     = InputConstants.KEY_RIGHT;
    public static final int LEFT      = InputConstants.KEY_LEFT;
    public static final int DOWN      = InputConstants.KEY_DOWN;
    public static final int UP        = InputConstants.KEY_UP;
    public static final int HOME      = InputConstants.KEY_HOME;
    public static final int END       = InputConstants.KEY_END;

    // ── Common letter keys ────────────────────────────────────────────────

    public static final int KEY_A = InputConstants.KEY_A;
    public static final int KEY_C = InputConstants.KEY_C;
    public static final int KEY_V = InputConstants.KEY_V;
    public static final int KEY_X = InputConstants.KEY_X;
    public static final int KEY_Z = InputConstants.KEY_Z;

    // ── Modifier queries ──────────────────────────────────────────────────

    // 1.21: Screen.hasControlDown()   26.1: Screen.hasControlDown() (static, stable)
    public static boolean isCtrlDown()  { return LzClient.mc().hasControlDown(); }
    // 1.21: Screen.hasShiftDown()   26.1: Screen.hasShiftDown() (static, stable)
    public static boolean isShiftDown() { return LzClient.mc().hasShiftDown(); }
    // 1.21: Screen.hasAltDown()   26.1: Screen.hasAltDown() (static, stable)
    public static boolean isAltDown()   { return LzClient.mc().hasAltDown(); }
}
