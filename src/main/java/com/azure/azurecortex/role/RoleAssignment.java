package com.azure.azurecortex.role;

import java.util.Objects;

import com.azure.azurecortex.api.role.AgentRole;

/**
 * A soft role assigned to an agent for a while, plus the tick it was assigned.
 * <p>
 * Roles are advisory: they're intended to bias GOAP goal scoring (a "guard" role prefers defend/patrol goals, a
 * "hunter" role prefers pursuit goals) rather than to gate what an agent is mechanically allowed to do. Store one on an
 * agent's blackboard under a mod-defined key.
 *
 * @param <R> the mod-defined role enum, typically implementing {@link AgentRole}
 */
@SuppressWarnings("unused")
public final class RoleAssignment<R extends AgentRole> {

    private final R role;

    private final long assignedTick;

    /**
     * @param role         the assigned role
     * @param assignedTick the game tick this role was assigned, for cooldown/re-evaluation purposes
     */
    public RoleAssignment(R role, long assignedTick) {
        this.role = role;
        this.assignedTick = assignedTick;
    }

    public R role() {
        return role;
    }

    public long assignedTick() {
        return assignedTick;
    }

    /**
     * @param currentTick     the tick to compare against
     * @param minHoldDuration how many ticks a role must be held before {@link RoleSelector} is allowed to reassign it
     * @return {@code true} if at least {@code minHoldDuration} ticks have passed since this role was assigned
     */
    public boolean canReassign(long currentTick, long minHoldDuration) {
        return (currentTick - assignedTick) >= minHoldDuration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof RoleAssignment))
            return false;
        RoleAssignment<?> other = (RoleAssignment<?>) o;
        return assignedTick == other.assignedTick && Objects.equals(role, other.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, assignedTick);
    }

    @Override
    public String toString() {
        return "RoleAssignment[role=" + role + ", assignedTick=" + assignedTick + "]";
    }
}
