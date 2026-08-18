package net.mobilelize.fabriclib.client;

import net.mobilelize.fabriclib.text.LzText;
import org.jetbrains.annotations.Nullable;

/**
 * Represents one logical chat message before word-wrap.
 * All LzChatLine objects produced from the same message share the same LzChatEntry instance,
 * enabling identity-based highlight/jump across panels with different widths.
 */
public final class LzChatEntry {

    public final int                creationTick;
    public final LzText             originalContent;
    @Nullable public final LzMessageIndicator indicator;
    /** Whether this message matched a Highlight Words keyword at the moment it was live-routed —
     *  baked in at construction since a refilter's offline rescan has no live highlight pass to
     *  redo (recomputing it would re-trigger sounds/revert-action registration). */
    public final boolean            highlightMatched;

    public LzChatEntry(int creationTick, LzText originalContent,
                       @Nullable LzMessageIndicator indicator) {
        this(creationTick, originalContent, indicator, false);
    }

    public LzChatEntry(int creationTick, LzText originalContent,
                       @Nullable LzMessageIndicator indicator, boolean highlightMatched) {
        this.creationTick     = creationTick;
        this.originalContent  = originalContent;
        this.indicator        = indicator;
        this.highlightMatched = highlightMatched;
    }
}
