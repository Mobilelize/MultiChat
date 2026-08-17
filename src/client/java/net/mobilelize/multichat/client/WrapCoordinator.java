package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background worker for ChatPanel.rebuildTabLines's "Optimized Loading" offload. Deliberately
 * simple: take the exact same synchronous work (ChatPanel.buildTabLines) the sync path already does,
 * snapshot its inputs, run it on a single background thread, and post the result back onto the main
 * thread via {@link LzClient#send} the instant it's ready — nothing polls this class.
 *
 * Live messages are NOT intercepted or queued — ChatManager.addNewMessage applies them straight to
 * the panel's line cache exactly as it always did, unconditionally, whether or not a rebuild is in
 * flight for that tab. Since a rebuild starts by wiping the panel's cache to a skeleton
 * (ChatPanel.showLoadingSkeleton) and live adds always prepend (index 0 = newest), anything that
 * arrives during the build sits in front of the skeleton rows as real, already-correct lines;
 * ChatPanel.applyWrapResult finds that boundary (a plain scan for the first skeleton row) and keeps
 * it in front of the finished result instead of discarding it. No separate tracking needed for that.
 */
public final class WrapCoordinator {

    /** Identifies one tab's line cache on one specific panel — the same tab can be wrapped
     *  differently on different panels (different width/scale), so the generation guard is keyed on
     *  the pair, never the tab alone. */
    private record PanelTab(ChatPanel panel, UUID tabId) {}

    private static final Map<PanelTab, Long> GENERATIONS = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MultiChat-WrapWorker");
        t.setDaemon(true);
        return t;
    });

    private WrapCoordinator() {}

    /** Starts a background rewrap of {@code tab}'s entire history for {@code panel}. Bumps the
     *  generation for this (panel, tab) pair first, so a result from a rebuild superseded before it
     *  finishes is recognized as canceled and dropped when it calls back. {@code snapshot}/
     *  {@code baseWidth}/{@code isPrimary} must be captured on the main thread before calling this. */
    public static void submitRebuild(ChatPanel panel, ChatTab tab, List<ChatTab.MessageEntry> snapshot,
                                      int baseWidth, boolean isPrimary) {
        PanelTab key = new PanelTab(panel, tab.id());
        long generation = GENERATIONS.merge(key, 1L, Long::sum);

        EXECUTOR.execute(() -> {
            // A rebuild trigger firing repeatedly in a burst (settings/scale/width change touching
            // every tab on every panel, a resize drag firing every mouse-move frame — see
            // PERFORMANCE_PLAN.md) queues one job per fire, each superseding the last. The single
            // worker thread processes them strictly in order; without this check it would burn
            // through every stale one IN FULL before ever reaching the one that still matters — skip
            // before spending any compute on a job that's already superseded, checked again below
            // before applying in case it goes stale while THIS job is mid-flight.
            if (GENERATIONS.getOrDefault(key, -1L) != generation) return;

            List<LzChatLine> lines;
            try {
                lines = ChatPanel.buildTabLines(snapshot, baseWidth, isPrimary);
            } catch (Exception e) {
                MultichatClient.LOGGER.warn("MultiChat: background wrap job failed", e);
                return;
            }

            LzClient.send(() -> {
                if (GENERATIONS.getOrDefault(key, -1L) != generation) return; // canceled/superseded
                panel.applyWrapResult(tab, lines);
            });
        });
    }
}
