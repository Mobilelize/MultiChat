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

    public LzChatEntry(int creationTick, LzText originalContent,
                       @Nullable LzMessageIndicator indicator) {
        this.creationTick    = creationTick;
        this.originalContent = originalContent;
        this.indicator       = indicator;
    }
}
