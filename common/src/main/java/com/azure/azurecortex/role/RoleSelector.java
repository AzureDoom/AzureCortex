package com.azure.azurecortex.role;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.azure.azurecortex.api.role.AgentRole;

/**
 * Assigns soft roles across a group of agents, holding each assignment for a minimum duration before it can change.
 * <p>
 * AzureCortex ships no opinion about how roles should be scored or distributed among a group — that's inherently
 * mod-specific (a hive might want exactly one "guard" near its core, a squad might want a fixed ratio of roles). This
 * class only handles the mechanical bookkeeping: tracking who currently holds which role and honoring a minimum hold
 * duration so roles don't flicker every tick. Supply your own {@code scorer} function to {@link #assign} to decide who
 * gets which role.
 *
 * @param <E> the agent type
 * @param <R> the mod-defined role enum
 */
@SuppressWarnings("unused")
public final class RoleSelector<E, R extends AgentRole> {

    private final Map<E, RoleAssignment<R>> assignments = new HashMap<>();

    private final long minHoldDuration;

    /**
     * @param minHoldDuration how many ticks an assignment must be held before it can be reassigned
     */
    public RoleSelector(long minHoldDuration) {
        this.minHoldDuration = minHoldDuration;
    }

    /**
     * Returns the currently assigned role for {@code agent}, or {@code null} if none has been assigned yet.
     */
    public R currentRole(E agent) {
        var assignment = assignments.get(agent);
        return assignment != null ? assignment.role() : null;
    }

    /**
     * Assigns {@code role} to {@code agent} at {@code currentTick}, unconditionally overwriting any existing
     * assignment. Use {@link #tryAssign} if the minimum hold duration should be respected.
     */
    public void assign(E agent, R role, long currentTick) {
        assignments.put(agent, new RoleAssignment<>(role, currentTick));
    }

    /**
     * Assigns {@code role} to {@code agent} at {@code currentTick} only if the agent has no current assignment or its
     * current assignment's hold duration has elapsed.
     *
     * @return {@code true} if the assignment was made, {@code false} if it was skipped due to the hold duration
     */
    public boolean tryAssign(E agent, R role, long currentTick) {
        var existing = assignments.get(agent);
        if (existing != null && !existing.canReassign(currentTick, minHoldDuration)) {
            return false;
        }
        assign(agent, role, currentTick);
        return true;
    }

    /** Removes any role assignment for {@code agent}. */
    public void clear(E agent) {
        assignments.remove(agent);
    }

    /**
     * Re-scores and reassigns roles for every agent in {@code agents} whose current assignment is eligible for
     * reassignment (or has none yet), using {@code scorer} to pick each agent's best role.
     *
     * @param agents      the group of agents to (re)assign roles for
     * @param scorer      computes the best role for a given agent; may consult the rest of the group however it likes
     * @param currentTick the current game tick
     */
    public void reassignEligible(Collection<E> agents, Function<E, R> scorer, long currentTick) {
        for (var agent : agents) {
            var existing = assignments.get(agent);
            if (existing != null && !existing.canReassign(currentTick, minHoldDuration)) {
                continue;
            }
            var role = scorer.apply(agent);
            if (role != null) {
                assign(agent, role, currentTick);
            }
        }
    }
}
