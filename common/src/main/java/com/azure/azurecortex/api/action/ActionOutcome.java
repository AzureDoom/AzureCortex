package com.azure.azurecortex.api.action;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
public sealed interface ActionOutcome<G> {

    record Running<G>() implements ActionOutcome<G> {}

    record Success<G>() implements ActionOutcome<G> {}

    /**
     * The action hit a recoverable obstacle and is still trying — it keeps running, but the runtime surfaces
     * {@code reason} to GOAP as {@link PlanFeedback} this tick so the planner can start responding before any hard
     * failure cap forces a full stop.
     *
     * @param reason            why this tick didn't make the progress it wanted
     * @param at                where the obstruction was observed, or {@code null} to default to the agent's current
     *                          position
     * @param goalType          overrides which goal type this feedback is attributed to, or {@code null} to use
     *                          whatever goal is currently active on the blackboard (the common case)
     * @param blockingPositions the specific block position(s) actually preventing progress, as traced by the action
     *                          itself (e.g. a wall column between the agent and its target) — empty if the action
     *                          didn't identify any specific block(s), just that something is wrong
     */
    record Blocked<G>(
        PlanFailureReason reason,
        @Nullable BlockPos at,
        @Nullable G goalType,
        List<BlockPos> blockingPositions
    ) implements ActionOutcome<G> {}

    /**
     * The action has given up entirely and will be stopped with {@link ActionStatus#FAILURE}.
     *
     * @param reason            why the action failed, or {@link PlanFailureReason#NONE} if there's nothing worth
     *                          reporting to GOAP (the runtime skips writing {@link PlanFeedback} in that case)
     * @param at                where the failure occurred, or {@code null} to default to the agent's current position
     * @param goalType          overrides which goal type this feedback is attributed to, or {@code null} to use
     *                          whatever goal is currently active on the blackboard (the common case)
     * @param blockingPositions the specific block position(s) actually preventing progress — see
     *                          {@link Blocked#blockingPositions()}
     */
    record Failed<G>(
        PlanFailureReason reason,
        @Nullable BlockPos at,
        @Nullable G goalType,
        List<BlockPos> blockingPositions
    ) implements ActionOutcome<G> {}

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
        return new Blocked<>(reason, at, null, List.of());
    }

    static <G> ActionOutcome<G> blocked(PlanFailureReason reason) {
        return new Blocked<>(reason, null, null, List.of());
    }

    static <G> ActionOutcome<G> blocked(PlanFailureReason reason, BlockPos at, G goalType) {
        return new Blocked<>(reason, at, goalType, List.of());
    }

    static <G> ActionOutcome<G> blocked(PlanFailureReason reason, BlockPos at, List<BlockPos> blockingPositions) {
        return new Blocked<>(reason, at, null, blockingPositions);
    }

    static <G> ActionOutcome<G> failed(PlanFailureReason reason, BlockPos at) {
        return new Failed<>(reason, at, null, List.of());
    }

    static <G> ActionOutcome<G> failed(PlanFailureReason reason) {
        return new Failed<>(reason, null, null, List.of());
    }

    static <G> ActionOutcome<G> failed(PlanFailureReason reason, BlockPos at, G goalType) {
        return new Failed<>(reason, at, goalType, List.of());
    }

    static <G> ActionOutcome<G> failed(PlanFailureReason reason, BlockPos at, List<BlockPos> blockingPositions) {
        return new Failed<>(reason, at, null, blockingPositions);
    }

    static <G> ActionOutcome<G> failed(PlanFailureReason reason, G goalType) {
        return new Failed<>(reason, null, goalType, List.of());
    }

    /**
     * @return a {@link Failed} outcome with no reason worth reporting to GOAP (no {@link PlanFeedback} is written) —
     *         use for genuinely uninteresting failures, not as a default to avoid picking a real reason
     */
    static <G> ActionOutcome<G> failed() {
        return new Failed<>(PlanFailureReason.NONE, null, null, List.of());
    }
}
