package com.azure.azurecortex.runtime;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.behavior.BehaviorResult;

/**
 * Resolves whether a candidate {@link BehaviorResult} is allowed to preempt the currently running {@link Action}, based
 * purely on {@link InterruptCategory} and priority.
 * <p>
 * Extracted out of {@link CortexRuntime} so the preemption policy can be unit-tested and reused (e.g. by a debug
 * overlay that wants to explain why a candidate action did or didn't take over) independent of the runtime's tick loop.
 */
public final class InterruptController {

    private InterruptController() {}

    /**
     * Decides whether {@code candidate} is allowed to preempt {@code current}, based on {@link InterruptCategory}
     * resolution rules layered on top of a plain priority comparison.
     *
     * @param current   the currently running action; must not be {@code null}
     * @param candidate the behavior-tree result being considered as a replacement
     * @param <E>       the agent type
     * @param <G>       the mod-defined goal-type enum
     * @return {@code true} if {@code candidate} should replace {@code current}
     */
    public static <E, G> boolean canPreempt(Action<E, G> current, BehaviorResult<E, G> candidate) {
        if (candidate.action() == null || candidate.action() == current)
            return false;

        var candidateCategory = candidate.effectiveCategory();

        return switch (current.interruptCategory()) {
            case LOCKED -> candidateCategory == InterruptCategory.EMERGENCY;
            case NORMAL -> candidateCategory == InterruptCategory.EMERGENCY
                || candidate.priority() > current.priority();
            case EMERGENCY -> candidateCategory == InterruptCategory.EMERGENCY
                && candidate.priority() > current.priority();
        };
    }
}
