package com.azure.azurecortex.navigation.traversal;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.azure.azurecortex.api.navigation.MovementCapability;
import com.azure.azurecortex.util.McCompat;

/**
 * Stateless block-safety and forward-lookahead checks shared by the pathfinder and by steering logic in
 * {@code com.azure.azurecortex.navigation.movement.MovementController}.
 * <p>
 * Every "is this block/fluid dangerous" question here defers to the moving mob's {@link MovementCapability} —
 * {@link MovementCapability#isHazardBlock}, {@link MovementCapability#isHazardFluid}, and
 * {@link MovementCapability#isPassableSolid} — rather than any fixed block set, since what counts as dangerous or
 * pass-through terrain is entirely mod-specific.
 */
@SuppressWarnings("unused")
public final class TraversalQueries {

    private TraversalQueries() {}

    /**
     * Returns {@code true} if the block at {@code pos} is safe to stand on or walk through for {@code mob} — not
     * classified as a hazard block, and if it is a liquid, not classified as a hazard fluid.
     *
     * @param world the world
     * @param pos   the block position to check
     * @param mob   the mob doing the check, consulted for {@link MovementCapability}
     * @return {@code true} if the block is safe
     */
    public static boolean isSafeBlock(World world, BlockPos pos, EntityLiving mob) {
        MovementCapability capability = MovementCapability.of(mob);
        IBlockState state = world.getBlockState(pos);

        if (capability.isHazardBlock(world, pos, state))
            return false;
        if (capability.isPassableSolid(world, pos, state))
            return true;

        if (McCompat.isLiquid(state)) {
            return !capability.isHazardFluid(world, pos, state);
        }
        return true;
    }

    private static boolean hasGroundWithinDrop(World world, BlockPos feetPos, int maxDrop, EntityLiving mob) {
        for (int drop = 1; drop <= maxDrop; drop++) {
            BlockPos ground = feetPos.down(drop);

            if (!McCompat.hasNoCollision(world, ground) && isSafeBlock(world, ground, mob)) {
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
    private static boolean isPassableColumn(World world, BlockPos feetPos, EntityLiving mob) {
        BlockPos headPos = feetPos.up();

        if (!isSafeBlock(world, feetPos, mob))
            return false;
        if (!isSafeBlock(world, headPos, mob))
            return false;

        MovementCapability capability = MovementCapability.of(mob);

        IBlockState feetState = world.getBlockState(feetPos);
        IBlockState headState = world.getBlockState(headPos);

        if (
            !McCompat.hasNoCollision(world, headPos, headState)
                && !capability.isPassableSolid(world, headPos, headState)
        )
            return false;

        return McCompat.hasNoCollision(world, feetPos, feetState)
            || capability.isPassableSolid(world, feetPos, feetState);
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
    public static boolean isSafeAhead(EntityLiving mob, Vec3d forward, double distance) {
        World world = mob.world;
        int feetY = MathHelper.floor(mob.getEntityBoundingBox().minY);
        Vec3d side = new Vec3d(-forward.z, 0.0D, forward.x);
        double halfW = (mob.width / 2.0D) + 0.02D;
        Vec3d origin = McCompat.position(mob);

        for (double d = 0.25D; d <= distance; d += 0.25D) {
            Vec3d center = origin.add(forward.scale(d));

            for (double s = -halfW; s <= halfW; s += halfW / 2.0D) {
                Vec3d sample = center.add(side.scale(s));

                BlockPos feetPos = new BlockPos(MathHelper.floor(sample.x), feetY, MathHelper.floor(sample.z));

                if (!isPassableColumn(world, feetPos, mob)) {
                    BlockPos stepped = feetPos.up();
                    if (!isPassableColumn(world, stepped, mob)) {
                        return false;
                    }
                    feetPos = stepped;
                    feetY = feetPos.getY();
                }

                BlockPos groundPos = feetPos.down();
                IBlockState feetState = world.getBlockState(feetPos);

                boolean inWater = feetState.getMaterial() == Material.WATER;

                if (!inWater) {
                    MovementCapability capability = MovementCapability.of(mob);
                    boolean feetPassable = McCompat.hasNoCollision(world, feetPos, feetState)
                        || capability.isPassableSolid(world, feetPos, feetState);

                    if (!feetPassable)
                        return false;

                    if (!hasGroundWithinDrop(world, feetPos, 9, mob))
                        return false;
                }

                if (feetState.getMaterial() == Material.LAVA)
                    return false;
                if (world.getBlockState(groundPos).getMaterial() == Material.LAVA)
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
    public static boolean hasSafeLandingAfterLeap(EntityLiving mob, Vec3d direction, double distance) {
        if (McCompat.lengthSqr(direction) < 0.0001D) {
            return false;
        }

        World world = mob.world;
        Vec3d forward = new Vec3d(direction.x, 0.0D, direction.z).normalize();

        Vec3d landingCenter = McCompat.position(mob).add(forward.scale(distance));
        double feetY = mob.getEntityBoundingBox().minY;

        BlockPos feetPos = new BlockPos(landingCenter.x, feetY, landingCenter.z);
        BlockPos headPos = feetPos.up();

        if (!isSafeBlock(world, feetPos, mob))
            return false;
        if (!isSafeBlock(world, headPos, mob))
            return false;
        if (!McCompat.hasNoCollision(world, feetPos))
            return false;
        if (!McCompat.hasNoCollision(world, headPos))
            return false;

        return hasGroundWithinDrop(world, feetPos, 4, mob);
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
    public static BlockPos findNearbyGroundPos(EntityLiving mob) {
        World world = mob.world;
        BlockPos origin = new BlockPos(mob);

        for (int dy = 1; dy <= 16; dy++) {
            BlockPos candidate = origin.down(dy);
            if (isOpenAirOverGround(world, candidate)) {
                return candidate;
            }
        }

        int[][] lateralDirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };

        for (int radius = 1; radius <= 24; radius++) {
            for (int[] dir : lateralDirs) {
                BlockPos lateral = origin.add(dir[0] * radius, 0, dir[1] * radius);
                for (int dy = 0; dy <= 16; dy++) {
                    BlockPos candidate = lateral.down(dy);
                    if (isOpenAirOverGround(world, candidate)) {
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    private static boolean isOpenAirOverGround(World world, BlockPos candidate) {
        IBlockState state = world.getBlockState(candidate);
        return McCompat.hasNoCollision(world, candidate, state)
            && !McCompat.isLiquid(state)
            && !McCompat.hasNoCollision(world, candidate.down());
    }
}
