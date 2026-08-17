package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Background worker for ChatManager.refilterSearch/refilterTab's "Optimized Refiltering" offload.
 * Same shape as WrapCoordinator: the scan runs on a single background thread, and the result is
 * handed back to the main thread via {@link LzClient#send} the instant it's ready — nothing polls
 * this class.
 *
 * Live messages are NOT intercepted or queued — ChatManager.processMessage/addSearchMessageWithCollapse
 * apply them straight to the tab exactly as they always do, whether or not a refilter is in flight
 * for it. When the background result lands, ChatManager's apply step (applySearchRefilterResult/
 * applyFilterTabResult) preserves whatever landed on the tab since the scan started — see their own
 * docs — instead of this class needing to know anything about individual messages.
 */
public final class FilterCoordinator {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MultiChat-FilterWorker");
        t.setDaemon(true);
        return t;
    });

    private FilterCoordinator() {}

    /** {@code compute} runs on the background thread and must return a {@code Runnable} that applies
     *  the finished result on the main thread. That Runnable is invoked from inside a generation
     *  check via LzClient.send() — if this tab's refilterGeneration moved on since this job was
     *  submitted, the result is dropped instead of applied. */
    public static void submit(ChatTab tab, long generation, Supplier<Runnable> compute) {
        EXECUTOR.execute(() -> {
            // Same reasoning as WrapCoordinator's stale-job check: a burst of refilter triggers for
            // the same tab (rapid query edits, filter-rule saves) queues one job per fire on this
            // single worker thread — skip before spending any compute on one that's already
            // superseded instead of grinding through every stale scan in full first.
            if (generation != tab.refilterGeneration()) return;

            Runnable apply;
            try {
                apply = compute.get();
            } catch (Exception e) {
                MultichatClient.LOGGER.warn("MultiChat: background refilter job failed", e);
                return;
            }

            LzClient.send(() -> {
                if (generation != tab.refilterGeneration()) return; // canceled/superseded
                apply.run();
            });
        });
    }
}
