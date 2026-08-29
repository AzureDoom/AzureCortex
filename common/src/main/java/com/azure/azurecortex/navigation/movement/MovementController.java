package com.azure.azurecortex.navigation.movement;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

import com.azure.azurecortex.api.navigation.MovementCapability;
import com.azure.azurecortex.api.navigation.NavigationHandler;
import com.azure.azurecortex.navigation.traversal.TraversalQueries;

/**
 * Ground-walking {@link NavigationHandler}: obstacle steering and danger-entity repulsion for mobs that don't need
 * wall-crawl physics. See {@code com.azure.azurecortex.navigation.crawl.CrawlController} for the wall-crawl-aware
 * counterpart.
 */
@SuppressWarnings("unused")
public final class MovementController implements NavigationHandler {

    /** Shared stateless instance. */
    public static final MovementController INSTANCE = new MovementController();

    /** Default distance (blocks) ahead of the mob used for obstacle look-ahead checks. */
    private static final double DEFAULT_LOOK_AHEAD = 1.25D;

    /** Candidate steering angles (degrees) tried in order when the direct path is blocked. */
    private static final int[] STEER_ANGLES = { 30, -30, 60, -60, 90, -90, 120, -120, 150, -150 };

    @Override
    public Vec3 computeMovement(Mob mob, Vec3 desiredMovement) {
        return findSafeMovement(mob, desiredMovement, new int[] { 0 });
    }

    /**
     * Returns a movement vector that steers around obstacles while staying as close as possible to
     * {@code desiredMovement}.
     * <p>
     * Tries progressively larger steering angles from {@link #STEER_ANGLES}, biasing toward the direction that worked
     * last time (tracked via {@code steerBias[0]}). Returns {@link Vec3#ZERO} if no safe direction is found.
     *
     * @param mob             the mob moving
     * @param desiredMovement the ideal movement vector
     * @param steerBias       a single-element array holding the last successful steering bias ({@code 1} = right,
     *                        {@code -1} = left, {@code 0} = none); updated in place
     * @return a safe movement vector, or {@link Vec3#ZERO} if the mob is completely blocked
     */
    public static Vec3 findSafeMovement(Mob mob, Vec3 desiredMovement, int[] steerBias) {
        var horizontal = new Vec3(desiredMovement.x, 0.0D, desiredMovement.z);
        var length = horizontal.length();

        if (length < 0.001D)
            return desiredMovement;

        var forward = horizontal.normalize();

        if (TraversalQueries.isSafeAhead(mob, forward, DEFAULT_LOOK_AHEAD)) {
            steerBias[0] = 0;
            return desiredMovement;
        }

        var angles = sortByBias(steerBias[0]);

        for (var angleDeg : angles) {
            var rotated = rotate(forward, angleDeg);
            if (TraversalQueries.isSafeAhead(mob, rotated, DEFAULT_LOOK_AHEAD)) {
                steerBias[0] = angleDeg > 0 ? 1 : -1;
                return rotated.scale(length);
            }
        }

        if (steerBias[0] != 0) {
            var wallFollow = rotate(forward, steerBias[0] > 0 ? 90 : -90);
            if (TraversalQueries.isSafeAhead(mob, wallFollow, DEFAULT_LOOK_AHEAD * 0.5D)) {
                return wallFollow.scale(length);
            }
        }

        return Vec3.ZERO;
    }

    private static Vec3 rotate(Vec3 forward, int angleDeg) {
        var radians = Math.toRadians(angleDeg);
        var cos = Math.cos(radians);
        var sin = Math.sin(radians);
        return new Vec3(
            forward.x * cos - forward.z * sin,
            0.0D,
            forward.x * sin + forward.z * cos
        );
    }

    private static int[] sortByBias(int bias) {
        if (bias == 0)
            return STEER_ANGLES;

        var preferred = new ArrayList<Integer>();
        var other = new ArrayList<Integer>();

        for (var a : STEER_ANGLES) {
            if ((bias > 0 && a > 0) || (bias < 0 && a < 0)) {
                preferred.add(a);
            } else {
                other.add(a);
            }
        }

        preferred.addAll(other);
        return preferred.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Computes a repulsion vector that pushes {@code mob} away from nearby hazardous entities, as classified by
     * {@link MovementCapability#isHazardEntityType}.
     * <p>
     * Each hazard entity within five blocks contributes a weighted outward force. Returns {@link Vec3#ZERO} if no
     * hazard entities are nearby.
     *
     * @param mob the mob to protect
     * @return the combined repulsion vector
     */
    public static Vec3 dangerEntityRepulsion(Mob mob) {
        final var dangerRadius = 5.0D;
        final var dangerRadiusSqr = dangerRadius * dangerRadius;
        final var avoidStrength = 1.25D;

        var capability = MovementCapability.of(mob);

        var away = Vec3.ZERO;
        var box = mob.getBoundingBox().inflate(dangerRadius);

        for (var entity : mob.level.getEntities(mob, box)) {
            if (!capability.isHazardEntityType(entity.getType())) {
                continue;
            }

            var offset = mob.position().subtract(entity.position());
            var distSqr = offset.lengthSqr();

            if (distSqr > dangerRadiusSqr) {
                continue;
            }

            if (distSqr < 0.0001D) {
                offset = Vec3.directionFromRotation(0.0F, mob.getYRot()).scale(-1.0D);
                distSqr = 0.0001D;
            }

            var distance = Math.sqrt(distSqr);
            var weight = 1.0D - distance / dangerRadius;

            away = away.add(offset.normalize().scale(weight * avoidStrength));
        }

        return away;
    }

    /**
     * Blends {@code desiredMovement} with a repulsion vector away from nearby hazardous entities, returning a movement
     * vector that avoids them while still pursuing the goal.
     *
     * @param mob             the mob moving
     * @param desiredMovement the ideal movement vector before repulsion is applied
     * @return the adjusted movement vector
     */
    public static Vec3 steerAwayFromDangerEntities(Mob mob, Vec3 desiredMovement) {
        var away = dangerEntityRepulsion(mob);

        if (away.lengthSqr() < 0.0001D) {
            return desiredMovement;
        }

        var desiredHorizontal = new Vec3(desiredMovement.x, 0.0D, desiredMovement.z);
        var desiredLength = desiredHorizontal.length();

        if (desiredLength < 0.001D) {
            return away.normalize().scale(0.12D);
        }

        var blended = desiredHorizontal.add(away);

        if (blended.lengthSqr() < 0.0001D) {
            return away.normalize().scale(desiredLength);
        }

        return blended.normalize().scale(desiredLength);
    }

    /**
     * Returns {@code true} if any hazard entity is close enough to produce a non-zero repulsion vector for {@code mob}.
     *
     * @param mob the mob to check
     * @return {@code true} if at least one hazard entity is within repulsion range
     */
    public static boolean hasNearbyDangerEntity(Mob mob) {
        return dangerEntityRepulsion(mob).lengthSqr() > 0.0001D;
    }
}
