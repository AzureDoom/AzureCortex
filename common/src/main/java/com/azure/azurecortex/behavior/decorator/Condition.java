package com.azure.azurecortex.behavior.decorator;

import com.azure.azurecortex.api.behavior.BehaviorNode;
import com.azure.azurecortex.api.behavior.BehaviorResult;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * A decorator {@link BehaviorNode} that only consults {@code child} while {@code predicate} holds, returning
 * {@link BehaviorResult#none()} otherwise.
 * <p>
 * The general-purpose gate for "only offer this branch when X is true" — pair with
 * {@link com.azure.azurecortex.behavior.leaf.ActionNode}'s own precondition when the condition is local to a single
 * leaf, or wrap a whole subtree with this when the condition should gate several nodes at once.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public final class Condition<E, G> implements BehaviorNode<E, G> {

    /** The gating predicate for a {@link Condition} node. */
    @FunctionalInterface
    public interface Predicate<E> {

        boolean test(E agent, Blackboard blackboard, CooldownTracker cooldowns);
    }

    private final Predicate<E> predicate;

    private final BehaviorNode<E, G> child;

    public Condition(Predicate<E> predicate, BehaviorNode<E, G> child) {
        this.predicate = predicate;
        this.child = child;
    }

    @Override
    public BehaviorResult<E, G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        if (!predicate.test(agent, blackboard, cooldowns))
            return BehaviorResult.none();
        return child.tick(agent, blackboard, cooldowns);
    }
}
