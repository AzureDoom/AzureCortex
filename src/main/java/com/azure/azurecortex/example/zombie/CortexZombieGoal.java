package com.azure.azurecortex.example.zombie;

import com.azure.azurecortex.api.goal.Goal;

/**
 * The goal types {@link CortexZombieEntity} can commit to. See {@link CortexZombieGoalPlanner} for the scoring logic
 * and {@link CortexZombieTree} for how each goal type maps to an actual behavior-tree branch.
 */
public enum CortexZombieGoal implements Goal {

    /** Nothing chosen yet — the planner's initial/fallback state before its first real evaluation. */
    NONE,

    /** No target and nothing else to do; wander aimlessly. */
    WANDER,

    /** Lost sight of a target recently; walk toward an extrapolated search point. */
    INVESTIGATE,

    /**
     * A live target is being pursued; see {@link com.azure.azurecortex.example.HuntTargetNode} for the chase/attack
     * split.
     */
    HUNT_TARGET,

    /** Critically wounded and carrying a golden apple; eat it before doing anything else. */
    EAT_TO_HEAL;

    @Override
    public boolean isNone() {
        return this == NONE;
    }
}
