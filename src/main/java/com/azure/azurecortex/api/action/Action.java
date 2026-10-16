package com.azure.azurecortex.api.action;

import com.azure.azurecortex.api.behavior.BehaviorNode;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.runtime.CooldownTracker;
import com.azure.azurecortex.runtime.CortexRuntime;
import com.azure.azurecortex.runtime.InterruptCategory;

/**
 * Represents a discrete, stateful behavior that an agent can perform over one or more ticks.
 * <p>
 * Actions are selected by {@link BehaviorNode}s and driven by {@link CortexRuntime}. The lifecycle is: {@link #start} →
 * repeated {@link #tick} calls → {@link #stop}.
 *
 * @param <E> the agent type this action operates on
 * @param <G> the mod-defined goal-type enum used to attribute {@link ActionOutcome} feedback to a GOAP goal; pass
 *            {@link Void} (and always {@code null} goal-type overrides) for agents that don't use the GOAP layer
 */
public interface Action<E, G> {

    /**
     * Called once when the action is first activated.
     *
     * @param agent      the agent executing this action
     * @param blackboard the agent's shared AI state store
     * @param cooldowns  the agent's cooldown tracker
     */
    void start(E agent, Blackboard blackboard, CooldownTracker cooldowns);

    /**
     * Called every game tick while this action is active.
     *
     * @param agent      the agent executing this action
     * @param blackboard the agent's shared AI state store
     * @param cooldowns  the agent's cooldown tracker
     * @return an {@link ActionOutcome} describing what happened this tick: {@link ActionOutcome.Running} or
     *         {@link ActionOutcome.Blocked} to continue (the latter also surfacing a reason to GOAP without stopping),
     *         or {@link ActionOutcome.Success} / {@link ActionOutcome.Failed} to end the action
     */
    ActionOutcome<G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns);

    /**
     * Called once when the action ends, either naturally or via interruption.
     *
     * @param agent      the agent that was executing this action
     * @param blackboard the agent's shared AI state store
     * @param cooldowns  the agent's cooldown tracker
     * @param reason     why the action stopped ({@code SUCCESS}, {@code FAILURE}, or {@code INTERRUPTED})
     */
    void stop(E agent, Blackboard blackboard, CooldownTracker cooldowns, ActionStatus reason);

    /**
     * Returns {@code true} if a higher-priority action is allowed to preempt this one while it is still running (i.e.
     * while {@link #tick} keeps returning {@link ActionOutcome.Running} or {@link ActionOutcome.Blocked}).
     * <p>
     * Kept for backward compatibility and as the basis of the default {@link #interruptCategory()}. New code that needs
     * finer-grained control (e.g. "resistant to normal preemption but not to emergencies") should override
     * {@link #interruptCategory()} instead of relying on this alone.
     *
     * @return {@code true} if the action can be interrupted mid-execution
     */
    boolean isInterruptible();

    /**
     * Returns this action's {@link InterruptCategory}, governing both how resistant it is to preemption while running
     * and, when it is a candidate returned by the behavior tree, what authority it has to preempt whatever is currently
     * running.
     * <p>
     * The default derives from {@link #isInterruptible()} for backward compatibility: {@code true} maps to
     * {@link InterruptCategory#NORMAL}, {@code false} maps to {@link InterruptCategory#LOCKED}. Actions representing
     * genuine emergencies (on fire, imminent explosion, critical health, ...) should override this to return
     * {@link InterruptCategory#EMERGENCY} so they are never trapped behind a {@link InterruptCategory#LOCKED} action
     * and so that, once running, they resist everything except a higher-priority emergency.
     *
     * @return this action's interrupt category
     */
    default InterruptCategory interruptCategory() {
        return isInterruptible() ? InterruptCategory.NORMAL : InterruptCategory.LOCKED;
    }

    /**
     * Returns the numeric priority of this action. Higher values take precedence over lower ones when the runtime
     * evaluates competing actions on the same tick.
     *
     * @return this action's priority
     */
    int priority();

    /**
     * A short, stable name for this action used by diagnostics and log output. Defaults to the simple class name, which
     * is fine for most actions; override when a class is reused generically for several conceptually different
     * behaviors and a more specific label would make diagnostic output actually useful.
     *
     * @return a short human-readable identifier for this action
     */
    default String debugName() {
        return getClass().getSimpleName();
    }
}
