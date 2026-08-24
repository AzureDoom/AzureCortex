package com.azure.azurecortex.api.behavior;

import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.runtime.CooldownTracker;
import com.azure.azurecortex.runtime.CortexRuntime;

/**
 * A single node in the agent's behavior tree, evaluated every tick by {@link CortexRuntime}.
 * <p>
 * Nodes are composable: a node may be a leaf that wraps an {@link com.azure.azurecortex.api.action.Action}, or an
 * interior node (selector, sequence, priority queue — see {@code com.azure.azurecortex.behavior}) that delegates to
 * child nodes. The node returns a {@link BehaviorResult} that either carries the winning action or indicates no action
 * was chosen.
 *
 * @param <E> the agent type this node operates on
 * @param <G> the mod-defined goal-type enum used for GOAP feedback attribution
 */
@FunctionalInterface
public interface BehaviorNode<E, G> {

    /**
     * Evaluates this node for the given agent and returns the action the node wants to run.
     *
     * @param agent      the agent being evaluated
     * @param blackboard the agent's shared AI state store
     * @param cooldowns  the agent's cooldown tracker
     * @return a {@link BehaviorResult} containing the selected action, or {@link BehaviorResult#none()} if no action
     *         was chosen
     */
    BehaviorResult<E, G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns);
}
