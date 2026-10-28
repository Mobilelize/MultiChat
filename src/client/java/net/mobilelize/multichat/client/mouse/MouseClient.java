package net.mobilelize.multichat.client.mouse;

import net.mobilelize.fabriclib.client.LzClient;
import org.lwjgl.glfw.GLFW;

import java.util.logging.Logger;

public class MouseClient {

    private static long arrowCursor;
    private static long handCursor;
    private static long ibeamCursor;
    private static long crosshairCursor;

    private static long currentCursor;

    public static void init() {
        arrowCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR);
        handCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
        ibeamCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR);
        crosshairCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR);

        currentCursor = arrowCursor;
    }

    public static void setArrowCursor() {
        setCursor(arrowCursor);
    }

    public static void setHandCursor() {
        setCursor(handCursor);
    }

    public static void setIBeamCursor() {
        setCursor(ibeamCursor);
    }

    public static void setCrosshairCursor() {
        setCursor(crosshairCursor);
    }

    public static void resetCursor() {
        setArrowCursor();
    }

    private static void setCursor(long cursor) {
        if (currentCursor == cursor) {
            return;
        }

        currentCursor = cursor;

        GLFW.glfwSetCursor(
                LzClient.mc().getWindow().getHandle(),
                cursor
        );
    }

    public static void cleanup() {
        GLFW.glfwDestroyCursor(arrowCursor);
        GLFW.glfwDestroyCursor(handCursor);
        GLFW.glfwDestroyCursor(ibeamCursor);
        GLFW.glfwDestroyCursor(crosshairCursor);
    }
}