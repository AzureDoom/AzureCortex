package com.azure.azurecortex.example.zombie;

import net.minecraft.world.item.Items;

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
 * Example {@link GoalPlanner} for {@link CortexZombieEntity}: eat a golden apple when critically wounded, otherwise
 * hunt a live target, otherwise investigate a recent sighting, otherwise wander.
 * <p>
 * This is deliberately a simple, linear priority chain rather than a full weighted-scoring planner (compare
 * Ovomorphosis's actual xenomorph planner, which juggles a dozen goal types) — enough to show the moving parts
 * ({@link GoalFailureCooldowns}, {@link PlanFeedback}, {@link CommonBlackboardKeys#LAST_SEEN_POS}) without the scoring
 * logic itself being the point.
 */
public final class CortexZombieGoalPlanner implements GoalPlanner<CortexZombieEntity, CortexZombieGoal> {

    /** Health fraction at or below which a zombie carrying a golden apple stops to eat it. */
    static final float EAT_HEALTH_FRACTION = 0.4f;

    private static final int INVESTIGATE_MAX_AGE_TICKS = 100;

    private static final int MIN_COMMIT_TICKS = 20;

    private static final int MAX_COMMIT_TICKS = 200;

    @Override
    public PlannedGoal<CortexZombieEntity, CortexZombieGoal> chooseGoal(
        CortexZombieEntity agent,
        Blackboard blackboard,
        CooldownTracker cooldowns
    ) {
        var currentTick = (int) agent.level().getGameTime();

        var healthFraction = agent.getMaxHealth() > 0f ? agent.getHealth() / agent.getMaxHealth() : 1f;
        if (healthFraction <= EAT_HEALTH_FRACTION && agent.getOffhandItem().is(Items.GOLDEN_APPLE)) {
            return PlannedGoal.of(
                CortexZombieGoal.EAT_TO_HEAL,
                100f,
                currentTick,
                10,
                100,
                null,
                null,
                GoalUrgency.EMERGENCY,
                true,
                "Wounded below " + (int) (EAT_HEALTH_FRACTION * 100) + "% health and carrying a golden apple"
            );
        }

        var failureCooldowns = GoalFailureCooldowns.<CortexZombieGoal>getOrCreate(blackboard);
        failureCooldowns.evictExpired(currentTick);

        @SuppressWarnings("unchecked")
        var feedback = (PlanFeedback<CortexZombieGoal>) blackboard.get(CommonBlackboardKeys.LAST_PLAN_FEEDBACK);
        if (
            feedback != null && feedback.isFresh(currentTick)
                && (feedback.reason() == PlanFailureReason.FAILED_STUCK
                    || feedback.reason() == PlanFailureReason.FAILED_NO_PATH)
        ) {
            // Repeatedly failing to close the distance (stuck behind terrain, no route at all) suppresses HUNT_TARGET
            // for a while so the zombie doesn't immediately re-commit to the exact same failing plan next tick.
            failureCooldowns.recordFailure(CortexZombieGoal.HUNT_TARGET, currentTick);
        }

        var target = blackboard.get(CommonBlackboardKeys.TARGET);
        if (target != null && target.isAlive()) {
            var score = 50f - failureCooldowns.getPenalty(CortexZombieGoal.HUNT_TARGET, currentTick);
            return PlannedGoal.of(
                CortexZombieGoal.HUNT_TARGET,
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
                CortexZombieGoal.INVESTIGATE,
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
            CortexZombieGoal.WANDER,
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
