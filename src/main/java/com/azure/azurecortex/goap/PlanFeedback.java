package com.azure.azurecortex.goap;

import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.runtime.CortexRuntime;

/**
 * Immutable snapshot written to the blackboard when an action reports a non-{@link PlanFailureReason#NONE} reason via
 * {@code ActionOutcome.Blocked} or {@code ActionOutcome.Failed}.
 * <p>
 * Planners read this on the next planning interval to adjust goal scores. The value is intentionally lightweight — it
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
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public final class PlanFeedback<G> {

    private final PlanFailureReason reason;

    private final int recordedAtTick;

    private final BlockPos failurePos;

    @Nullable
    private final G failedGoalType;

    private final List<BlockPos> blockingPositions;

    /**
     * @param reason            why the action failed or was interrupted
     * @param recordedAtTick    the game tick at which the failure was recorded; planners use this to decide whether the
     *                          feedback is still fresh enough to act on
     * @param failurePos        world position where the failure occurred, or the agent's position at the time of
     *                          failure
     * @param failedGoalType    the goal type that was active when the failure occurred, or {@code null} if none was
     *                          active
     * @param blockingPositions the specific block position(s) an action traced as actually preventing progress — empty
     *                          if the action didn't identify any specific block(s)
     */
    public PlanFeedback(
        PlanFailureReason reason,
        int recordedAtTick,
        BlockPos failurePos,
        @Nullable G failedGoalType,
        List<BlockPos> blockingPositions
    ) {
        this.reason = reason;
        this.recordedAtTick = recordedAtTick;
        this.failurePos = failurePos;
        this.failedGoalType = failedGoalType;
        this.blockingPositions = blockingPositions;
    }

    public static <G> PlanFeedback<G> of(
        PlanFailureReason reason,
        int currentTick,
        BlockPos failurePos,
        @Nullable G failedGoalType
    ) {
        return new PlanFeedback<>(reason, currentTick, failurePos, failedGoalType, Collections.emptyList());
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

    public PlanFailureReason reason() {
        return reason;
    }

    public int recordedAtTick() {
        return recordedAtTick;
    }

    public BlockPos failurePos() {
        return failurePos;
    }

    @Nullable
    public G failedGoalType() {
        return failedGoalType;
    }

    public List<BlockPos> blockingPositions() {
        return blockingPositions;
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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof PlanFeedback))
            return false;
        PlanFeedback<?> other = (PlanFeedback<?>) o;
        return recordedAtTick == other.recordedAtTick && reason == other.reason
            && Objects.equals(failurePos, other.failurePos) && Objects.equals(failedGoalType, other.failedGoalType)
            && Objects.equals(blockingPositions, other.blockingPositions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reason, recordedAtTick, failurePos, failedGoalType, blockingPositions);
    }

    @Override
    public String toString() {
        return "PlanFeedback[reason=" + reason + ", recordedAtTick=" + recordedAtTick + ", failurePos=" + failurePos
            + ", failedGoalType=" + failedGoalType + ", blockingPositions=" + blockingPositions + "]";
    }
}
