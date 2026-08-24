package com.azure.azurecortex.goap;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.runtime.CortexRuntime;

/**
 * Immutable snapshot written to the blackboard when an action reports a non-{@link PlanFailureReason#NONE} reason via
 * {@code ActionOutcome.Blocked} or {@code ActionOutcome.Failed}.
 * <p>
 * Planners read this on the next planning interval to adjust goal scores. The record is intentionally lightweight — it
 * only captures information that is cheap to collect at action termination time.
 * <h3>Lifecycle</h3>
 * <ol>
 * <li>An action's {@code tick()} returns {@code ActionOutcome.blocked(reason, ...)} (still running, early signal) or
 * {@code ActionOutcome.failed(reason, ...)} (terminating).</li>
 * <li>{@link CortexRuntime} — not the action itself — constructs the {@link PlanFeedback} and stores it under
 * {@link CommonBlackboardKeys#LAST_PLAN_FEEDBACK}, centralizing this so no action can forget to report.</li>
 * <li>The planner reads the feedback, applies score modifiers, then <b>clears</b> the key so stale feedback does not
 * bleed into future planning cycles.</li>
 * </ol>
 *
 * @param <G>               the mod-defined goal-type enum
 * @param reason            why the action failed or was interrupted
 * @param recordedAtTick    the game tick at which the failure was recorded; planners use this to decide whether the
 *                          feedback is still fresh enough to act on
 * @param failurePos        world position where the failure occurred, or the agent's position at the time of failure
 * @param failedGoalType    the goal type that was active when the failure occurred, or {@code null} if none was active
 * @param blockingPositions the specific block position(s) an action traced as actually preventing progress — empty if
 *                          the action didn't identify any specific block(s)
 */
@SuppressWarnings("unused")
public record PlanFeedback<G>(
    PlanFailureReason reason,
    int recordedAtTick,
    BlockPos failurePos,
    @Nullable G failedGoalType,
    List<BlockPos> blockingPositions
) {

    public static <G> PlanFeedback<G> of(
        PlanFailureReason reason,
        int currentTick,
        BlockPos failurePos,
        @Nullable G failedGoalType
    ) {
        return new PlanFeedback<>(reason, currentTick, failurePos, failedGoalType, List.of());
    }

    /**
     * Like {@link #of(PlanFailureReason, int, BlockPos, Object)}, but additionally attaches the specific block
     * position(s) an action traced as actually responsible for the failure.
     */
    public static <G> PlanFeedback<G> of(
        PlanFailureReason reason,
        int currentTick,
        BlockPos failurePos,
        @Nullable G failedGoalType,
        List<BlockPos> blockingPositions
    ) {
        return new PlanFeedback<>(reason, currentTick, failurePos, failedGoalType, blockingPositions);
    }

    /**
     * @param currentTick the tick to compare against
     * @return {@code true} if this feedback is no more than 80 ticks old
     */
    public boolean isFresh(int currentTick) {
        return (currentTick - recordedAtTick) <= 80;
    }

    public boolean isNone() {
        return reason == PlanFailureReason.NONE;
    }
}
