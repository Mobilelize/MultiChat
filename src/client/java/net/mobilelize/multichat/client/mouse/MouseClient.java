package net.mobilelize.multichat.client.mouse;

import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.mobilelize.fabriclib.client.LzClient;

// 26.3: GLFW removed (SDL3) — cursors go through vanilla Window.selectCursor(CursorType)
public class MouseClient {

    private static CursorType currentCursor = CursorTypes.ARROW;

    public static void init() {
        currentCursor = CursorTypes.ARROW;
    }

    public static void setArrowCursor() {
        setCursor(CursorTypes.ARROW);
    }

    public static void setHandCursor() {
        setCursor(CursorTypes.POINTING_HAND);
    }

    public static void setIBeamCursor() {
        setCursor(CursorTypes.IBEAM);
    }

    public static void setCrosshairCursor() {
        setCursor(CursorTypes.CROSSHAIR);
    }

    public static void resetCursor() {
        setArrowCursor();
    }

    private static void setCursor(CursorType cursor) {
        if (currentCursor == cursor) {
            return;
        }

        currentCursor = cursor;

        LzClient.mc().getWindow().selectCursor(cursor);
    }

    // Vanilla owns the cursor handles now; nothing to free
    public static void cleanup() {}
}
