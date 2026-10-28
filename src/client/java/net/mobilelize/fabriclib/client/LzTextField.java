package net.mobilelize.fabriclib.client;

import net.minecraft.client.gui.components.EditBox;
import net.mobilelize.fabriclib.text.LzText;

/**
 * TextFieldWidget with a minimal constructor — no textRenderer arg needed at call site.
 * 1.21 (Yarn): TextFieldWidget(TextRenderer, x, y, w, h, Text)
 * 26.1 (Mojang): VERIFY constructor params and TextRenderer→font rename.
 */
public class LzTextField extends EditBox {

    public LzTextField(int x, int y, int w, int h) {
        super(LzClient.mc().font, x, y, w, h, LzText.empty().mc());
    }
}
