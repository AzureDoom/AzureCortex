package com.azure.azurecortex.behavior.leaf;

import java.util.function.Predicate;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.behavior.BehaviorNode;
import com.azure.azurecortex.api.behavior.BehaviorResult;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * A leaf {@link BehaviorNode} that offers a single {@link Action} at a fixed priority, gated by an optional
 * precondition.
 * <p>
 * This is the simplest possible tree node: "if {@code precondition} holds, run {@code action} at {@code priority};
 * otherwise offer nothing." Combine several of these under a
 * {@link com.azure.azurecortex.behavior.composite.PrioritySelector} to build a full tree.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public final class ActionNode<E, G> implements BehaviorNode<E, G> {

    /** A leaf node's precondition check. */
    @FunctionalInterface
    public interface Precondition<E> {

        boolean test(E agent, Blackboard blackboard, CooldownTracker cooldowns);
    }

    private final Action<E, G> action;

    private final int priority;

    private final Precondition<E> precondition;

    public ActionNode(Action<E, G> action, int priority, Precondition<E> precondition) {
        this.action = action;
        this.priority = priority;
        this.precondition = precondition;
    }

    /** Convenience constructor with no precondition — always offers {@code action}. */
    public ActionNode(Action<E, G> action, int priority) {
        this(action, priority, (agent, blackboard, cooldowns) -> true);
    }

    /**
     * Convenience constructor taking a blackboard/cooldown-agnostic predicate over just the agent.
     */
    public static <E, G> ActionNode<E, G> when(Predicate<E> precondition, Action<E, G> action, int priority) {
        return new ActionNode<>(action, priority, (agent, blackboard, cooldowns) -> precondition.test(agent));
    }

    @Override
    public BehaviorResult<E, G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        if (!precondition.test(agent, blackboard, cooldowns))
            return BehaviorResult.none();
        return BehaviorResult.run(action, priority);
    }
}
