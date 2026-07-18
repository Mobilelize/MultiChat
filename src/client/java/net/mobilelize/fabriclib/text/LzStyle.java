package net.mobilelize.fabriclib.text;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.net.URI;

/**
 * Stable wrapper for MC Style.
 * 1.21 (Yarn): net.minecraft.text.Style
 * 26.1 (Mojang): net.minecraft.network.chat.Style
 */
public final class LzStyle {

    // 1.21: Style.EMPTY   26.1: Style.EMPTY  (stable)
    public static final LzStyle EMPTY = new LzStyle(Style.EMPTY);

    private final Style style;

    public LzStyle(Style style) { this.style = style; }

    // ── Static factories ────────────────────────────────────────────────────

    // 1.21: style.withColor(TextColor.fromRgb(rgb))   26.1: stable
    public static LzStyle ofColor(int rgb) {
        return new LzStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    // 1.21: style.withColor(Formatting)   26.1: style.withColor(ChatFormatting)
    public static LzStyle ofFormatting(LzFormatting formatting) {
        return new LzStyle(Style.EMPTY.withColor(formatting.mc()));
    }

    public static LzStyle empty() { return EMPTY; }

    // ── Instance methods ────────────────────────────────────────────────────

    public LzStyle withColor(int rgb)            { return new LzStyle(style.withColor(TextColor.fromRgb(rgb))); }

    /** Package-private — only called from LzFormatting. */
    LzStyle withFormatting(Formatting formatting) { return new LzStyle(style.withColor(formatting)); }

    public LzStyle withObfuscated(boolean v)     { return new LzStyle(style.withObfuscated(v)); }
    public LzStyle withBold(boolean v)           { return new LzStyle(style.withBold(v)); }
    public LzStyle withStrikethrough(boolean v)  { return new LzStyle(style.withStrikethrough(v)); }
    public LzStyle withUnderline(boolean v)      { return new LzStyle(style.withUnderline(v)); }
    public LzStyle withItalic(boolean v)         { return new LzStyle(style.withItalic(v)); }

    // ── Click events ────────────────────────────────────────────────────────

    public LzStyle withClickRunCommand(String command)      { return new LzStyle(style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))); }
    public LzStyle withClickSuggestCommand(String command)  { return new LzStyle(style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))); }
    public LzStyle withClickOpenUrl(String url)             { return new LzStyle(style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))); }
    public LzStyle withClickCopyToClipboard(String text)    { return new LzStyle(style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))); }

    // ── Hover events ────────────────────────────────────────────────────────

    public LzStyle withHoverText(LzText text)               { return new LzStyle(style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text.mc()))); }

    // ── Raw escape hatches ──────────────────────────────────────────────────

    public LzStyle withClickEvent(ClickEvent event)         { return new LzStyle(style.withClickEvent(event)); }
    public LzStyle withHoverEvent(HoverEvent event)         { return new LzStyle(style.withHoverEvent(event)); }

    /** Raw MC Style — escape hatch. */
    public Style mc() { return style; }
}
