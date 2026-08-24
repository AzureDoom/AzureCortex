package com.azure.azurecortex.sensing;

import net.minecraft.world.entity.Mob;

import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.BlackboardKey;
import com.azure.azurecortex.navigation.movement.MovementController;

/**
 * A small {@link Sensor} that refreshes a boolean blackboard flag indicating whether a hazard entity (as classified by
 * {@code com.azure.azurecortex.api.navigation.MovementCapability#isHazardEntityType}) is currently near the agent.
 * <p>
 * This is a thin convenience wrapper around {@link MovementController#hasNearbyDangerEntity} — most agents don't need a
 * dedicated sensor for this and can just call that method directly from an action or planner. Use this when a behavior
 * tree wants the flag pre-computed on the blackboard (e.g. to gate a branch without recomputing the repulsion scan
 * itself).
 *
 * @param <E> the agent type, must be a {@link Mob}
 */
public final class HazardSensor<E extends Mob> implements Sensor<E> {

    private final BlackboardKey<Boolean> key;

    /**
     * @param key the blackboard key this sensor writes its result to
     */
    public HazardSensor(BlackboardKey<Boolean> key) {
        this.key = key;
    }

    @Override
    public void tick(E agent, Blackboard blackboard) {
        blackboard.set(key, MovementController.hasNearbyDangerEntity(agent));
    }
}
