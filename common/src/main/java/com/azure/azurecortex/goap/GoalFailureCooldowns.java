package com.azure.azurecortex.goap;

import java.util.HashMap;
import java.util.Map;

import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;

/**
 * Tracks per-goal-type failure cooldowns so the planner can suppress goal types that have recently failed, beyond the
 * 80-tick freshness window of {@link PlanFeedback}.
 * <h3>Usage</h3> Store one instance on the blackboard under {@link CommonBlackboardKeys#GOAL_FAILURE_COOLDOWNS}. The
 * planner reads it at the start of {@code chooseGoal} and calls {@link #getPenalty} to get an additive score penalty
 * for each goal type, then records failures via {@link #recordFailure}.
 *
 * <pre>{@code
 * var gfc = GoalFailureCooldowns.<MyGoalType>getOrCreate(blackboard);
 * gfc.tick(currentTick);
 *
 * huntScore -= gfc.getPenalty(MyGoalType.HUNT_TARGET, currentTick);
 *
 * // When a goal fails:
 * gfc.recordFailure(MyGoalType.HUNT_TARGET, currentTick, 100);
 * }</pre>
 *
 * <h3>Penalty decay</h3> The penalty is not binary on/off. It scales linearly from 60 at the moment of failure down to
 * {@code 0} at expiry, so a goal that failed recently is heavily suppressed but the suppression fades gradually.
 *
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public final class GoalFailureCooldowns<G> {

    public static final int DEFAULT_DURATION = 200;

    private record Entry(
        int failedAtTick,
        int durationTicks
    ) {

        boolean isActive(int currentTick) {
            return (currentTick - failedAtTick) < durationTicks;
        }

        float penalty(int currentTick) {
            if (!isActive(currentTick))
                return 0f;
            var elapsed = currentTick - failedAtTick;
            var fraction = 1f - (float) elapsed / durationTicks;
            return 60f * fraction;
        }
    }

    private final Map<G, Entry> entries = new HashMap<>();

    /**
     * Retrieves the instance stored on {@code blackboard}, creating and storing a new one if none exists yet.
     */
    @SuppressWarnings("unchecked")
    public static <G> GoalFailureCooldowns<G> getOrCreate(Blackboard blackboard) {
        var existing = (GoalFailureCooldowns<G>) blackboard.get(CommonBlackboardKeys.GOAL_FAILURE_COOLDOWNS);
        if (existing != null)
            return existing;
        var fresh = new GoalFailureCooldowns<G>();
        blackboard.set(CommonBlackboardKeys.GOAL_FAILURE_COOLDOWNS, fresh);
        return fresh;
    }

    /**
     * Records a failure for {@code goalType}, suppressing it for {@code durationTicks}. If the goal type was already
     * suppressed, the new record replaces the old one only if it would produce a longer suppression window.
     */
    public void recordFailure(G goalType, int currentTick, int durationTicks) {
        var existing = entries.get(goalType);
        if (existing != null) {
            var existingExpiry = existing.failedAtTick() + existing.durationTicks();
            var newExpiry = currentTick + durationTicks;
            if (newExpiry <= existingExpiry)
                return;
        }
        entries.put(goalType, new Entry(currentTick, durationTicks));
    }

    public void recordFailure(G goalType, int currentTick) {
        recordFailure(goalType, currentTick, DEFAULT_DURATION);
    }

    /**
     * Removes all expired entries to keep the map small. Call once per planning cycle before reading penalties.
     */
    public void evictExpired(int currentTick) {
        entries.entrySet().removeIf(e -> !e.getValue().isActive(currentTick));
    }

    /**
     * Returns the current additive score penalty for {@code goalType}. Returns {@code 0} if the goal type has no active
     * failure record.
     */
    public float getPenalty(G goalType, int currentTick) {
        var entry = entries.get(goalType);
        return entry == null ? 0f : entry.penalty(currentTick);
    }

    /** Returns {@code true} if {@code goalType} currently has an active failure record. */
    public boolean isSuppressed(G goalType, int currentTick) {
        var entry = entries.get(goalType);
        return entry != null && entry.isActive(currentTick);
    }
}
