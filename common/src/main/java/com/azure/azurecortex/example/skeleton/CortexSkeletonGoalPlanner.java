package com.azure.azurecortex.example.skeleton;

import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.api.goal.GoalUrgency;
import com.azure.azurecortex.goap.*;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * Example {@link GoalPlanner} for {@link CortexSkeletonEntity}: hunt a live target, otherwise investigate a recent
 * sighting, otherwise wander. Simpler than {@code CortexZombieGoalPlanner} — no emergency branch here — since the point
 * of this example is the bow/melee split inside {@code HUNT_TARGET}, not planner sophistication.
 */
public final class CortexSkeletonGoalPlanner implements GoalPlanner<CortexSkeletonEntity, CortexSkeletonGoal> {

    private static final int INVESTIGATE_MAX_AGE_TICKS = 100;

    private static final int MIN_COMMIT_TICKS = 20;

    private static final int MAX_COMMIT_TICKS = 200;

    @Override
    public PlannedGoal<CortexSkeletonEntity, CortexSkeletonGoal> chooseGoal(
        CortexSkeletonEntity agent,
        Blackboard blackboard,
        CooldownTracker cooldowns
    ) {
        var currentTick = (int) agent.level.getGameTime();

        var failureCooldowns = GoalFailureCooldowns.<CortexSkeletonGoal>getOrCreate(blackboard);
        failureCooldowns.evictExpired(currentTick);

        @SuppressWarnings("unchecked")
        var feedback = (PlanFeedback<CortexSkeletonGoal>) blackboard.get(CommonBlackboardKeys.LAST_PLAN_FEEDBACK);
        if (
            feedback != null && feedback.isFresh(currentTick)
                && (feedback.reason() == PlanFailureReason.FAILED_STUCK
                    || feedback.reason() == PlanFailureReason.FAILED_NO_PATH)
        ) {
            failureCooldowns.recordFailure(CortexSkeletonGoal.HUNT_TARGET, currentTick);
        }

        var target = blackboard.get(CommonBlackboardKeys.TARGET);
        if (target != null && target.isAlive()) {
            var score = 50f - failureCooldowns.getPenalty(CortexSkeletonGoal.HUNT_TARGET, currentTick);
            return PlannedGoal.of(
                CortexSkeletonGoal.HUNT_TARGET,
                score,
                currentTick,
                MIN_COMMIT_TICKS,
                MAX_COMMIT_TICKS,
                target,
                null,
                GoalUrgency.NORMAL,
                true,
                "Live target acquired"
            );
        }

        var lastSeenPos = blackboard.get(CommonBlackboardKeys.LAST_SEEN_POS);
        var lastSeenTick = blackboard.get(CommonBlackboardKeys.LAST_SEEN_TICK);
        if (lastSeenPos != null && lastSeenTick != null && currentTick - lastSeenTick <= INVESTIGATE_MAX_AGE_TICKS) {
            return PlannedGoal.of(
                CortexSkeletonGoal.INVESTIGATE,
                30f,
                currentTick,
                MIN_COMMIT_TICKS,
                MAX_COMMIT_TICKS,
                null,
                lastSeenPos,
                GoalUrgency.NORMAL,
                true,
                "Lost sight of a target recently"
            );
        }

        return PlannedGoal.of(
            CortexSkeletonGoal.WANDER,
            10f,
            currentTick,
            MIN_COMMIT_TICKS,
            MAX_COMMIT_TICKS,
            null,
            null,
            GoalUrgency.LOW,
            true,
            "Nothing better to do"
        );
    }
}
