package com.azure.azurecortex.navigation.movement;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import com.azure.azurecortex.api.navigation.MovementCapability;
import com.azure.azurecortex.api.navigation.NavigationHandler;
import com.azure.azurecortex.navigation.traversal.TraversalQueries;
import com.azure.azurecortex.util.McCompat;

/**
 * Ground-walking {@link NavigationHandler}: obstacle steering and danger-entity repulsion.
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
    public Vec3d computeMovement(EntityLiving mob, Vec3d desiredMovement) {
        return findSafeMovement(mob, desiredMovement, new int[] { 0 });
    }

    /**
     * Returns a movement vector that steers around obstacles while staying as close as possible to
     * {@code desiredMovement}.
     * <p>
     * Tries progressively larger steering angles from {@link #STEER_ANGLES}, biasing toward the direction that worked
     * last time (tracked via {@code steerBias[0]}). Returns {@link Vec3d#ZERO} if no safe direction is found.
     *
     * @param mob             the mob moving
     * @param desiredMovement the ideal movement vector
     * @param steerBias       a single-element array holding the last successful steering bias ({@code 1} = right,
     *                        {@code -1} = left, {@code 0} = none); updated in place
     * @return a safe movement vector, or {@link Vec3d#ZERO} if the mob is completely blocked
     */
    public static Vec3d findSafeMovement(EntityLiving mob, Vec3d desiredMovement, int[] steerBias) {
        Vec3d horizontal = new Vec3d(desiredMovement.x, 0.0D, desiredMovement.z);
        double length = McCompat.length(horizontal);

        if (length < 0.001D)
            return desiredMovement;

        Vec3d forward = horizontal.normalize();

        if (TraversalQueries.isSafeAhead(mob, forward, DEFAULT_LOOK_AHEAD)) {
            steerBias[0] = 0;
            return desiredMovement;
        }

        int[] angles = sortByBias(steerBias[0]);

        for (int angleDeg : angles) {
            Vec3d rotated = rotate(forward, angleDeg);
            if (TraversalQueries.isSafeAhead(mob, rotated, DEFAULT_LOOK_AHEAD)) {
                steerBias[0] = angleDeg > 0 ? 1 : -1;
                return rotated.scale(length);
            }
        }

        if (steerBias[0] != 0) {
            Vec3d wallFollow = rotate(forward, steerBias[0] > 0 ? 90 : -90);
            if (TraversalQueries.isSafeAhead(mob, wallFollow, DEFAULT_LOOK_AHEAD * 0.5D)) {
                return wallFollow.scale(length);
            }
        }

        return Vec3d.ZERO;
    }

    private static Vec3d rotate(Vec3d forward, int angleDeg) {
        double radians = Math.toRadians(angleDeg);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3d(
            forward.x * cos - forward.z * sin,
            0.0D,
            forward.x * sin + forward.z * cos
        );
    }

    private static int[] sortByBias(int bias) {
        if (bias == 0)
            return STEER_ANGLES;

        List<Integer> preferred = new ArrayList<>();
        List<Integer> other = new ArrayList<>();

        for (int a : STEER_ANGLES) {
            if ((bias > 0 && a > 0) || (bias < 0 && a < 0)) {
                preferred.add(a);
            } else {
                other.add(a);
            }
        }

        preferred.addAll(other);
        int[] sorted = new int[preferred.size()];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = preferred.get(i);
        }
        return sorted;
    }

    /**
     * Computes a repulsion vector that pushes {@code mob} away from nearby hazardous entities, as classified by
     * {@link MovementCapability#isHazardEntityType}.
     * <p>
     * Each hazard entity within five blocks contributes a weighted outward force. Returns {@link Vec3d#ZERO} if no
     * hazard entities are nearby.
     *
     * @param mob the mob to protect
     * @return the combined repulsion vector
     */
    public static Vec3d dangerEntityRepulsion(EntityLiving mob) {
        final double dangerRadius = 5.0D;
        final double dangerRadiusSqr = dangerRadius * dangerRadius;
        final double avoidStrength = 1.25D;

        MovementCapability capability = MovementCapability.of(mob);

        Vec3d away = Vec3d.ZERO;
        AxisAlignedBB box = mob.getEntityBoundingBox().grow(dangerRadius);
        Vec3d mobPos = McCompat.position(mob);

        for (Entity entity : mob.world.getEntitiesWithinAABBExcludingEntity(mob, box)) {
            if (!capability.isHazardEntityType(entity.getClass())) {
                continue;
            }

            Vec3d offset = mobPos.subtract(McCompat.position(entity));
            double distSqr = McCompat.lengthSqr(offset);

            if (distSqr > dangerRadiusSqr) {
                continue;
            }

            if (distSqr < 0.0001D) {
                // Directly on top of the hazard: push backwards along the mob's facing.
                double yawRad = Math.toRadians(mob.rotationYaw);
                offset = new Vec3d(Math.sin(yawRad), 0.0D, -Math.cos(yawRad));
                distSqr = 0.0001D;
            }

            double distance = Math.sqrt(distSqr);
            double weight = 1.0D - distance / dangerRadius;

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
    public static Vec3d steerAwayFromDangerEntities(EntityLiving mob, Vec3d desiredMovement) {
        Vec3d away = dangerEntityRepulsion(mob);

        if (McCompat.lengthSqr(away) < 0.0001D) {
            return desiredMovement;
        }

        Vec3d desiredHorizontal = new Vec3d(desiredMovement.x, 0.0D, desiredMovement.z);
        double desiredLength = McCompat.length(desiredHorizontal);

        if (desiredLength < 0.001D) {
            return away.normalize().scale(0.12D);
        }

        Vec3d blended = desiredHorizontal.add(away);

        if (McCompat.lengthSqr(blended) < 0.0001D) {
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
    public static boolean hasNearbyDangerEntity(EntityLiving mob) {
        return McCompat.lengthSqr(dangerEntityRepulsion(mob)) > 0.0001D;
    }
}
