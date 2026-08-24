package com.azure.azurecortex.behavior.composite;

import java.util.List;

import com.azure.azurecortex.api.behavior.BehaviorNode;
import com.azure.azurecortex.api.behavior.BehaviorResult;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * A composite {@link BehaviorNode} that tries children in order and returns the first one that produces an action.
 * <p>
 * Unlike {@link PrioritySelector}, which consults every child and picks the best, {@link Sequence} short-circuits at
 * the first success — useful for a fixed fallback chain ("try the specific approach, then the general one") where
 * priority comparison across every branch every tick isn't needed.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public final class Sequence<E, G> implements BehaviorNode<E, G> {

    private final List<BehaviorNode<E, G>> children;

    public Sequence(List<BehaviorNode<E, G>> children) {
        this.children = children;
    }

    @SafeVarargs
    public static <E, G> Sequence<E, G> of(BehaviorNode<E, G>... children) {
        return new Sequence<>(List.of(children));
    }

    @Override
    public BehaviorResult<E, G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        for (var child : children) {
            var result = child.tick(agent, blackboard, cooldowns);
            if (result.success())
                return result;
        }
        return BehaviorResult.none();
    }
}
