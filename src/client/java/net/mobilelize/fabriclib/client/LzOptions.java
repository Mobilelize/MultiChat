package net.mobilelize.fabriclib.client;

import net.minecraft.client.option.GameOptions;
import org.jetbrains.annotations.Nullable;

public final class LzOptions {

    private LzOptions() {}

    // 1.21: client.options   26.1: client.options  (stable)
    @Nullable
    private static GameOptions opts() { return LzClient.mc().options; }

    public static boolean isReady() { return opts() != null; }

    /**
     * Current gamma (brightness) value.
     * 1.21: options.getGamma().getValue()   26.1: options.gamma().get()
     */
    public static double gamma() {
        var opts = opts();
        return opts == null ? 0.0 : opts.getGamma().getValue();
    }

    // 1.21: options.getChatOpacity().getValue()   26.1: VERIFY
    public static double chatOpacity() {
        var opts = opts();
        return opts == null ? 1.0 : (Double) opts.getChatOpacity().getValue();
    }

    // 1.21: options.getChatLineSpacing().getValue()   26.1: VERIFY
    public static double chatLineSpacing() {
        var opts = opts();
        return opts == null ? 0.0 : (Double) opts.getChatLineSpacing().getValue();
    }

    // 1.21: options.getTextBackgroundOpacity().getValue()   26.1: VERIFY
    public static double textBackgroundOpacity() {
        var opts = opts();
        return opts == null ? 0.5 : (Double) opts.getTextBackgroundOpacity().getValue();
    }

    // 1.21: options.getChatVisibility().getValue()   26.1: VERIFY enum rename
    @Nullable
    public static LzChatVisibility chatVisibility() {
        var opts = opts();
        if (opts == null) return null;
        var v = opts.getChatVisibility().getValue();
        return v == null ? null : LzChatVisibility.fromMC(v);
    }
}
