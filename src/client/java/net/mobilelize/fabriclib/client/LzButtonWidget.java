package net.mobilelize.fabriclib.client;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.mobilelize.fabriclib.text.LzText;

/**
 * Stable ButtonWidget subclass.
 * 1.21 (Yarn): net.minecraft.client.gui.widget.ButtonWidget
 * 26.1 (Mojang): net.minecraft.client.gui.components.Button  (VERIFY)
 */
public final class LzButtonWidget extends ButtonWidget.Text {

    // 1.21.11: extend ButtonWidget.Text (concrete subclass) — ButtonWidget itself is abstract with abstract drawIcon
    LzButtonWidget(int x, int y, int w, int h,
                   net.minecraft.text.Text message,
                   PressAction onPress,
                   NarrationSupplier narration) {
        super(x, y, w, h, message, onPress, narration);
    }

    // 1.21: ButtonWidget.DEFAULT_NARRATION_SUPPLIER (protected)   26.1: VERIFY
    static NarrationSupplier defaultNarration() {
        return DEFAULT_NARRATION_SUPPLIER;
    }

    // 1.21: ButtonWidget.setMessage(Text)   26.1: AbstractButton.setMessage (VERIFY)
    public void setMessage(LzText text) {
        super.setMessage(text.mc());
    }
}
