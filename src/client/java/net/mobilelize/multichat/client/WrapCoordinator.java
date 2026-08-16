package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChatLine;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Single background worker that off-loads chat-line wrapping (string measure/wrap) for
 * {@link ChatPanel#rebuildTabLines}'s "optimized loading" path, so a full-history rebuild never
 * blocks the render thread. See PERFORMANCE_PLAN.md item 4.
 *
 * One job = one tab's entire history, wrapped in full (same output as the old synchronous rebuild),
 * off the main thread. There's no incremental/partial apply — when a job finishes, the caller
 * ({@link ChatPanel#applyWrapResult}) swaps the whole result in at once. That's a deliberate
 * simplification over a per-message incremental approach: nothing is visible until the whole tab is
 * done, but there's no position bookkeeping to get wrong, and the game would otherwise be frozen for
 * that same duration anyway — this only moves the wait off the render thread, it doesn't remove it.
 *
 * Thread safety: the worker only does pure data work (string wrap/measure via
 * {@link ChatPanel#buildTabLines}, over a plain list snapshot taken at submit time) — no GL calls,
 * no mutation of live ChatPanel/ChatTab state. Watch item: TextRenderer width lookups can trigger
 * lazy glyph-atlas loading on first use of an unfamiliar codepoint, which touches GL state and isn't
 * safe off the main thread. Believed a non-issue in practice (most chat glyphs get warmed on
 * arrival), but unconfirmed — see PERFORMANCE_PLAN.md.
 */
public final class WrapCoordinator {

    private record WrapJob(ChatPanel panel, ChatTab tab, long generation,
                            List<ChatTab.MessageEntry> entries, int baseWidth, boolean isPrimary) {}

    public record WrapResult(ChatPanel panel, ChatTab tab, long generation, List<LzChatLine> lines) {}

    private static final LinkedBlockingDeque<WrapJob> JOBS = new LinkedBlockingDeque<>();
    private static final ConcurrentLinkedQueue<WrapResult> RESULTS = new ConcurrentLinkedQueue<>();
    private static volatile Thread worker;

    private WrapCoordinator() {}

    /** {@code priority}: jump this job to the front of the queue instead of the back — for the tab
     *  actually on screen right now, so it doesn't wait behind every other (currently invisible)
     *  tab's rebuild. A rebuild trigger (settings/scale/width change) fires one job per tab per
     *  panel; without this, the one tab the player can actually see could be queued anywhere among
     *  all of them. */
    public static void submit(ChatPanel panel, ChatTab tab, long generation,
                               List<ChatTab.MessageEntry> entries, int baseWidth, boolean isPrimary,
                               boolean priority) {
        ensureWorkerRunning();
        WrapJob job = new WrapJob(panel, tab, generation, entries, baseWidth, isPrimary);
        if (priority) JOBS.addFirst(job); else JOBS.addLast(job);
    }

    /** Applies up to {@code budget} finished results on the calling (main) thread. Called from
     *  ChatRenderer.render() every frame (not the tick loop) so a finished background rewrap shows
     *  up on the next frame instead of waiting on a 20Hz tick boundary. Each result is a whole-tab
     *  swap, not a single line, so this budget is just a safety cap against several rebuilds
     *  finishing back-to-back in the same frame — not a per-line frame budget. */
    public static void drain(int budget) {
        int n = 0;
        WrapResult r;
        while (n < budget && (r = RESULTS.poll()) != null) {
            r.panel().applyWrapResult(r);
            n++;
        }
    }

    private static synchronized void ensureWorkerRunning() {
        if (worker != null) return;
        worker = new Thread(WrapCoordinator::workerLoop, "MultiChat-WrapWorker");
        worker.setDaemon(true);
        worker.start();
    }

    private static void workerLoop() {
        while (true) {
            WrapJob job;
            try {
                job = JOBS.takeFirst();
            } catch (InterruptedException e) {
                return;
            }
            // A rebuild trigger that fires repeatedly in a burst (e.g. dragging a slider — see
            // PERFORMANCE_PLAN.md's documented per-frame rebuild triggers) queues one job per fire,
            // each superseding the last. Without this check the worker would burn through every
            // stale one in full before ever reaching the job that still matters, which is exactly
            // what "swap happened but took forever" looks like. applyWrapResult() re-checks this
            // too (the generation could go stale while THIS job is mid-flight), so this is a cheap
            // early skip, not the only guard.
            if (job.generation() != job.panel().currentGeneration(job.tab().id())) continue;
            try {
                List<LzChatLine> lines = ChatPanel.buildTabLines(job.entries(), job.baseWidth(), job.isPrimary());
                RESULTS.add(new WrapResult(job.panel(), job.tab(), job.generation(), lines));
            } catch (Exception e) {
                MultichatClient.LOGGER.warn("MultiChat: background wrap job failed", e);
            }
        }
    }
}
