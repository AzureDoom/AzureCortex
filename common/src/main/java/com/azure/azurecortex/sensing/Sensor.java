package com.azure.azurecortex.sensing;

import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.runtime.CortexRuntime;

/**
 * Periodically refreshes some piece of perception state onto an agent's {@link Blackboard}.
 * <p>
 * {@link CortexRuntime} ticks one (optional) sensor per agent every game tick, skipping the call while the currently
 * running action is locked against interruption (see {@code InterruptCategory}). Sensors are the generalization of the
 * historical {@code TargetingSystem}: whatever an agent needs to periodically re-evaluate about its surroundings —
 * targets, hazards, ambient conditions — implements this interface and is wired into the agent's {@link CortexRuntime}
 * at construction time.
 *
 * @param <E> the agent type this sensor serves
 */
@FunctionalInterface
public interface Sensor<E> {

    /**
     * Advances this sensor by one tick, writing whatever perception state it owns onto {@code blackboard}.
     *
     * @param agent      the agent this sensor serves
     * @param blackboard the agent's shared AI state store
     */
    void tick(E agent, Blackboard blackboard);
}
