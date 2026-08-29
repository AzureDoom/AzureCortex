package com.azure.azurecortex.example.spider;

import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.api.goal.GoalUrgency;
import com.azure.azurecortex.goap.GoalFailureCooldowns;
import com.azure.azurecortex.goap.GoalPlanner;
import com.azure.azurecortex.goap.PlanFailureReason;
import com.azure.azurecortex.goap.PlanFeedback;
import com.azure.azurecortex.goap.PlannedGoal;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * Example {@link GoalPlanner} for {@link CortexSpiderEntity}: hunt a live target, otherwise investigate a recent
 * sighting, otherwise wander. Intentionally identical in shape to
 * {@code com.azure.azurecortex.example.skeleton.CortexSkeletonGoalPlanner} — wall-crawling is a navigation-layer
 * capability, not a planning-layer one, so this planner needs no special-casing for it at all.
 */
public final class CortexSpiderGoalPlanner implements GoalPlanner<CortexSpiderEntity, CortexSpiderGoal> {

    private static final int INVESTIGATE_MAX_AGE_TICKS = 100;

    private static final int MIN_COMMIT_TICKS = 20;

    private static final int MAX_COMMIT_TICKS = 200;

    @Override
    public PlannedGoal<CortexSpiderEntity, CortexSpiderGoal> chooseGoal(
        CortexSpiderEntity agent,
        Blackboard blackboard,
        CooldownTracker cooldowns
    ) {
        var currentTick = (int) agent.level().getGameTime();

        var failureCooldowns = GoalFailureCooldowns.<CortexSpiderGoal>getOrCreate(blackboard);
        failureCooldowns.evictExpired(currentTick);

        @SuppressWarnings("unchecked")
        var feedback = (PlanFeedback<CortexSpiderGoal>) blackboard.get(CommonBlackboardKeys.LAST_PLAN_FEEDBACK);
        if (
            feedback != null && feedback.isFresh(currentTick)
                && (feedback.reason() == PlanFailureReason.FAILED_STUCK
                    || feedback.reason() == PlanFailureReason.FAILED_NO_PATH)
        ) {
            failureCooldowns.recordFailure(CortexSpiderGoal.HUNT_TARGET, currentTick);
        }

        var target = blackboard.get(CommonBlackboardKeys.TARGET);
        if (target != null && target.isAlive()) {
            var score = 50f - failureCooldowns.getPenalty(CortexSpiderGoal.HUNT_TARGET, currentTick);
            return PlannedGoal.of(
                CortexSpiderGoal.HUNT_TARGET,
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
                CortexSpiderGoal.INVESTIGATE,
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
            CortexSpiderGoal.WANDER,
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
