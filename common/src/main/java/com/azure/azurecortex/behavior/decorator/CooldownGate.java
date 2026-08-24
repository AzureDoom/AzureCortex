package com.azure.azurecortex.behavior.decorator;

import com.azure.azurecortex.api.behavior.BehaviorNode;
import com.azure.azurecortex.api.behavior.BehaviorResult;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * A decorator {@link BehaviorNode} that only consults {@code child} while a given cooldown key is ready, returning
 * {@link BehaviorResult#none()} otherwise.
 * <p>
 * Use this to keep an expensive or noisy branch (a periodic scan, a rarely-eligible attack) from being re-evaluated
 * every single tick.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public final class CooldownGate<E, G> implements BehaviorNode<E, G> {

    private final String cooldownKey;

    private final BehaviorNode<E, G> child;

    public CooldownGate(String cooldownKey, BehaviorNode<E, G> child) {
        this.cooldownKey = cooldownKey;
        this.child = child;
    }

    @Override
    public BehaviorResult<E, G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        if (cooldowns.isOnCooldown(cooldownKey))
            return BehaviorResult.none();
        return child.tick(agent, blackboard, cooldowns);
    }
}
