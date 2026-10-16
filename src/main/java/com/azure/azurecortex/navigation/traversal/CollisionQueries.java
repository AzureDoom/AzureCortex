package com.azure.azurecortex.navigation.traversal;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import com.azure.azurecortex.api.navigation.MovementCapability;
import com.azure.azurecortex.util.McCompat;

/**
 * Stateless collision/footprint classification queries: "can a mob's full bounding box occupy this position" and "is
 * this block physically solid". These back the ground-walking A* ({@code AStarPathfinder}).
 */
@SuppressWarnings("unused")
public final class CollisionQueries {

    private CollisionQueries() {}

    /**
     * Returns {@code true} if the mob's full bounding-box footprint fits safely at {@code feet} — every foot/head cell
     * under its horizontal footprint is a safe, non-hazardous, non-solid (or capability-passable) block, and there is
     * solid ground (or fluid) directly beneath.
     * <p>
     * The center column (the one {@code feet} itself sits over) must match exactly at {@code feet.getY()}. Every other
     * column under the footprint is allowed to be clear at {@code feet.getY() - 1}, {@code feet.getY()}, or
     * {@code feet.getY() + 1} — i.e. tolerates an ordinary single-block step up or down from the candidate position,
     * rather than demanding every column be clear at the exact same Y.
     * <p>
     * This distinction only matters once the mob's footprint is wider than one block ({@code width > ~1.0}): a
     * footprint that size straddles two columns in both X and Z no matter where it's centered, and a natural hillside
     * is essentially a staircase of single-block steps — an exact-Y-match requirement rejects almost every stance along
     * it, since one of the straddled columns will always be one block higher or lower than the other. A real mob's body
     * resting partly on a step like that is completely normal; a narrower mob (whose footprint fits inside one column)
     * never hits this at all, which is why this only shows up for wider entities.
     *
     * @param world the world
     * @param mob   the mob being evaluated
     * @param feet  the candidate foot position
     * @return {@code true} if the mob's full bounding box fits safely at this position
     */
    public static boolean canStandAt(World world, EntityLiving mob, BlockPos feet) {
        MovementCapability capability = MovementCapability.of(mob);
        double padding = 0.02D;
        double radius = (mob.width / 2.0D) + padding;

        double centerX = feet.getX() + 0.5D;
        double centerZ = feet.getZ() + 0.5D;

        int minX = MathHelper.floor(centerX - radius);
        int maxX = MathHelper.floor(centerX + radius);
        int minZ = MathHelper.floor(centerZ - radius);
        int maxZ = MathHelper.floor(centerZ + radius);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (x == feet.getX() && z == feet.getZ()) {
                    continue;
                }
                if (!edgeColumnClear(world, mob, capability, x, feet.getY(), z)) {
                    return false;
                }
            }
        }

        BlockPos checkHead = feet.up();

        if (!TraversalQueries.isSafeBlock(world, feet, mob))
            return false;
        if (!TraversalQueries.isSafeBlock(world, checkHead, mob))
            return false;

        IBlockState feetState = world.getBlockState(feet);
        IBlockState headState = world.getBlockState(checkHead);

        if (
            !McCompat.hasNoCollision(world, feet, feetState)
                && !capability.isPassableSolid(world, feet, feetState)
        )
            return false;

        if (
            !McCompat.hasNoCollision(world, checkHead, headState)
                && !capability.isPassableSolid(world, checkHead, headState)
        )
            return false;

        boolean isInFluid = McCompat.isLiquid(feetState);
        return isInFluid || !McCompat.hasNoCollision(world, feet.down());
    }

    /**
     * Returns {@code true} if column {@code (x, z)} is clear enough for the edge of a wide mob's footprint to rest
     * there, checking {@code candidateY - 1}, {@code candidateY}, and {@code candidateY + 1} rather than only
     * {@code candidateY} — see {@link #canStandAt}'s docs for why an edge column needs this tolerance and the center
     * column doesn't.
     */
    private static boolean edgeColumnClear(
        World world,
        EntityLiving mob,
        MovementCapability capability,
        int x,
        int candidateY,
        int z
    ) {
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos checkFeet = new BlockPos(x, candidateY + dy, z);
            BlockPos checkHead = checkFeet.up();

            if (!TraversalQueries.isSafeBlock(world, checkFeet, mob))
                continue;
            if (!TraversalQueries.isSafeBlock(world, checkHead, mob))
                continue;

            IBlockState feetState = world.getBlockState(checkFeet);
            IBlockState headState = world.getBlockState(checkHead);

            boolean feetOk = McCompat.hasNoCollision(world, checkFeet, feetState)
                || capability.isPassableSolid(world, checkFeet, feetState);
            boolean headOk = McCompat.hasNoCollision(world, checkHead, headState)
                || capability.isPassableSolid(world, checkHead, headState);

            if (feetOk && headOk) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the block is physically solid for pathfinding purposes — has a non-null collision box.
     * Blocks like tall grass and flowers return {@code false} here even though they are not air.
     */
    public static boolean isPhysicallySolid(World world, BlockPos pos) {
        return !McCompat.hasNoCollision(world, pos);
    }

    /**
     * Marker accessor so callers outside this package can classify a liquid the same way
     * {@link TraversalQueries#isSafeBlock} would, without duplicating the capability lookup.
     */
    public static boolean isHazardFluid(World world, BlockPos pos, IBlockState fluid, EntityLiving mob) {
        return MovementCapability.of(mob).isHazardFluid(world, pos, fluid);
    }
}
