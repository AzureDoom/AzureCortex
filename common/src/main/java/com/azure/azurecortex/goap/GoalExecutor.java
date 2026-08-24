package com.azure.azurecortex.goap;

import net.minecraft.world.entity.Mob;

import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.api.goal.GoalUrgency;

/**
 * Writes a committed {@link PlannedGoal} to the blackboard and provides the {@link #shouldReplan} gate that controls
 * when the planner is allowed to run.
 * <h3>Replan policy</h3>
 * <ol>
 * <li><b>Emergency override</b> — a new plan whose urgency is {@link GoalUrgency#EMERGENCY} always replaces the current
 * goal immediately, regardless of commit timers.</li>
 * <li><b>Min-commit lock</b> — the planner is suppressed until {@link PlannedGoal#canReplan(int)} returns {@code true}
 * (i.e. at least {@code minCommitTicks} have elapsed since the goal started). This prevents per-tick thrashing when two
 * goals score closely.</li>
 * <li><b>Max-commit expiry</b> — once {@link PlannedGoal#isExpired(int)} is true the planner is forced to run
 * regardless of other conditions, preventing a goal from running forever if the agent gets stuck in a state that never
 * self-terminates.</li>
 * <li><b>World-state invalidation</b> — {@link PlanInvalidation#isInvalidated} also forces a replan the moment the
 * facts that justified the current plan stop being true.</li>
 * <li><b>Normal replan</b> — once the min-commit window has passed the planner runs on its normal cadence (gated by
 * whatever replan cooldown the caller maintains, e.g. {@link CommonBlackboardKeys#GOAL_REPLAN}).</li>
 * </ol>
 */
public final class GoalExecutor {

    private GoalExecutor() {}

    /**
     * This is a generic, goal-agnostic check that sits <em>above</em> the existing {@code ActionOutcome}-driven
     * {@link PlanFeedback} loop — it does not require any action to be running or to have reported anything.
     *
     * @param blackboard       the agent's blackboard
     * @param currentTick      current game tick
     * @param candidateUrgency the urgency of the highest-priority candidate goal, if known; pass {@code null} to skip
     *                         the emergency-override check
     * @param mob              the agent being planned for, used to evaluate world-state invalidation
     * @return {@code true} if replanning should proceed
     */
    @SuppressWarnings("unchecked")
    public static <E extends Mob, G> boolean shouldReplan(
        Blackboard blackboard,
        int currentTick,
        GoalUrgency candidateUrgency,
        Mob mob
    ) {
        var active = (PlannedGoal<E, G>) blackboard.get(CommonBlackboardKeys.ACTIVE_GOAL);

        if (active == null)
            return true;

        if (active.isExpired(currentTick))
            return true;

        if (candidateUrgency == GoalUrgency.EMERGENCY)
            return true;

        if (PlanInvalidation.isInvalidated(mob, blackboard))
            return true;

        return active.canReplan(currentTick);
    }

    /**
     * Convenience overload that skips the emergency-urgency check. Use when the caller has not yet scored candidates
     * and cannot know the urgency.
     */
    public static <E extends Mob, G> boolean shouldReplan(Blackboard blackboard, int currentTick, Mob mob) {
        return GoalExecutor.<E, G>shouldReplan(blackboard, currentTick, null, mob);
    }

    /**
     * Writes {@code goal} to the blackboard as the active goal and clears stale feedback keys.
     *
     * @param mob        the agent whose blackboard is being updated
     * @param blackboard the blackboard
     * @param goal       the newly chosen goal
     */
    public static <E extends Mob, G> void apply(E mob, Blackboard blackboard, PlannedGoal<E, G> goal) {
        if (mob.isNoAi())
            return;
        blackboard.set(CommonBlackboardKeys.ACTIVE_GOAL, goal);
        blackboard.set(CommonBlackboardKeys.ACTIVE_GOAL_TYPE, goal.type());
        blackboard.set(CommonBlackboardKeys.LAST_GOAL_REASON, goal.reason());

        goal.target()
            .ifPresent(target -> blackboard.set(CommonBlackboardKeys.GOAL_TARGET, target));

        goal.destination()
            .ifPresent(pos -> blackboard.set(CommonBlackboardKeys.GOAL_DESTINATION, pos));

        blackboard.remove(CommonBlackboardKeys.LAST_PLAN_FEEDBACK);
        blackboard.remove(CommonBlackboardKeys.LAST_FAILURE_REASON);

        // Record what the world looked like at the moment this plan was committed, so PlanInvalidation can notice
        // when it stops looking that way — see WorldStateSnapshot/PlanInvalidation for why this exists.
        blackboard.set(CommonBlackboardKeys.PLAN_WORLD_STATE, WorldStateSnapshot.capture(mob, blackboard));
    }
}
