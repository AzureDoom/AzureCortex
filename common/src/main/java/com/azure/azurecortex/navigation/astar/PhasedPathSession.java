package com.azure.azurecortex.navigation.astar;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.function.Supplier;

/**
 * Chains a sequence of {@link IncrementalPathSession} attempts — e.g. "try the crawl-aware route to the target, then a
 * relaxed-radius crawl route, then a plain ground route, then give up" — while spending only one shared per-tick node
 * budget across the whole chain, instead of each fallback stage running its own separate synchronous search.
 * <h3>Why this exists</h3> Without this, a caller driving {@link IncrementalPathSession} directly still has to decide
 * what to do when a session comes back {@link IncrementalPathSession.Status#FAILED}, and the obvious thing to do —
 * immediately try the next fallback strategy synchronously — defeats the point of incremental budgeting: the agent that
 * carefully amortized its primary search across several ticks can still trigger one big synchronous fallback search the
 * moment that primary search fails. {@link PhasedPathSession} makes the entire fallback chain incremental: if phase 1
 * fails after spending only part of this tick's budget, phase 2 starts immediately with whatever budget is left over
 * <em>this same tick</em> — so the chain still resolves in one tick when it can — but if a phase is still working when
 * the budget runs out, the whole chain reports {@link Status#RUNNING} and picks up again next tick exactly where it
 * left off. This gives a hard upper bound on total search work per agent per tick, however many fallback stages it
 * takes to resolve.
 * <h3>Usage</h3>
 *
 * <pre>{@code
 *
 * var phases = List.of(
 *     new PhasedPathSession.Phase(
 *         "PRIMARY_CRAWL",
 *         () -> IncrementalPathSession.crawling(mob, start, crawlGoal, 96, radius, cache)
 *     ),
 *     new PhasedPathSession.Phase(
 *         "RELAXED_CRAWL",
 *         () -> IncrementalPathSession.crawling(mob, start, crawlGoal, 96, Math.max(radius, 1), cache)
 *     ),
 *     new PhasedPathSession.Phase(
 *         "NORMAL_ASTAR",
 *         () -> IncrementalPathSession.normal(mob, start, target.blockPosition(), 64, Math.max(radius, 1))
 *     )
 * );
 *
 * var session = new PhasedPathSession(phases);
 *
 * // ... once per tick, until it stops returning RUNNING:
 * var status = session.step(budget);
 * }</pre>
 */
@SuppressWarnings("unused")
public final class PhasedPathSession {

    public enum Status {
        RUNNING,
        DONE,
        FAILED
    }

    /**
     * One stage of the fallback chain. {@code start} is a {@link Supplier} rather than an already-built
     * {@link IncrementalPathSession} so earlier phases aren't paid for unless the chain actually reaches them.
     *
     * @param name  a short, stable identifier for diagnostics/logging (e.g. {@code "PRIMARY_CRAWL"})
     * @param start builds the {@link IncrementalPathSession} for this phase, called only when the chain reaches it
     */
    public record Phase(
        String name,
        Supplier<IncrementalPathSession> start
    ) {}

    private final List<Phase> phases;

    private int phaseIndex = 0;

    private IncrementalPathSession current;

    private String activePhaseName;

    private Status status = Status.RUNNING;

    private List<BlockPos> result = List.of();

    public PhasedPathSession(List<Phase> phases) {
        this.phases = phases;
    }

    /**
     * Spends up to {@code totalBudget} node expansions across the fallback chain this tick, advancing to the next phase
     * (with whatever budget is left over) any time the current phase fails outright rather than waiting for a fresh
     * tick.
     *
     * @param totalBudget the total node-expansion budget for this tick, shared across however many phases it takes
     * @return {@link Status#RUNNING} if a phase is still working when the budget ran out, {@link Status#DONE} if some
     *         phase produced a path (available via {@link #result()}), or {@link Status#FAILED} if every phase was
     *         tried and none produced even a partial path
     */
    public Status step(int totalBudget) {
        if (status != Status.RUNNING)
            return status;

        var remaining = totalBudget;

        while (remaining > 0) {
            if (current == null) {
                if (phaseIndex >= phases.size()) {
                    status = Status.FAILED;
                    result = List.of();
                    return status;
                }
                var phase = phases.get(phaseIndex);
                current = phase.start().get();
                activePhaseName = phase.name();
            }

            var before = current.nodesSearched();
            var phaseStatus = current.step(remaining);
            var spent = current.nodesSearched() - before;
            // Guarantee forward progress through the phase list even if a degenerate phase reports FAILED having
            // spent zero nodes — without this, such a phase would loop forever consuming none of `remaining`.
            remaining -= Math.max(spent, 1);

            if (phaseStatus == IncrementalPathSession.Status.RUNNING) {
                return Status.RUNNING;
            }

            if (phaseStatus == IncrementalPathSession.Status.DONE) {
                result = current.result();
                status = Status.DONE;
                return status;
            }

            // FAILED: this phase is done for good; move to the next one and keep spending this tick's leftover
            // budget rather than waiting for the next tick to even start trying the fallback.
            current = null;
            phaseIndex++;
        }

        return Status.RUNNING;
    }

    public Status status() {
        return status;
    }

    public List<BlockPos> result() {
        return result;
    }

    /** The name of the phase currently being searched, or the last phase tried if the chain has finished. */
    public String activePhaseName() {
        return activePhaseName;
    }
}
