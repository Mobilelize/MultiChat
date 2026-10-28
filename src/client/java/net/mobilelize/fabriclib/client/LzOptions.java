package net.mobilelize.fabriclib.client;

import net.minecraft.client.Options;
import org.jetbrains.annotations.Nullable;

public final class LzOptions {

    private LzOptions() {}

    // 1.21: client.options   26.1: client.options  (stable)
    @Nullable
    private static Options opts() { return LzClient.mc().options; }

    public static boolean isReady() { return opts() != null; }

    /**
     * Current gamma (brightness) value.
     * 1.21: options.getGamma().getValue()   26.1: options.gamma().get()
     */
    public static double gamma() {
        var opts = opts();
        return opts == null ? 0.0 : opts.gamma().get();
    }

    // 1.21: options.getChatOpacity().getValue()   26.1: options.chatOpacity().get()
    public static double chatOpacity() {
        var opts = opts();
        return opts == null ? 1.0 : opts.chatOpacity().get();
    }

    // 1.21: options.getChatLineSpacing().getValue()   26.1: options.chatLineSpacing().get()
    public static double chatLineSpacing() {
        var opts = opts();
        return opts == null ? 0.0 : opts.chatLineSpacing().get();
    }

    // 1.21: options.getTextBackgroundOpacity().getValue()   26.1: options.textBackgroundOpacity().get()
    public static double textBackgroundOpacity() {
        var opts = opts();
        return opts == null ? 0.5 : opts.textBackgroundOpacity().get();
    }

    // 1.21: options.getChatVisibility().getValue()   26.1: options.chatVisibility().get()
    @Nullable
    public static LzChatVisibility chatVisibility() {
        var opts = opts();
        if (opts == null) return null;
        var v = opts.chatVisibility().get();
        return v == null ? null : LzChatVisibility.fromMC(v);
    }

    // 1.21: options.getChatScale().getValue()   26.1: options.chatScale().get()
    public static double chatScale() {
        var opts = opts();
        return opts == null ? 1.0 : opts.chatScale().get();
    }

    // 1.21: options.getChatWidth().getValue()   26.1: options.chatWidth().get()
    public static double chatWidth() {
        var opts = opts();
        return opts == null ? 1.0 : opts.chatWidth().get();
    }

    // 1.21: options.getChatColors().getValue()   26.1: options.chatColors().get()
    public static boolean chatColors() {
        var opts = opts();
        return opts == null || opts.chatColors().get();
    }
}
