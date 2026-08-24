package com.azure.azurecortex.api.navigation;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Drives an agent's movement toward a destination each tick, on top of whatever {@link Pathfinder} produced the route.
 * <p>
 * This is the generalization of the historical {@code MovementUtils}/{@code CrawlingMovementManager} steering helpers:
 * an implementation is responsible for turning "the agent wants to move toward point P" into an actual velocity,
 * accounting for obstacle steering, danger-entity repulsion, and — for capable agents — wall-crawl physics and
 * orientation. See {@code com.azure.azurecortex.navigation.movement.MovementController} for the ground-walking
 * implementation and {@code com.azure.azurecortex.navigation.crawl.CrawlController} for the wall-crawl-aware one.
 */
public interface NavigationHandler {

    /**
     * Computes a safe movement vector toward {@code desiredMovement}, steering around obstacles and hazards as needed.
     *
     * @param mob             the mob moving
     * @param desiredMovement the ideal (unobstructed) movement vector for this tick
     * @return a safe movement vector, or {@link Vec3#ZERO} if the mob is completely blocked
     */
    Vec3 computeMovement(Mob mob, Vec3 desiredMovement);

    /**
     * Called once per tick to update any physics/orientation state this handler owns (e.g. gravity suppression while
     * wall-crawling). Implementations with no such state may no-op.
     *
     * @param mob      the mob to update
     * @param movement the movement vector applied this tick, as returned by {@link #computeMovement}
     */
    default void tick(Mob mob, Vec3 movement) {}
}
