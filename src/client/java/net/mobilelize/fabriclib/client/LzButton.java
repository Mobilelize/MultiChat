package net.mobilelize.fabriclib.client;

import net.mobilelize.fabriclib.text.LzText;

import java.util.function.Consumer;

/**
 * Factory for buttons — isolates the ButtonWidget builder chain.
 * 1.21 (Yarn): ButtonWidget.builder(Text, PressAction).dimensions(x,y,w,h).build()
 * 26.1 (Mojang): renamed to Button, builder chain may change — update here only.
 */
public final class LzButton {

    private LzButton() {}

    private static net.minecraft.client.gui.widget.ButtonWidget.NarrationSupplier narration() {
        return LzButtonWidget.defaultNarration();
    }

    public static LzButtonWidget of(String label, int x, int y, int w, int h,
                                    Consumer<LzButtonWidget> onClick) {
        LzButtonWidget[] ref = new LzButtonWidget[1];
        ref[0] = new LzButtonWidget(x, y, w, h,
            LzText.literal(label).mc(),
            btn -> onClick.accept(ref[0]),
            narration());
        return ref[0];
    }

    public static LzButtonWidget of(LzText label, int x, int y, int w, int h,
                                    Consumer<LzButtonWidget> onClick) {
        LzButtonWidget[] ref = new LzButtonWidget[1];
        ref[0] = new LzButtonWidget(x, y, w, h,
            label.mc(),
            btn -> onClick.accept(ref[0]),
            narration());
        return ref[0];
    }
}
