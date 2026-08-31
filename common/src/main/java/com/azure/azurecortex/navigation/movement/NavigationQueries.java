package com.azure.azurecortex.navigation.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import com.azure.azurecortex.navigation.crawl.CrawlController;
import com.azure.azurecortex.navigation.traversal.CollisionQueries;

/**
 * Decision-level queries about what kind of movement is required to reach a position: an ordinary walk, a one-block
 * step/jump, or a wall-crawl.
 */
@SuppressWarnings("unused")
public final class NavigationQueries {

    private NavigationQueries() {}

    /** Describes the type of movement required to occupy a given block position. */
    public enum MovementType {
        /** The mob can walk to this position normally. */
        WALK,
        /** The mob must jump one block up to reach this position. */
        JUMP,
        /** The mob must climb (wall-crawl or ladder) to reach this position. */
        CLIMB
    }

    /**
     * Determines the {@link MovementType} required for the mob to occupy {@code pos}.
     *
     * @param mob the mob being evaluated
     * @param pos the target block position
     * @return the movement type needed
     */
    public static MovementType requiredMovementAt(Mob mob, BlockPos pos) {
        var below = pos.below();
        var stateBelow = mob.level().getBlockState(below);

        if (stateBelow.entityCanStandOn(mob.level(), below, mob)) {
            return MovementType.WALK;
        }

        var twoBelow = below.below();
        var stateTwoBelow = mob.level().getBlockState(twoBelow);

        if (stateTwoBelow.entityCanStandOn(mob.level(), twoBelow, mob)) {
            return MovementType.JUMP;
        }

        return MovementType.CLIMB;
    }

    /**
     * Returns {@code true} if reaching {@code wanted} requires wall-crawl movement given the mob's current position and
     * the surrounding terrain.
     *
     * @param mob    the mob evaluating the move
     * @param wanted the world-space position to reach
     * @return {@code true} if wall-crawl movement is necessary
     */
    public static boolean needsWallCrawl(Mob mob, Vec3 wanted) {
        if (!CrawlController.canWallCrawl(mob)) {
            return false;
        }

        var feet = mob.blockPosition();

        if (!CollisionQueries.isClimbable(mob.level(), feet, true)) {
            return false;
        }

        var current = requiredMovementAt(mob, mob.blockPosition());
        if (current == MovementType.CLIMB) {
            return true;
        }

        var wantedBlock = BlockPos.containing(wanted.x, wanted.y, wanted.z);
        var wantedType = requiredMovementAt(mob, wantedBlock);

        if (wantedType == MovementType.CLIMB) {
            return true;
        }

        return wantedBlock.equals(mob.blockPosition().above()) && wantedType == MovementType.JUMP;
    }

    /**
     * Computes a velocity vector for a wall-crawling mob moving toward a world-space {@code wanted} position, clamped
     * to {@code speed}.
     *
     * @param mob    the mob to move
     * @param wanted the target position in world space
     * @param speed  maximum movement speed in blocks per tick
     * @return the velocity to apply this tick, or {@link Vec3#ZERO} if already at the target
     */
    public static Vec3 computeWallCrawlVelocity(Mob mob, Vec3 wanted, double speed) {
        var center = mob.getBoundingBox().getCenter();
        var offset = wanted.subtract(center);
        var dist = offset.length();

        if (dist < 0.1D) {
            return Vec3.ZERO;
        }

        var clampedSpeed = Math.min(speed, dist);
        return offset.normalize().scale(clampedSpeed);
    }
}
