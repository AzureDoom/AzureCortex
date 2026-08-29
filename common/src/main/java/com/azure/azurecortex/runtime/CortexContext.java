package com.azure.azurecortex.runtime;

import net.minecraft.world.entity.Mob;

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
 *
 * @param mob         the agent this context describes
 * @param blackboard  the agent's blackboard
 * @param cooldowns   the agent's cooldown tracker
 * @param currentTick the game tick this context was captured on
 */
public record CortexContext(
    Mob mob,
    Blackboard blackboard,
    CooldownTracker cooldowns,
    long currentTick
) {

    /**
     * Captures a {@link CortexContext} for {@code mob} using its current level's game time.
     *
     * @param mob        the agent to capture context for
     * @param blackboard the agent's blackboard
     * @param cooldowns  the agent's cooldown tracker
     * @return a new context snapshot
     */
    public static CortexContext of(Mob mob, Blackboard blackboard, CooldownTracker cooldowns) {
        return new CortexContext(mob, blackboard, cooldowns, mob.level.getGameTime());
    }
}
