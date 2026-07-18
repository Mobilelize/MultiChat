package net.mobilelize.fabriclib.client;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.mobilelize.fabriclib.text.LzOrderedText;
import org.jetbrains.annotations.Nullable;

/**
 * Stable wrapper for a rendered chat line.
 * 1.21 (Yarn): net.minecraft.client.gui.hud.ChatHudLine.Visible
 * 26.1 (Mojang): net.minecraft.client.multiplayer.chat.GuiMessage.Line
 *   Line.constructor(GuiMessage parent, FormattedCharSequence content, boolean endOfEntry)
 *   addedTime / tag come from parent GuiMessage, not the Line directly.
 */
public final class LzChatLine {

    private final GuiMessage.Line line;
    @Nullable private LzChatEntry entry;

    public LzChatLine(GuiMessage.Line line) { this.line = line; }

    @Nullable
    public LzChatEntry entry() { return entry; }

    /** Raw MC GuiMessage.Line — escape hatch. */
    public GuiMessage.Line mc() { return line; }

    // 1.21: line.addedTime()   26.1: line.parent().addedTime()  (delegated via record accessor)
    public int addedTime() { return line.addedTime(); }

    // 1.21: line.content()   26.1: line.content()  (stable)
    public LzOrderedText content() { return LzOrderedText.wrap(line.content()); }

    // 1.21: line.indicator()   26.1: line.tag()  (via GuiMessage parent)
    @Nullable
    public LzMessageIndicator indicator() { return LzMessageIndicator.wrapNullable(line.tag()); }

    // 1.21: line.endOfEntry()   26.1: line.endOfEntry()  (stable)
    public boolean endOfEntry() { return line.endOfEntry(); }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Creates a line from an existing GuiMessage parent + content.
     * 26.1: GuiMessage.Line(parent, FormattedCharSequence, boolean) — addedTime/tag come from parent.
     */
    public static LzChatLine create(GuiMessage parent, LzOrderedText content, boolean endOfEntry) {
        return new LzChatLine(new GuiMessage.Line(parent, content.mc(), endOfEntry));
    }

    public static LzChatLine create(GuiMessage parent, LzOrderedText content, boolean endOfEntry,
                                    @Nullable LzChatEntry entry) {
        LzChatLine l = new LzChatLine(new GuiMessage.Line(parent, content.mc(), endOfEntry));
        l.entry = entry;
        return l;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LzChatLine other)) return false;
        return line.equals(other.line);
    }

    @Override
    public int hashCode() { return line.hashCode(); }
}
