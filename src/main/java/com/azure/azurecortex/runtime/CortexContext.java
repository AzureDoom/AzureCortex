package com.azure.azurecortex.runtime;

import net.minecraft.entity.EntityLiving;

import java.util.Objects;

import com.azure.azurecortex.api.blackboard.Blackboard;

/**
 * A read-only, per-tick bundle of the state a {@link CortexRuntime} exposes about the agent it drives: the agent
 * itself, its {@link Blackboard}, its {@link CooldownTracker}, and the current game tick.
 * <p>
 * The core {@link com.azure.azurecortex.api.action.Action}/{@link com.azure.azurecortex.api.behavior.BehaviorNode}
 * contracts intentionally take {@code (agent, blackboard, cooldowns)} as three separate parameters rather than one
 * context object, matching the framework's origin and keeping the most common call sites free of an extra allocation
 * and field-access indirection. {@link CortexContext} exists as a convenience for code that wants to pass "everything
 * about this tick" around as a single value instead — diagnostics, debug commands, sensors, and helper utilities that
 * don't need to conform to the {@code Action}/{@code BehaviorNode} interfaces.
 */
public final class CortexContext {

    private final EntityLiving mob;

    private final Blackboard blackboard;

    private final CooldownTracker cooldowns;

    private final long currentTick;

    /**
     * @param mob         the agent this context describes
     * @param blackboard  the agent's blackboard
     * @param cooldowns   the agent's cooldown tracker
     * @param currentTick the game tick this context was captured on
     */
    public CortexContext(EntityLiving mob, Blackboard blackboard, CooldownTracker cooldowns, long currentTick) {
        this.mob = mob;
        this.blackboard = blackboard;
        this.cooldowns = cooldowns;
        this.currentTick = currentTick;
    }

    /**
     * Captures a {@link CortexContext} for {@code mob} using its current world's total game time.
     *
     * @param mob        the agent to capture context for
     * @param blackboard the agent's blackboard
     * @param cooldowns  the agent's cooldown tracker
     * @return a new context snapshot
     */
    public static CortexContext of(EntityLiving mob, Blackboard blackboard, CooldownTracker cooldowns) {
        return new CortexContext(mob, blackboard, cooldowns, mob.world.getTotalWorldTime());
    }

    public EntityLiving mob() {
        return mob;
    }

    public Blackboard blackboard() {
        return blackboard;
    }

    public CooldownTracker cooldowns() {
        return cooldowns;
    }

    public long currentTick() {
        return currentTick;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CortexContext))
            return false;
        CortexContext other = (CortexContext) o;
        return currentTick == other.currentTick && Objects.equals(mob, other.mob)
            && Objects.equals(blackboard, other.blackboard) && Objects.equals(cooldowns, other.cooldowns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mob, blackboard, cooldowns, currentTick);
    }

    @Override
    public String toString() {
        return "CortexContext[mob=" + mob + ", currentTick=" + currentTick + "]";
    }
}
