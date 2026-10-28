package net.mobilelize.fabriclib.client;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
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
    private boolean startOfEntry;
    private boolean skeleton;

    public LzChatLine(GuiMessage.Line line) { this.line = line; }

    @Nullable
    public LzChatEntry entry() { return entry; }

    /** True for the first physical (pre-wrap) line of its logical entry — symmetric to
     *  {@link #endOfEntry()}, which marks the last. Bolted on like {@link #entry}, since vanilla's
     *  ChatHudLine.Visible record has no room for it. */
    public boolean startOfEntry() { return startOfEntry; }

    /** True for a generic loading-placeholder row — not tied to any real message. Used while a
     *  tab's history is being wrapped on a background thread (see WrapCoordinator); the whole
     *  skeleton block is discarded and replaced wholesale once the real content is ready. */
    public boolean isSkeleton() { return skeleton; }

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

    // 26.1: a Line reads addedTime/tag from a parent GuiMessage. MultiChat lines have no vanilla
    // message behind them, so each gets a stand-in parent carrying just those two values.
    private static GuiMessage parent(int addedTime, @Nullable LzMessageIndicator indicator) {
        return new GuiMessage(addedTime, Component.empty(), null, GuiMessageSource.SYSTEM_CLIENT,
            indicator == null ? null : indicator.mc());
    }

    /**
     * Creates a line from an existing GuiMessage parent + content.
     * 26.1: GuiMessage.Line(parent, FormattedCharSequence, boolean) — addedTime/tag come from parent.
     */
    public static LzChatLine create(GuiMessage parent, LzOrderedText content, boolean endOfEntry) {
        return new LzChatLine(new GuiMessage.Line(parent, content.mc(), endOfEntry));
    }

    /** Creates a new chat line with a logical-message entry for cross-panel identity. */
    public static LzChatLine create(int addedTime, LzOrderedText content,
                                    @Nullable LzMessageIndicator indicator, boolean endOfEntry,
                                    @Nullable LzChatEntry entry, boolean startOfEntry) {
        LzChatLine l = new LzChatLine(new GuiMessage.Line(parent(addedTime, indicator), content.mc(), endOfEntry));
        l.entry = entry;
        l.startOfEntry = startOfEntry;
        return l;
    }

    /** Generic loading-placeholder row — no message content, no entry identity. Just fills space
     *  while a background wrap is in flight. {@code addedTime} should be the current tick (like a
     *  freshly-added real message) — the existing age-based fade/hide rules in ChatRenderer key off
     *  it, and an old/stale addedTime (e.g. 0) reads as an ancient message and gets hidden outright
     *  the moment chat is unfocused instead of fading in normally. */
    public static LzChatLine createSkeleton(int addedTime) {
        LzChatLine l = new LzChatLine(new GuiMessage.Line(parent(addedTime, null), LzOrderedText.EMPTY.mc(), true));
        l.startOfEntry = true;
        l.skeleton = true;
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
