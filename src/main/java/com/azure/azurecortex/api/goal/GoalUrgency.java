package com.azure.azurecortex.api.goal;

/**
 * How urgently a planned goal wants to preempt whatever the agent is currently committed to.
 * <p>
 * {@link #EMERGENCY} is the only level with special handling in the GOAP replan gate
 * ({@code GoalExecutor#shouldReplan}) — it always bypasses the min-commit lock.
 * {@link #LOW}/{@link #NORMAL}/{@link #HIGH} are informational levels a planner can use for its own scoring/logging;
 * the framework does not treat them differently from each other.
 */
public enum GoalUrgency {
    LOW,
    NORMAL,
    HIGH,
    EMERGENCY
}
