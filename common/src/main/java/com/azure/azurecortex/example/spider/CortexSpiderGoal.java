package com.azure.azurecortex.example.spider;

import com.azure.azurecortex.api.goal.Goal;

/**
 * The goal types {@link CortexSpiderEntity} can commit to. Deliberately the same shape as
 * {@code com.azure.azurecortex.example.skeleton.CortexSkeletonGoal} — see {@link CortexSpiderTree} for where the
 * wall-crawling behavior actually lives (the pathfinder/action choice, not the goal set).
 */
public enum CortexSpiderGoal implements Goal {

    /** Nothing chosen yet — the planner's initial/fallback state before its first real evaluation. */
    NONE,

    /** No target and nothing else to do; wander aimlessly. */
    WANDER,

    /** Lost sight of a target recently; walk toward an extrapolated search point. */
    INVESTIGATE,

    /** A live target is being pursued, climbing over walls and across ceilings as needed to close the distance. */
    HUNT_TARGET;

    @Override
    public boolean isNone() {
        return this == NONE;
    }
}
