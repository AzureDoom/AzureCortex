package com.azure.azurecortex.example.skeleton;

import com.azure.azurecortex.api.goal.Goal;

/**
 * The goal types {@link CortexSkeletonEntity} can commit to. See {@link CortexSkeletonGoalPlanner} for the scoring
 * logic and {@link CortexSkeletonTree} for how each goal type maps to an actual behavior-tree branch.
 */
public enum CortexSkeletonGoal implements Goal {

    /** Nothing chosen yet — the planner's initial/fallback state before its first real evaluation. */
    NONE,

    /** No target and nothing else to do; wander aimlessly. */
    WANDER,

    /** Lost sight of a target recently; walk toward an extrapolated search point. */
    INVESTIGATE,

    /**
     * A live target is being pursued; see {@link com.azure.azurecortex.example.HuntTargetNode} for the split between
     * closing distance, drawing the bow, and falling back to melee at point-blank range.
     */
    HUNT_TARGET;

    @Override
    public boolean isNone() {
        return this == NONE;
    }
}
