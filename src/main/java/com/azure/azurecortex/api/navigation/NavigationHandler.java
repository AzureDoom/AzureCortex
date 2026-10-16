package com.azure.azurecortex.api.navigation;

import net.minecraft.entity.EntityLiving;
import net.minecraft.util.math.Vec3d;

/**
 * Drives an agent's movement toward a destination each tick, on top of whatever {@link Pathfinder} produced the route.
 * <p>
 * This is the generalization of the historical {@code MovementUtils} steering helpers: an implementation is responsible
 * for turning "the agent wants to move toward point P" into an actual velocity, accounting for obstacle steering,
 * danger-entity repulsion, and any movement-model-specific physics. See
 * {@code com.azure.azurecortex.navigation.movement.MovementController} for the ground-walking implementation.
 */
@SuppressWarnings("unused")
public interface NavigationHandler {

    /**
     * Computes a safe movement vector toward {@code desiredMovement}, steering around obstacles and hazards as needed.
     *
     * @param mob             the mob moving
     * @param desiredMovement the ideal (unobstructed) movement vector for this tick
     * @return a safe movement vector, or {@link Vec3d#ZERO} if the mob is completely blocked
     */
    Vec3d computeMovement(EntityLiving mob, Vec3d desiredMovement);

    /**
     * Called once per tick to update any physics/orientation state this handler owns. Implementations with no such
     * state may no-op.
     *
     * @param mob      the mob to update
     * @param movement the movement vector applied this tick, as returned by {@link #computeMovement}
     */
    default void tick(EntityLiving mob, Vec3d movement) {}
}
