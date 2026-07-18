package net.mobilelize.fabriclib.text;

import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;

import java.util.List;



/**
 * Stable wrapper for MC FormattedCharSequence (immutable pre-rendered glyph sequence).
 * 1.21 (Yarn): net.minecraft.text.OrderedText
 * 26.1 (Mojang): net.minecraft.util.FormattedCharSequence
 */
public final class LzOrderedText {

    // 1.21: OrderedText.EMPTY   26.1: FormattedCharSequence.EMPTY
    public static final LzOrderedText EMPTY = new LzOrderedText(FormattedCharSequence.EMPTY);

    private final FormattedCharSequence text;

    public LzOrderedText(FormattedCharSequence text) { this.text = text; }

    /** Raw MC FormattedCharSequence — escape hatch. */
    public FormattedCharSequence mc() { return text; }

    // 1.21: text.accept(visitor)   26.1: text.accept(FormattedCharSink)
    public boolean accept(FormattedCharSink visitor) { return text.accept(visitor); }

    // Lz visitor — provides LzStyle instead of raw Style
    @FunctionalInterface
    public interface LzCharacterVisitor {
        boolean accept(int index, LzStyle style, int codePoint);
    }

    public boolean lzAccept(LzCharacterVisitor visitor) {
        return text.accept((idx, style, cp) -> visitor.accept(idx, new LzStyle(style), cp));
    }

    // ── Static factories ──────────────────────────────────────────────────────

    /** Wraps an existing MC FormattedCharSequence — use only at mixin boundaries. */
    public static LzOrderedText wrap(FormattedCharSequence text) { return new LzOrderedText(text); }

    // 1.21: text.asOrderedText()   26.1: component.getVisualOrderText()
    public static LzOrderedText of(LzText text) { return new LzOrderedText(text.mc().getVisualOrderText()); }

    // 1.21: OrderedText.concat(a, b)   26.1: FormattedCharSequence.composite(a, b)
    public static LzOrderedText concat(LzOrderedText a, LzOrderedText b) {
        return new LzOrderedText(FormattedCharSequence.composite(a.text, b.text));
    }

    // 1.21: OrderedText.concat(List)   26.1: FormattedCharSequence.composite(list)
    public static LzOrderedText concat(List<LzOrderedText> texts) {
        return new LzOrderedText(FormattedCharSequence.composite(texts.stream().map(t -> t.text).toList()));
    }
}
