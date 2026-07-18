package net.mobilelize.fabriclib.client;

import net.minecraft.client.gui.components.Button;
import net.mobilelize.fabriclib.text.LzText;

/**
 * Stable Button subclass.
 * 1.21 (Yarn): net.minecraft.client.gui.widget.ButtonWidget  (extended ButtonWidget.Text)
 * 26.1 (Mojang): net.minecraft.client.gui.components.Button  — Button itself is abstract
 * (extractContents split out for the render-state refactor); the concrete impl lives in
 * Button.Plain, so extend that instead of Button directly.
 */
public final class LzButtonWidget extends Button.Plain {

    LzButtonWidget(int x, int y, int w, int h,
                   net.minecraft.network.chat.Component message,
                   OnPress onPress,
                   CreateNarration narration) {
        super(x, y, w, h, message, onPress, narration);
    }

    // 1.21: ButtonWidget.DEFAULT_NARRATION_SUPPLIER (protected)   26.1: Button.DEFAULT_NARRATION (VERIFY)
    static CreateNarration defaultNarration() {
        return DEFAULT_NARRATION;
    }

    // 1.21: ButtonWidget.setMessage(Text)   26.1: AbstractButton.setMessage(Component)
    public void setMessage(LzText text) {
        super.setMessage(text.mc());
    }
}
