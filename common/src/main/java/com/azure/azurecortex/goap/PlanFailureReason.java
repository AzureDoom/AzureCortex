package com.azure.azurecortex.goap;

import com.azure.azurecortex.runtime.CortexRuntime;

/**
 * Structured failure codes that actions report back to the GOAP planner.
 * <p>
 * Actions never write these to the blackboard themselves. Instead, {@code Action.tick} returns
 * {@code ActionOutcome.blocked(reason, ...)} or {@code ActionOutcome.failed(reason, ...)}, and {@link CortexRuntime}
 * writes the corresponding {@link PlanFeedback} centrally from that return value. The planner reads that feedback on
 * the next planning interval.
 *
 * <pre>{@code
 * // Inside any Action.tick():
 * if (path == null) {
 *     return ActionOutcome.failed(PlanFailureReason.FAILED_NO_PATH, mob.blockPosition());
 * }
 * }</pre>
 * <p>
 * These codes are deliberately domain-agnostic (pathing, cooldowns, obstruction, danger, precondition) so they apply
 * regardless of what a mod's actual goals are named. A mod's {@code GoalPlanner} decides what to do with a given
 * reason, e.g. raising the score of whatever goal type represents "investigate" or "retreat" for that mod.
 */
public enum PlanFailureReason {

    /** No failure recorded — default state. */
    NONE,

    /**
     * Pathfinder returned null or an empty path. Typical planner response: raise the score of an obstacle-clearing or
     * alternate-approach goal.
     */
    FAILED_NO_PATH,

    /**
     * Navigation was succeeding but the agent became stuck (no meaningful displacement over several ticks despite an
     * active path). Typical planner response: same as {@link #FAILED_NO_PATH} but also consider a flanking route.
     */
    FAILED_STUCK,

    /**
     * The blackboard target became null, died, or left sensor range mid-action. Typical planner response: raise the
     * score of an investigate-last-known-position goal.
     */
    FAILED_TARGET_LOST,

    /**
     * A required piece of mod-specific infrastructure was not present (the generalization of Ovomorphosis's "no web
     * cross nearby"). Typical planner response: raise the score of whatever goal builds/finds that infrastructure.
     */
    FAILED_MISSING_INFRASTRUCTURE,

    /**
     * Ambient conditions at the destination or along the route made it unsuitable (the generalization of Ovomorphosis's
     * "too bright for a darkness-seeking mob"). Typical planner response: raise the score of a goal that changes those
     * conditions or seeks a different location.
     */
    FAILED_UNSUITABLE_CONDITIONS,

    /**
     * Physical obstacle (non-breakable block, entity, etc.) prevents the action from completing. Typical planner
     * response: raise the score of a break-obstacle or detour goal.
     */
    FAILED_BLOCKED,

    /**
     * A danger stimulus (fire, retaliation, overwhelming force) was detected and continuing the action would be unsafe.
     * Typical planner response: raise the score of a retreat/hide goal.
     */
    FAILED_DANGER,

    /**
     * The action's own cooldown or a shared cooldown prevented execution. Typical planner response: suppress the goal
     * score for that goal type for the remainder of the cooldown window; prefer an alternative goal.
     */
    FAILED_COOLDOWN,

    /**
     * The action requires a specific precondition on the blackboard that is not satisfied (generic catch-all for
     * prerequisite failures not covered above).
     */
    FAILED_PRECONDITION,

    /**
     * The path to the target is obstructed by a block the agent cannot break through. Typical planner response: switch
     * to an investigate/reposition/wait goal instead of retrying the same approach.
     */
    FAILED_OBSTACLE_UNBREAKABLE,

    /**
     * A placement/construction action found no valid candidate position at all (every scanned position was occupied,
     * unreplaceable, or unsuitable) — as distinct from {@link #FAILED_UNSUITABLE_CONDITIONS}, which means candidates
     * might exist but ambient conditions ruled them out. Typical planner response: suppress that goal for a cooldown
     * window and prefer wandering/repositioning before recommitting to the same spot.
     */
    FAILED_NO_VALID_PLACEMENT,
}
