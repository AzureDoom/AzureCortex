package com.azure.azurecortex.goap;

import net.minecraft.world.entity.Mob;

import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * Strategy interface for choosing which goal an agent should commit to next.
 * <p>
 * AzureCortex ships no built-in scoring logic — every mod's notion of "what's a good goal right now" is inherently
 * domain-specific. Implement this per agent archetype, typically scoring a handful of
 * {@link com.azure.azurecortex.api.goal.GoalScore} candidates and returning the winner wrapped in a
 * {@link PlannedGoal}.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public interface GoalPlanner<E extends Mob, G> {

    PlannedGoal<E, G> chooseGoal(E agent, Blackboard blackboard, CooldownTracker cooldowns);
}
