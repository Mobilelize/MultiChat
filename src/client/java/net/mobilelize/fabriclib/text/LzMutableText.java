package net.mobilelize.fabriclib.text;

import net.minecraft.network.chat.MutableComponent;

/**
 * Stable wrapper for MC MutableComponent.
 * 1.21 (Yarn): net.minecraft.text.MutableText
 * 26.1 (Mojang): net.minecraft.network.chat.MutableComponent
 */
public final class LzMutableText extends LzText {

    private final MutableComponent mutable;

    public LzMutableText(MutableComponent mutable) {
        super(mutable);
        this.mutable = mutable;
    }

    @Override
    public MutableComponent mc() { return mutable; }

    @Override
    public LzMutableText append(LzText other) {
        // unchanged: text.append(other)
        mutable.append(other.mc());
        return this;
    }

    // 1.21: text.copy()   26.1: component.copy()
    @Override
    public LzMutableText copy() { return new LzMutableText(mutable.copy()); }

    /** Applies an LzStyle to this text. */
    public LzMutableText withStyle(LzStyle style) {
        // 1.21: text.setStyle(style)
        mutable.setStyle(style.mc());
        return this;
    }

    // 1.21: MutableText.withColor(int)   26.1: MutableComponent.withColor(int)
    public LzMutableText withColor(int rgb) {
        mutable.withColor(rgb);
        return this;
    }
}
