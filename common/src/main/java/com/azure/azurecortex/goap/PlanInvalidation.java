package com.azure.azurecortex.goap;

import net.minecraft.world.entity.Mob;

import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;

/**
 * Generic, goal-agnostic replan trigger: compares the {@link WorldStateSnapshot} taken when the current plan was
 * committed against the live world, and reports {@code true} the moment they diverge meaningfully — regardless of
 * whether the currently running action has itself noticed anything wrong.
 * <h3>Where this sits relative to existing feedback</h3>
 *
 * <pre>
 * Action.tick() → ActionOutcome.Blocked/Failed → CortexRuntime → PlanFeedback     (reactive, per-action)
 * PlanInvalidation.isInvalidated(...) → GoalExecutor.shouldReplan                 (proactive, generic)
 * </pre>
 *
 * {@code PlanFeedback} answers "did the thing I was doing just fail?" {@link PlanInvalidation} answers "does the world
 * still look like it did when I decided to do this?" — a strictly cheaper, upstream question that doesn't require any
 * action to be running, let alone to have failed, in order to fire. Both feed the same
 * {@link GoalExecutor#shouldReplan} gate; this class is the "world-state invalidation above your existing action
 * feedback" half of it.
 * <p>
 * This intentionally only ever <em>unlocks replanning</em> (bypasses the min-commit lock) — it never forces the newly
 * chosen goal to preempt whatever action is currently running. That is still governed entirely by
 * {@code CortexRuntime}/{@code InterruptController} and each action's {@code InterruptCategory}, exactly as before.
 * Invalidating a plan just means the planner is allowed to reconsider, not that it is forced to act immediately.
 */
public final class PlanInvalidation {

    private PlanInvalidation() {}

    /**
     * Returns {@code true} if the world-state facts on record for the currently active plan no longer match reality.
     *
     * @param mob        the agent to probe
     * @param blackboard the agent's blackboard
     * @return {@code true} if a generic replan should be forced regardless of the min-commit window
     */
    public static boolean isInvalidated(Mob mob, Blackboard blackboard) {
        var recorded = blackboard.get(CommonBlackboardKeys.PLAN_WORLD_STATE);
        if (recorded == null)
            return false;

        var current = WorldStateSnapshot.capture(mob, blackboard);

        if (recorded.targetId() != current.targetId())
            return true;

        if (current.healthBucket().ordinal() < recorded.healthBucket().ordinal())
            return true;

        if (current.inDarkness() != recorded.inDarkness())
            return true;

        if (recorded.targetId() != -1) {
            var bucketDelta = Math.abs(current.distanceBucket().ordinal() - recorded.distanceBucket().ordinal());
            return bucketDelta >= 2;
        }

        return false;
    }
}
