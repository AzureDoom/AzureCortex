package com.azure.azurecortex.behavior.composite;

import java.util.List;

import com.azure.azurecortex.api.behavior.BehaviorNode;
import com.azure.azurecortex.api.behavior.BehaviorResult;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * A composite {@link BehaviorNode} that evaluates every child each tick and returns the single highest-priority result
 * among the ones that produced an action.
 * <p>
 * Unlike a "first match wins" selector, every child is consulted every tick — this is what lets an
 * {@link com.azure.azurecortex.runtime.InterruptCategory#EMERGENCY} branch declared anywhere in the list win out over
 * whatever else the tree would otherwise pick, regardless of where it sits in {@code children}. Order only matters as a
 * tie-break between equal priorities (first-listed wins).
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
public final class PrioritySelector<E, G> implements BehaviorNode<E, G> {

    private final List<BehaviorNode<E, G>> children;

    public PrioritySelector(List<BehaviorNode<E, G>> children) {
        this.children = children;
    }

    @SafeVarargs
    public static <E, G> PrioritySelector<E, G> of(BehaviorNode<E, G>... children) {
        return new PrioritySelector<>(List.of(children));
    }

    @Override
    public BehaviorResult<E, G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        BehaviorResult<E, G> best = null;

        for (var child : children) {
            var result = child.tick(agent, blackboard, cooldowns);
            if (!result.success())
                continue;
            if (best == null || result.priority() > best.priority()) {
                best = result;
            }
        }

        return best != null ? best : BehaviorResult.none();
    }
}
