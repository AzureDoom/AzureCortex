package com.azure.azurecortex.api.action;

import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

import com.azure.azurecortex.goap.PlanFailureReason;
import com.azure.azurecortex.goap.PlanFeedback;
import com.azure.azurecortex.runtime.CortexRuntime;

/**
 * The result of a single {@link Action#tick} call.
 * <h3>Why actions return this instead of writing feedback themselves</h3> If every action decided for itself whether
 * and how to write {@link PlanFeedback} to the blackboard on failure, that convention would be easy to follow
 * inconsistently and easy for a new action to forget entirely. {@link ActionOutcome} makes "why did this not go well"
 * part of the return value itself, so {@link CortexRuntime} can extract it and write {@link PlanFeedback} in exactly
 * one place, for every action, unconditionally.
 * <h3>The four cases</h3>
 * <ul>
 * <li>{@link Running} — nothing noteworthy this tick; keep executing, no feedback written.</li>
 * <li>{@link Success} — the action achieved its goal; it is stopped with {@link ActionStatus#SUCCESS}, no feedback
 * written.</li>
 * <li>{@link Blocked} — the action hit a recoverable obstacle <em>and is still trying</em> (e.g. a repath attempt
 * failed, but the action hasn't given up yet). The runtime writes {@link PlanFeedback} from {@code reason}/{@code at}
 * <em>without</em> stopping the action, so GOAP can start biasing scores away from the current strategy in real time,
 * well before any hard failure cap is reached.</li>
 * <li>{@link Failed} — the action has given up entirely. The runtime writes {@link PlanFeedback} (unless {@code reason}
 * is {@link PlanFailureReason#NONE}) and stops the action with {@link ActionStatus#FAILURE}.</li>
 * </ul>
 * These four are the only implementations — the 1.12.2 port targets Java 8, so the closed hierarchy is enforced by
 * convention (and by {@link CortexRuntime} rejecting anything else) rather than by a {@code sealed} interface.
 * <h3>Goal-type attribution</h3> By default, the runtime attributes {@link Blocked}/{@link Failed} feedback to whatever
 * goal type is currently active on the blackboard. Some actions fire opportunistically outside the goal their failure
 * is conceptually "about". For those cases, {@code goalType} lets the action override the attribution explicitly
 * instead of it being silently misattributed to whatever goal happened to be active.
 * <p>
 * Note there is no "Interrupted" case here: an action never decides for itself that it was interrupted — only
 * {@link CortexRuntime} does that, when it preempts a running action in favor of a higher-priority or emergency
 * candidate. An action that can no longer proceed should return {@link Failed} (with a reason if one applies, or
 * {@link #failed()} if none does); the runtime is the only source of {@link ActionStatus#INTERRUPTED}.
 *
 * @param <G> the mod-defined goal-type enum this outcome's feedback may be attributed to
 */
@SuppressWarnings("unused")
public interface ActionOutcome<G> {

    /** Nothing noteworthy this tick; keep executing. Prefer the shared {@link ActionOutcome#running()} instance. */
    final class Running<G> implements ActionOutcome<G> {

        @Override
        public String toString() {
            return "Running[]";
        }
    }

    /** The action achieved its goal. Prefer the shared {@link ActionOutcome#success()} instance. */
    final class Success<G> implements ActionOutcome<G> {

        @Override
        public String toString() {
            return "Success[]";
        }
    }

    /**
     * The action hit a recoverable obstacle and is still trying — it keeps running, but the runtime surfaces
     * {@code reason} to GOAP as {@link PlanFeedback} this tick so the planner can start responding before any hard
     * failure cap forces a full stop.
     */
    final class Blocked<G> implements ActionOutcome<G> {

        private final PlanFailureReason reason;

        @Nullable
        private final BlockPos at;

        @Nullable
        private final G goalType;

        private final List<BlockPos> blockingPositions;

        /**
         * @param reason            why this tick didn't make the progress it wanted
         * @param at                where the obstruction was observed, or {@code null} to default to the agent's
         *                          current position
         * @param goalType          overrides which goal type this feedback is attributed to, or {@code null} to use
         *                          whatever goal is currently active on the blackboard (the common case)
         * @param blockingPositions the specific block position(s) actually preventing progress, as traced by the action
         *                          itself (e.g. a wall column between the agent and its target) — empty if the action
         *                          didn't identify any specific block(s), just that something is wrong
         */
        public Blocked(
            PlanFailureReason reason,
            @Nullable BlockPos at,
            @Nullable G goalType,
            List<BlockPos> blockingPositions
        ) {
            this.reason = reason;
            this.at = at;
            this.goalType = goalType;
            this.blockingPositions = blockingPositions;
        }

        public PlanFailureReason reason() {
            return reason;
        }

        @Nullable
        public BlockPos at() {
            return at;
        }

        @Nullable
        public G goalType() {
            return goalType;
        }

        public List<BlockPos> blockingPositions() {
            return blockingPositions;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof Blocked))
                return false;
            Blocked<?> other = (Blocked<?>) o;
            return reason == other.reason && Objects.equals(at, other.at)
                && Objects.equals(goalType, other.goalType)
                && Objects.equals(blockingPositions, other.blockingPositions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reason, at, goalType, blockingPositions);
        }

        @Override
        public String toString() {
            return "Blocked[reason=" + reason + ", at=" + at + ", goalType=" + goalType + ", blockingPositions="
                + blockingPositions + "]";
        }
    }

    /**
     * The action has given up entirely and will be stopped with {@link ActionStatus#FAILURE}.
     */
    final class Failed<G> implements ActionOutcome<G> {

        private final PlanFailureReason reason;

        @Nullable
        private final BlockPos at;

        @Nullable
        private final G goalType;

        private final List<BlockPos> blockingPositions;

        /**
         * @param reason            why the action failed, or {@link PlanFailureReason#NONE} if there's nothing worth
         *                          reporting to GOAP (the runtime skips writing {@link PlanFeedback} in that case)
         * @param at                where the failure occurred, or {@code null} to default to the agent's current
         *                          position
         * @param goalType          overrides which goal type this feedback is attributed to, or {@code null} to use
         *                          whatever goal is currently active on the blackboard (the common case)
         * @param blockingPositions the specific block position(s) actually preventing progress — see
         *                          {@link Blocked#blockingPositions()}
         */
        public Failed(
            PlanFailureReason reason,
            @Nullable BlockPos at,
            @Nullable G goalType,
            List<BlockPos> blockingPositions
        ) {
            this.reason = reason;
            this.at = at;
            this.goalType = goalType;
            this.blockingPositions = blockingPositions;
        }

        public PlanFailureReason reason() {
            return reason;
        }

        @Nullable
        public BlockPos at() {
            return at;
        }

        @Nullable
        public G goalType() {
            return goalType;
        }

        public List<BlockPos> blockingPositions() {
            return blockingPositions;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof Failed))
                return false;
            Failed<?> other = (Failed<?>) o;
            return reason == other.reason && Objects.equals(at, other.at)
                && Objects.equals(goalType, other.goalType)
                && Objects.equals(blockingPositions, other.blockingPositions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reason, at, goalType, blockingPositions);
        }

        @Override
        public String toString() {
            return "Failed[reason=" + reason + ", at=" + at + ", goalType=" + goalType + ", blockingPositions="
                + blockingPositions + "]";
        }
    }

    /** @return a shared, allocation-free "still running, nothing to report" instance */
    @SuppressWarnings("unchecked")
    static <G> ActionOutcome<G> running() {
        return (ActionOutcome<G>) RUNNING_INSTANCE;
    }

    /** @return a shared, allocation-free "succeeded" instance */
    @SuppressWarnings("unchecked")
    static <G> ActionOutcome<G> success() {
        return (ActionOutcome<G>) SUCCESS_INSTANCE;
    }

    Running<Object> RUNNING_INSTANCE = new Running<>();

    Success<Object> SUCCESS_INSTANCE = new Success<>();

    static <G> ActionOutcome<G> blocked(PlanFailureReason reason, BlockPos at) {
        return new Blocked<>(reason, at, null, Collections.emptyList());
    }

    static <G> ActionOutcome<G> blocked(PlanFailureReason reason) {
        return new Blocked<>(reason, null, null, Collections.emptyList());
    }

    static <G> ActionOutcome<G> blocked(PlanFailureReason reason, BlockPos at, G goalType) {
        return new Blocked<>(reason, at, goalType, Collections.emptyList());
    }

    static <G> ActionOutcome<G> blocked(PlanFailureReason reason, BlockPos at, List<BlockPos> blockingPositions) {
        return new Blocked<>(reason, at, null, blockingPositions);
    }

    static <G> ActionOutcome<G> failed(PlanFailureReason reason, BlockPos at) {
        return new Failed<>(reason, at, null, Collections.emptyList());
    }

    static <G> ActionOutcome<G> failed(PlanFailureReason reason) {
        return new Failed<>(reason, null, null, Collections.emptyList());
    }

    static <G> ActionOutcome<G> failed(PlanFailureReason reason, BlockPos at, G goalType) {
        return new Failed<>(reason, at, goalType, Collections.emptyList());
    }

    static <G> ActionOutcome<G> failed(PlanFailureReason reason, BlockPos at, List<BlockPos> blockingPositions) {
        return new Failed<>(reason, at, null, blockingPositions);
    }

    static <G> ActionOutcome<G> failed(PlanFailureReason reason, G goalType) {
        return new Failed<>(reason, null, goalType, Collections.emptyList());
    }

    /**
     * @return a {@link Failed} outcome with no reason worth reporting to GOAP (no {@link PlanFeedback} is written) —
     *         use for genuinely uninteresting failures, not as a default to avoid picking a real reason
     */
    static <G> ActionOutcome<G> failed() {
        return new Failed<>(PlanFailureReason.NONE, null, null, Collections.emptyList());
    }
}
