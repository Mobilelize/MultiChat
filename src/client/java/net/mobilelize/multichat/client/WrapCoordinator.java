package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzClient;

import java.util.List;
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

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MultiChat-WrapWorker");
        t.setDaemon(true);
        return t;
    });

    private WrapCoordinator() {}

    /** Starts a background rewrap of {@code tab}'s entire history for {@code panel}. {@code generation}
     *  must be captured from {@code panel.showLoadingSkeleton(tab)} (or another line-cache bump) on the
     *  main thread before calling this — checked against {@code panel.currentLineGeneration(tab)} both
     *  before compute starts and before the result is applied, so a rebuild superseded by a newer one
     *  (from this coordinator OR FilterCoordinator's refilter — they share the same panel-owned
     *  counter) is recognized as stale and dropped instead of overwriting/duplicating newer content.
     *  {@code snapshot}/{@code baseWidth}/{@code isPrimary} must be captured on the main thread too. */
    public static void submitRebuild(ChatPanel panel, ChatTab tab, List<ChatTab.MessageEntry> snapshot,
                                      int baseWidth, boolean isPrimary, long generation) {
        EXECUTOR.execute(() -> {
            // A rebuild trigger firing repeatedly in a burst (settings/scale/width change touching
            // every tab on every panel, a resize drag firing every mouse-move frame — see
            // PERFORMANCE_PLAN.md) queues one job per fire, each superseding the last. The single
            // worker thread processes them strictly in order; without this check it would burn
            // through every stale one IN FULL before ever reaching the one that still matters — skip
            // before spending any compute on a job that's already superseded, checked again below
            // before applying in case it goes stale while THIS job is mid-flight.
            if (panel.currentLineGeneration(tab) != generation) return;

            List<LzChatLine> lines;
            try {
                lines = ChatPanel.buildTabLines(snapshot, baseWidth, isPrimary);
            } catch (Exception e) {
                MultichatClient.LOGGER.warn("MultiChat: background wrap job failed", e);
                return;
            }

            LzClient.send(() -> {
                if (panel.currentLineGeneration(tab) != generation) return; // canceled/superseded
                panel.applyWrapResult(tab, lines);
            });
        });
    }
}
