package com.azure.azurecortex.navigation.traversal;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.azure.azurecortex.api.navigation.MovementCapability;

/**
 * Stateless block-safety and forward-lookahead checks shared by the ground-walking and wall-crawling pathfinders and by
 * steering logic in {@code com.azure.azurecortex.navigation.movement.MovementController}.
 * <p>
 * Every "is this block/fluid dangerous" question here defers to the moving mob's {@link MovementCapability} —
 * {@link MovementCapability#isHazardBlock}, {@link MovementCapability#isHazardFluid}, and
 * {@link MovementCapability#isPassableSolid} — rather than any fixed tag set, since what counts as dangerous or
 * pass-through terrain is entirely mod-specific.
 */
@SuppressWarnings("unused")
public final class TraversalQueries {

    private TraversalQueries() {}

    /**
     * Returns {@code true} if the block at {@code pos} is safe to stand on or walk through for {@code mob} — not
     * classified as a hazard block, and if fluid-filled, not classified as a hazard fluid.
     *
     * @param level the world
     * @param pos   the block position to check
     * @param mob   the mob doing the check, consulted for {@link MovementCapability}
     * @return {@code true} if the block is safe
     */
    public static boolean isSafeBlock(Level level, BlockPos pos, Mob mob) {
        var capability = MovementCapability.of(mob);
        var state = level.getBlockState(pos);

        if (capability.isHazardBlock(level, pos, state))
            return false;
        if (capability.isPassableSolid(level, pos, state))
            return true;

        var fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            return !capability.isHazardFluid(level, pos, fluid);
        }
        return true;
    }

    private static boolean hasGroundWithinDrop(Level level, BlockPos feetPos, int maxDrop, Mob mob) {
        for (var drop = 1; drop <= maxDrop; drop++) {
            var ground = feetPos.below(drop);

            if (
                !level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()
                    && isSafeBlock(level, ground, mob)
            ) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns {@code true} if a mob's foot/head cells at {@code feetPos} are clear enough to occupy — not hazardous,
     * and not solid geometry unless the capability marks it passable. Used by {@link #isSafeAhead} to test a column at
     * both the mob's current height and, on retry, one block higher.
     */
    private static boolean isPassableColumn(Level level, BlockPos feetPos, Mob mob) {
        var headPos = feetPos.above();

        if (!isSafeBlock(level, feetPos, mob))
            return false;
        if (!isSafeBlock(level, headPos, mob))
            return false;

        var capability = MovementCapability.of(mob);

        var feetState = level.getBlockState(feetPos);
        var headState = level.getBlockState(headPos);

        var feetCollision = feetState.getCollisionShape(level, feetPos);
        var headCollision = headState.getCollisionShape(level, headPos);

        if (!headCollision.isEmpty() && !capability.isPassableSolid(level, headPos, headState))
            return false;

        return feetCollision.isEmpty() || capability.isPassableSolid(level, feetPos, feetState);
    }

    /**
     * Returns {@code true} if the path {@code distance} blocks ahead of the mob in the {@code forward} direction is
     * free of hazards, solid geometry, and lava, and has ground within nine blocks below.
     * <p>
     * Samples multiple points across the mob's width to account for its footprint. Each sample first checks the column
     * at the mob's current foot height; if that's blocked, it retries one block higher before giving up, matching the
     * one-block rise the entity's own step-up assist can climb without help from this function.
     *
     * @param mob      the mob performing the check
     * @param forward  normalized horizontal direction to check
     * @param distance how far ahead (blocks) to scan
     * @return {@code true} if the path is clear
     */
    public static boolean isSafeAhead(Mob mob, Vec3 forward, double distance) {
        var level = mob.level;
        var feetY = Mth.floor(mob.getBoundingBox().minY);
        var side = new Vec3(-forward.z, 0.0D, forward.x);
        var halfW = (mob.getBbWidth() / 2.0D) + 0.02D;

        for (var d = 0.25D; d <= distance; d += 0.25D) {
            var center = mob.position().add(forward.scale(d));

            for (var s = -halfW; s <= halfW; s += halfW / 2.0D) {
                var sample = center.add(side.scale(s));

                var feetPos = new BlockPos(Mth.floor(sample.x), feetY, Mth.floor(sample.z));

                if (!isPassableColumn(level, feetPos, mob)) {
                    var stepped = feetPos.above();
                    if (!isPassableColumn(level, stepped, mob)) {
                        return false;
                    }
                    feetPos = stepped;
                    feetY = feetPos.getY();
                }

                var groundPos = feetPos.below();
                var feetState = level.getBlockState(feetPos);
                var feetCollision = feetState.getCollisionShape(level, feetPos);

                var feetFluid = feetState.getFluidState();
                var inWater = feetFluid.is(FluidTags.WATER);

                if (!inWater) {
                    var capability = MovementCapability.of(mob);
                    var feetPassable = feetCollision.isEmpty() || capability.isPassableSolid(level, feetPos, feetState);

                    if (!feetPassable)
                        return false;

                    if (!hasGroundWithinDrop(level, feetPos, 9, mob))
                        return false;
                }

                if (feetFluid.is(FluidTags.LAVA))
                    return false;
                if (level.getBlockState(groundPos).getFluidState().is(FluidTags.LAVA))
                    return false;
            }
        }

        return true;
    }

    /**
     * Returns {@code true} if there is a safe landing spot {@code distance} blocks ahead of the mob in the horizontal
     * component of {@code direction}.
     *
     * @param mob       the mob about to leap
     * @param direction the intended leap direction
     * @param distance  the expected horizontal travel distance in blocks
     * @return {@code true} if the landing area is safe
     */
    public static boolean hasSafeLandingAfterLeap(Mob mob, Vec3 direction, double distance) {
        if (direction.lengthSqr() < 0.0001D) {
            return false;
        }

        var level = mob.level;
        var forward = new Vec3(direction.x, 0.0D, direction.z).normalize();

        var landingCenter = mob.position().add(forward.scale(distance));
        var feetY = mob.getBoundingBox().minY;

        var feetPos = new BlockPos(landingCenter.x, feetY, landingCenter.z);
        var headPos = feetPos.above();

        if (!isSafeBlock(level, feetPos, mob))
            return false;
        if (!isSafeBlock(level, headPos, mob))
            return false;
        if (!level.getBlockState(feetPos).getCollisionShape(level, feetPos).isEmpty())
            return false;
        if (!level.getBlockState(headPos).getCollisionShape(level, headPos).isEmpty())
            return false;

        return hasGroundWithinDrop(level, feetPos, 4, mob);
    }

    /**
     * Scans outward from {@code mob}'s current position for the nearest open-air position with solid ground directly
     * beneath it — the generalization of the historical Ovomorphosis {@code TargetingUtils.findNearbyGroundPos}, used
     * by a stranded-swimmer action to find somewhere to climb out onto.
     * <p>
     * Checks straight down first (up to 16 blocks), then spirals outward laterally in the eight compass/ordinal
     * directions out to a 24-block radius, scanning down to 16 blocks at each lateral offset. This is a plain,
     * unweighted scan — it returns the first match found in that search order, not necessarily the closest one overall
     * — which is fine for "find literally any nearby shore" but not a substitute for a real pathfind if the caller
     * needs the actual best landing spot.
     * <p>
     * Unlike most of this class, this does not consult {@link MovementCapability} — it is looking for genuinely open
     * air over genuinely solid ground, not asking whether a particular block is hazardous or passable for this mob.
     *
     * @param mob the mob to find shore for
     * @return the nearest matching ground position found, or {@code null} if none was found within range
     */
    public static BlockPos findNearbyGroundPos(Mob mob) {
        var level = mob.level;
        var origin = mob.blockPosition();

        for (var dy = 1; dy <= 16; dy++) {
            var candidate = origin.below(dy);
            if (isOpenAirOverGround(level, candidate)) {
                return candidate;
            }
        }

        int[][] lateralDirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };

        for (var radius = 1; radius <= 24; radius++) {
            for (var dir : lateralDirs) {
                var lateral = origin.offset(dir[0] * radius, 0, dir[1] * radius);
                for (var dy = 0; dy <= 16; dy++) {
                    var candidate = lateral.below(dy);
                    if (isOpenAirOverGround(level, candidate)) {
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    private static boolean isOpenAirOverGround(Level level, BlockPos candidate) {
        var below = candidate.below();
        return level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
            && level.getBlockState(candidate).getFluidState().isEmpty()
            && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }
}
