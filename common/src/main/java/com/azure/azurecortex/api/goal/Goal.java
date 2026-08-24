package com.azure.azurecortex.api.goal;

import com.azure.azurecortex.goap.GoalPlanner;
import com.azure.azurecortex.goap.PlannedGoal;

/**
 * Marker interface a mod's own goal-type enum should implement so it can be used as the {@code G} type parameter
 * throughout the GOAP layer ({@link GoalPlanner}, {@link PlannedGoal}, and friends).
 * <p>
 * AzureCortex deliberately does not ship a fixed set of goal types (Ovomorphosis's {@code HUNT_TARGET},
 * {@code EXPAND_HIVE}, and so on are entirely specific to that mod's creatures). Instead, each consuming mod declares
 * its own {@code enum} — typically implementing {@link Goal} plus {@code Enum<G>} — listing the goals its agents can
 * pursue:
 *
 * <pre>{@code
 * public enum MyGoalType implements Goal {
 *     NONE,
 *     SURVIVE,
 *     WANDER,
 *     HUNT_TARGET,
 *     // ...
 * }
 * }</pre>
 *
 * Implementing this interface is optional — nothing in the framework requires it structurally, since {@code G} is an
 * unbounded type parameter — but it documents intent and gives IDE tooling an easy way to find every goal-type enum in
 * a project.
 */
public interface Goal {

    /**
     * Returns {@code true} if this goal represents "no goal selected" — the idle/fallback state a planner returns
     * before any real goal has been chosen. Mods with a {@code NONE}-style enum constant should override this to return
     * {@code true} only for that constant.
     *
     * @return {@code true} if this is the "no goal" sentinel value
     */
    default boolean isNone() {
        return false;
    }
}
