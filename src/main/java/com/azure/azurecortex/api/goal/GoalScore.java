package com.azure.azurecortex.api.goal;

import java.util.Objects;

/**
 * A single scored candidate produced while a {@link com.azure.azurecortex.goap.GoalPlanner} evaluates which goal an
 * agent should commit to next.
 * <p>
 * Planners are free to build and compare these however they like internally (there's no required scoring algorithm);
 * this class exists so implementations share a common, self-describing shape for "this goal, worth this much" rather
 * than each planner inventing its own pair/tuple.
 *
 * @param <G> the mod-defined goal-type enum
 */
public final class GoalScore<G> {

    private final G goal;

    private final float score;

    /**
     * @param goal  the candidate goal type
     * @param score the computed desirability of this goal — higher is more desirable; scale is planner-defined
     */
    public GoalScore(G goal, float score) {
        this.goal = goal;
        this.score = score;
    }

    public G goal() {
        return goal;
    }

    public float score() {
        return score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof GoalScore))
            return false;
        GoalScore<?> other = (GoalScore<?>) o;
        return Float.compare(score, other.score) == 0 && Objects.equals(goal, other.goal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(goal, score);
    }

    @Override
    public String toString() {
        return "GoalScore[goal=" + goal + ", score=" + score + "]";
    }
}
