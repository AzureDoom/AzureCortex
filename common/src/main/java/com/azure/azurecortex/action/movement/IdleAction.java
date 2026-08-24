package com.azure.azurecortex.action.movement;

import net.minecraft.world.entity.Mob;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.action.ActionOutcome;
import com.azure.azurecortex.api.action.ActionStatus;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * A minimal fallback action: stops the agent's navigator and reports {@link ActionOutcome#running()} forever until
 * preempted.
 * <p>
 * Every behavior tree needs some lowest-priority branch that's always eligible, so the tree never ends up with "nothing
 * selected" — this is that branch. It deliberately does nothing else; a mod that wants idle agents to look around, play
 * an animation, etc. should build its own richer idle action, but almost every tree still wants this at priority 0 as
 * the ultimate fallback.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public final class IdleAction<E extends Mob, G> implements Action<E, G> {

    @Override
    public void start(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        agent.getNavigation().stop();
    }

    @Override
    public ActionOutcome<G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        return ActionOutcome.running();
    }

    @Override
    public void stop(E agent, Blackboard blackboard, CooldownTracker cooldowns, ActionStatus reason) {}

    @Override
    public boolean isInterruptible() {
        return true;
    }

    @Override
    public int priority() {
        return 0;
    }
}
