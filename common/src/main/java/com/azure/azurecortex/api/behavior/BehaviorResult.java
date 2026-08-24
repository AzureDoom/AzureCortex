package com.azure.azurecortex.api.behavior;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.runtime.CortexRuntime;
import com.azure.azurecortex.runtime.InterruptCategory;

/**
 * The value returned by a {@link BehaviorNode} after each tick.
 * <p>
 * Wraps the winning {@link Action} (if any), its priority, and whether the node succeeded. {@link CortexRuntime} uses
 * the priority to decide whether to preempt the currently running action.
 *
 * @param <E>              the agent type this result targets
 * @param <G>              the mod-defined goal-type enum used for GOAP feedback attribution
 * @param action           the action the node wants to run, or {@code null} if no action was selected
 * @param priority         the numeric priority of the selected action; higher values preempt lower ones
 * @param success          {@code true} if the node produced a valid action
 * @param categoryOverride overrides the effective {@link InterruptCategory} for this candidate, or {@code null} to use
 *                         the action's own {@link Action#interruptCategory()}
 */
@SuppressWarnings("unused")
public record BehaviorResult<E, G>(
    Action<E, G> action,
    int priority,
    boolean success,
    InterruptCategory categoryOverride
) {

    /**
     * @return an empty result indicating that no action was selected
     */
    public static <E, G> BehaviorResult<E, G> none() {
        return new BehaviorResult<>(null, 0, false, null);
    }

    /**
     * Returns a successful result that requests the given {@code action} be started.
     * <p>
     * The result's effective {@link InterruptCategory} is inherited from {@link Action#interruptCategory()}. Use
     * {@link #runEmergency} when the tree itself has determined the situation is an emergency (e.g. critical health)
     * even though the action instance is an otherwise-ordinary one shared with non-emergency branches.
     *
     * @param action   the action to run
     * @param priority the priority of this action; higher values can preempt lower-priority actions
     */
    public static <E, G> BehaviorResult<E, G> run(Action<E, G> action, int priority) {
        return new BehaviorResult<>(action, priority, true, null);
    }

    /**
     * Returns a successful result that requests {@code action} be started with an {@link InterruptCategory#EMERGENCY}
     * override, regardless of what {@link Action#interruptCategory()} would otherwise report.
     * <p>
     * Use this when the tree has detected a genuinely critical situation (e.g. health below a critical threshold) that
     * should be able to preempt a {@link InterruptCategory#LOCKED} action, but is driving the agent with an action
     * instance that is also used for ordinary, non-emergency behavior (so permanently tagging the action class itself
     * as {@link InterruptCategory#EMERGENCY} would be wrong).
     *
     * @param action   the action to run
     * @param priority the priority of this action
     */
    public static <E, G> BehaviorResult<E, G> runEmergency(Action<E, G> action, int priority) {
        return new BehaviorResult<>(action, priority, true, InterruptCategory.EMERGENCY);
    }

    /**
     * Returns the category this result should be treated as when deciding preemption: {@link #categoryOverride} if
     * present, otherwise the action's own {@link Action#interruptCategory()}.
     *
     * @return the effective interrupt category, or {@link InterruptCategory#NORMAL} if there is no action
     */
    public InterruptCategory effectiveCategory() {
        if (action == null)
            return InterruptCategory.NORMAL;
        return categoryOverride != null ? categoryOverride : action.interruptCategory();
    }
}
