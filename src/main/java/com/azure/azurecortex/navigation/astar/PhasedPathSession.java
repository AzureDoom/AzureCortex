package com.azure.azurecortex.navigation.astar;

import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Chains a sequence of {@link IncrementalPathSession} attempts — e.g. "try a tight-radius route to the target, then a
 * relaxed-radius route, then a route to the target's last known position, then give up" — while spending only one
 * shared per-tick node budget across the whole chain, instead of each fallback stage running its own separate
 * synchronous search.
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
 * <pre>
 *
 * {
 *     &#64;code
 *     List<PhasedPathSession.Phase> phases = Arrays.asList(
 *         new PhasedPathSession.Phase(
 *             "PRIMARY",
 *             () -> IncrementalPathSession.normal(mob, start, goal, 64, radius)
 *         ),
 *         new PhasedPathSession.Phase(
 *             "RELAXED",
 *             () -> IncrementalPathSession.normal(mob, start, goal, 64, Math.max(radius, 3))
 *         )
 *     );
 *
 *     PhasedPathSession session = new PhasedPathSession(phases);
 *
 *     // ... once per tick, until it stops returning RUNNING:
 *     PhasedPathSession.Status status = session.step(budget);
 * }
 * </pre>
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
     * {@link IncrementalPathSession} so later phases aren't paid for unless the chain actually reaches them.
     */
    public static final class Phase {

        private final String name;

        private final Supplier<IncrementalPathSession> start;

        /**
         * @param name  a short, stable identifier for diagnostics/logging (e.g. {@code "PRIMARY"})
         * @param start builds the {@link IncrementalPathSession} for this phase, called only when the chain reaches it
         */
        public Phase(String name, Supplier<IncrementalPathSession> start) {
            this.name = name;
            this.start = start;
        }

        public String name() {
            return name;
        }

        public Supplier<IncrementalPathSession> start() {
            return start;
        }

        @Override
        public String toString() {
            return "Phase[name=" + name + "]";
        }
    }

    private final List<Phase> phases;

    private int phaseIndex = 0;

    private IncrementalPathSession current;

    private String activePhaseName;

    private Status status = Status.RUNNING;

    private List<BlockPos> result = Collections.emptyList();

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

        int remaining = totalBudget;

        while (remaining > 0) {
            if (current == null) {
                if (phaseIndex >= phases.size()) {
                    status = Status.FAILED;
                    result = Collections.emptyList();
                    return status;
                }
                Phase phase = phases.get(phaseIndex);
                current = phase.start().get();
                activePhaseName = phase.name();
            }

            int before = current.nodesSearched();
            IncrementalPathSession.Status phaseStatus = current.step(remaining);
            int spent = current.nodesSearched() - before;
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
