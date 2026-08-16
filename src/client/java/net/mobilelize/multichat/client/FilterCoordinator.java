package net.mobilelize.multichat.client;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

/**
 * Single background worker that off-loads the search/filter matching scan (refilterSearch /
 * refilterTab in ChatManager) — a full history scan, same shape of problem as WrapCoordinator's
 * tab-rebuild wrap, and reusing the exact same pattern: install a loading skeleton
 * (ChatPanel#showLoadingSkeleton), compute the result off the main thread, swap it in wholesale
 * once ready. See PERFORMANCE_PLAN.md item 3.
 *
 * Generic over the two kinds of refilter (search vs. filtered-tab) rather than knowing about either
 * one specifically: {@code compute} runs on the background thread and does the actual pure
 * computation (ChatManager.computeSearchRefilter / computeFilterTab), then returns a small
 * {@code Runnable} that captures the already-computed result and applies it on the main thread —
 * the coordinator itself never needs to know what "applying" means for either kind.
 *
 * Thread safety: {@code compute} must be pure with respect to the tab it's scanning — no
 * mutation of ChatTab/ChatPanel state, only reads of the immutable/rarely-mutated bits already
 * accepted as safe elsewhere in this codebase (filter rules, a message snapshot taken at submit
 * time). See ChatManager's RefilterConfig for what gets snapshotted rather than read live.
 */
public final class FilterCoordinator {

    private record FilterJob(ChatTab tab, long generation, Supplier<Runnable> compute) {}

    private static final LinkedBlockingQueue<FilterJob> JOBS = new LinkedBlockingQueue<>();
    private static final ConcurrentLinkedQueue<Runnable> RESULTS = new ConcurrentLinkedQueue<>();
    private static volatile Thread worker;

    private FilterCoordinator() {}

    public static void submit(ChatTab tab, long generation, Supplier<Runnable> compute) {
        ensureWorkerRunning();
        JOBS.add(new FilterJob(tab, generation, compute));
    }

    /** Applies up to {@code budget} finished results on the calling (main) thread. Called from
     *  ChatRenderer.render() every frame, same as WrapCoordinator.drain(). */
    public static void drain(int budget) {
        int n = 0;
        Runnable r;
        while (n < budget && (r = RESULTS.poll()) != null) {
            r.run();
            n++;
        }
    }

    private static synchronized void ensureWorkerRunning() {
        if (worker != null) return;
        worker = new Thread(FilterCoordinator::workerLoop, "MultiChat-FilterWorker");
        worker.setDaemon(true);
        worker.start();
    }

    private static void workerLoop() {
        while (true) {
            FilterJob job;
            try {
                job = JOBS.take();
            } catch (InterruptedException e) {
                return;
            }
            // A query/filter change fired again before this scan finished — skip before spending
            // any compute on it, same reasoning as WrapCoordinator's stale-job check.
            if (job.generation() != job.tab().refilterGeneration()) continue;
            try {
                Runnable apply = job.compute().get();
                RESULTS.add(apply);
            } catch (Exception e) {
                MultichatClient.LOGGER.warn("MultiChat: background refilter job failed", e);
            }
        }
    }
}
