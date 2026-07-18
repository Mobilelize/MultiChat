package net.mobilelize.fabriclib.text;

import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.OrderedText;

import java.util.List;



/**
 * Stable wrapper for MC OrderedText (immutable pre-rendered glyph sequence).
 * 1.21 (Yarn): net.minecraft.text.OrderedText
 * 26.1 (Mojang): VERIFY
 */
public final class LzOrderedText {

    // 1.21: OrderedText.EMPTY   26.1: VERIFY
    public static final LzOrderedText EMPTY = new LzOrderedText(OrderedText.EMPTY);

    private final OrderedText text;

    public LzOrderedText(OrderedText text) { this.text = text; }

    /** Raw MC OrderedText — escape hatch. */
    public OrderedText mc() { return text; }

    // 1.21: text.accept(visitor)   26.1: VERIFY
    public boolean accept(CharacterVisitor visitor) { return text.accept(visitor); }

    // Lz visitor — provides LzStyle instead of raw Style
    @FunctionalInterface
    public interface LzCharacterVisitor {
        boolean accept(int index, LzStyle style, int codePoint);
    }

    public boolean lzAccept(LzCharacterVisitor visitor) {
        return text.accept((idx, style, cp) -> visitor.accept(idx, new LzStyle(style), cp));
    }

    // ── Static factories ──────────────────────────────────────────────────────

    /** Wraps an existing MC OrderedText — use only at mixin boundaries. */
    public static LzOrderedText wrap(OrderedText text) { return new LzOrderedText(text); }

    // 1.21: text.asOrderedText()   26.1: component.getVisualOrderText() (VERIFY)
    public static LzOrderedText of(LzText text) { return new LzOrderedText(text.mc().asOrderedText()); }

    // 1.21: OrderedText.concat(a, b)   26.1: VERIFY
    public static LzOrderedText concat(LzOrderedText a, LzOrderedText b) {
        return new LzOrderedText(OrderedText.concat(a.text, b.text));
    }

    // 1.21: OrderedText.concat(List)   26.1: VERIFY
    public static LzOrderedText concat(List<LzOrderedText> texts) {
        return new LzOrderedText(OrderedText.concat(texts.stream().map(t -> t.text).toList()));
    }
}
