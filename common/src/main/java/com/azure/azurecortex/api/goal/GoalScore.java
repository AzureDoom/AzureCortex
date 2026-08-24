package com.azure.azurecortex.api.goal;

/**
 * A single scored candidate produced while a {@link com.azure.azurecortex.goap.GoalPlanner} evaluates which goal an
 * agent should commit to next.
 * <p>
 * Planners are free to build and compare these however they like internally (there's no required scoring algorithm);
 * this record exists so implementations share a common, self-describing shape for "this goal, worth this much" rather
 * than each planner inventing its own pair/tuple.
 *
 * @param <G>   the mod-defined goal-type enum
 * @param goal  the candidate goal type
 * @param score the computed desirability of this goal — higher is more desirable; scale is planner-defined
 */
public record GoalScore<G>(
    G goal,
    float score
) {}
