package net.mobilelize.fabriclib.client;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

public final class LzClient {

    private LzClient() {}

    /** Raw MC client — escape hatch for advanced use. */
    public static Minecraft mc() { return Minecraft.getInstance(); }

    // 1.21: client.worldRenderer.reload()   26.1: client.levelRenderer.allChanged()
    public static void reloadRenderer() { mc().levelRenderer.allChanged(); }

    // unchanged: client.execute(r)
    public static void execute(Runnable runnable) { mc().execute(runnable); }

    // 1.21: client.runDirectory   26.1: client.gameDirectory
    public static java.io.File runDir() { return mc().gameDirectory; }

    public static void openScreen(@Nullable LzScreen screen) { mc().setScreen(screen); }

    public static boolean hasPlayer() { return mc().player != null; }
    // 1.21: mc().world   26.1: mc().level
    public static boolean hasWorld()  { return mc().level != null; }

    /**
     * Current system clipboard text.
     * 1.21: client.keyboard.getClipboard()   26.1: client.keyboardHandler.getClipboard()
     */
    public static String clipboard() {
        var kb = mc().keyboardHandler;
        return kb != null ? kb.getClipboard() : "";
    }

    public static void setClipboard(String text) {
        var kb = mc().keyboardHandler;
        if (kb != null) kb.setClipboard(text);
    }

    // unchanged: client.isRunning()
    public static boolean isRunning() { return mc().isRunning(); }

    // 1.21: client.currentScreen   26.1: client.screen
    @Nullable
    public static net.minecraft.client.gui.screens.Screen currentScreen() {
        return mc().screen;
    }

    // 1.21: client.getWindow().getScaledWidth()   26.1: VERIFY
    public static int scaledWindowWidth() {
        return mc().getWindow().getGuiScaledWidth();
    }

    // 1.21: client.getWindow().getScaledHeight()   26.1: VERIFY
    public static int scaledWindowHeight() {
        return mc().getWindow().getGuiScaledHeight();
    }

    // 1.21: client.getWindow().getHandle()   26.1: VERIFY (GLFW native handle)
    public static long windowHandle() { return mc().getWindow().handle(); }

    // 1.21: client.send(r)   26.1: execute() (ReentrantBlockableEventLoop — no send/submit(Runnable))
    public static void send(Runnable runnable) { mc().execute(runnable); }
}
