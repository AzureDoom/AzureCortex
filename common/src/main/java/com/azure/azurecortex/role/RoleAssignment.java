package com.azure.azurecortex.role;

import com.azure.azurecortex.api.role.AgentRole;

/**
 * A soft role assigned to an agent for a while, plus the tick it was assigned.
 * <p>
 * Roles are advisory: they're intended to bias GOAP goal scoring (a "guard" role prefers defend/patrol goals, a
 * "hunter" role prefers pursuit goals) rather than to gate what an agent is mechanically allowed to do. Store one on an
 * agent's blackboard under a mod-defined key.
 *
 * @param <R>          the mod-defined role enum, typically implementing {@link AgentRole}
 * @param role         the assigned role
 * @param assignedTick the game tick this role was assigned, for cooldown/re-evaluation purposes
 */
public record RoleAssignment<R extends AgentRole>(
    R role,
    long assignedTick
) {

    /**
     * @param currentTick     the tick to compare against
     * @param minHoldDuration how many ticks a role must be held before {@link RoleSelector} is allowed to reassign it
     * @return {@code true} if at least {@code minHoldDuration} ticks have passed since this role was assigned
     */
    public boolean canReassign(long currentTick, long minHoldDuration) {
        return (currentTick - assignedTick) >= minHoldDuration;
    }
}
