package net.mobilelize.fabriclib.client;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.mobilelize.fabriclib.text.LzOrderedText;
import org.jetbrains.annotations.Nullable;

/**
 * Stable wrapper for a rendered chat line (MC ChatHudLine.Visible).
 * 1.21 (Yarn): net.minecraft.client.gui.hud.ChatHudLine.Visible
 * 26.1 (Mojang): VERIFY
 */
public final class LzChatLine {

    private final ChatHudLine.Visible line;
    @Nullable private LzChatEntry entry;

    public LzChatLine(ChatHudLine.Visible line) { this.line = line; }

    @Nullable
    public LzChatEntry entry() { return entry; }

    /** Raw MC ChatHudLine.Visible — escape hatch. */
    public ChatHudLine.Visible mc() { return line; }

    // 1.21: line.addedTime()   26.1: VERIFY
    public int addedTime() { return line.addedTime(); }

    // 1.21: line.content()   26.1: VERIFY
    public LzOrderedText content() { return LzOrderedText.wrap(line.content()); }

    // 1.21: line.indicator()   26.1: VERIFY
    @Nullable
    public LzMessageIndicator indicator() { return LzMessageIndicator.wrapNullable(line.indicator()); }

    // 1.21: line.endOfEntry()   26.1: VERIFY
    public boolean endOfEntry() { return line.endOfEntry(); }

    // ── Factory ───────────────────────────────────────────────────────────────

    /** Creates a new chat line. Indicator may be null. */
    public static LzChatLine create(int addedTime, LzOrderedText content,
                                    @Nullable LzMessageIndicator indicator, boolean endOfEntry) {
        return new LzChatLine(new ChatHudLine.Visible(
            addedTime, content.mc(),
            indicator == null ? null : indicator.mc(),
            endOfEntry));
    }

    /** Creates a new chat line with a logical-message entry for cross-panel identity. */
    public static LzChatLine create(int addedTime, LzOrderedText content,
                                    @Nullable LzMessageIndicator indicator, boolean endOfEntry,
                                    @Nullable LzChatEntry entry) {
        LzChatLine l = new LzChatLine(new ChatHudLine.Visible(
            addedTime, content.mc(),
            indicator == null ? null : indicator.mc(),
            endOfEntry));
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
