package com.azure.azurecortex.navigation.traversal;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

import com.azure.azurecortex.api.navigation.MovementCapability;
import com.azure.azurecortex.navigation.astar.PathNodeCache;

/**
 * Stateless collision/footprint classification queries: "can a mob's full bounding box occupy this position", "is this
 * a valid surface-cling node", "is there solid geometry nearby to climb". These back both the ground-walking A*
 * ({@code AStarPathfinder}) and the wall-crawl-aware search ({@code CrawlTraversalEvaluator}).
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
     * This distinction only matters once the mob's footprint is wider than one block ({@code bbWidth > ~1.0}): a
     * footprint that size straddles two columns in both X and Z no matter where it's centered, and a natural hillside
     * is essentially a staircase of single-block steps — an exact-Y-match requirement rejects almost every stance along
     * it, since one of the straddled columns will always be one block higher or lower than the other. A real mob's body
     * resting partly on a step like that is completely normal; a narrower mob (whose footprint fits inside one column)
     * never hits this at all, which is why this only shows up for wider entities.
     *
     * @param level the world
     * @param mob   the mob being evaluated
     * @param feet  the candidate foot position
     * @return {@code true} if the mob's full bounding box fits safely at this position
     */
    public static boolean canStandAt(Level level, Mob mob, BlockPos feet) {
        var capability = MovementCapability.of(mob);
        var padding = 0.02D;
        var radius = (mob.getBbWidth() / 2.0D) + padding;

        var centerX = feet.getX() + 0.5D;
        var centerZ = feet.getZ() + 0.5D;

        var minX = Mth.floor(centerX - radius);
        var maxX = Mth.floor(centerX + radius);
        var minZ = Mth.floor(centerZ - radius);
        var maxZ = Mth.floor(centerZ + radius);

        for (var x = minX; x <= maxX; x++) {
            for (var z = minZ; z <= maxZ; z++) {
                if (x == feet.getX() && z == feet.getZ()) {
                    continue;
                }
                if (!edgeColumnClear(level, mob, capability, x, feet.getY(), z)) {
                    return false;
                }
            }
        }

        var checkFeet = feet;
        var checkHead = feet.above();

        if (!TraversalQueries.isSafeBlock(level, checkFeet, mob))
            return false;
        if (!TraversalQueries.isSafeBlock(level, checkHead, mob))
            return false;

        var feetState = level.getBlockState(checkFeet);
        var headState = level.getBlockState(checkHead);

        if (
            !feetState.getCollisionShape(level, checkFeet).isEmpty()
                && !capability.isPassableSolid(level, checkFeet, feetState)
        )
            return false;

        if (
            !headState.getCollisionShape(level, checkHead).isEmpty()
                && !capability.isPassableSolid(level, checkHead, headState)
        )
            return false;

        var isInFluid = !feetState.getFluidState().isEmpty();
        var below = feet.below();
        return isInFluid || !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    /**
     * Returns {@code true} if column {@code (x, z)} is clear enough for the edge of a wide mob's footprint to rest
     * there, checking {@code candidateY - 1}, {@code candidateY}, and {@code candidateY + 1} rather than only
     * {@code candidateY} — see {@link #canStandAt}'s class docs for why an edge column needs this tolerance and the
     * center column doesn't.
     */
    private static boolean edgeColumnClear(
        Level level,
        Mob mob,
        MovementCapability capability,
        int x,
        int candidateY,
        int z
    ) {
        for (var dy = -1; dy <= 1; dy++) {
            var checkFeet = new BlockPos(x, candidateY + dy, z);
            var checkHead = checkFeet.above();

            if (!TraversalQueries.isSafeBlock(level, checkFeet, mob))
                continue;
            if (!TraversalQueries.isSafeBlock(level, checkHead, mob))
                continue;

            var feetState = level.getBlockState(checkFeet);
            var headState = level.getBlockState(checkHead);

            var feetOk = feetState.getCollisionShape(level, checkFeet).isEmpty()
                || capability.isPassableSolid(level, checkFeet, feetState);
            var headOk = headState.getCollisionShape(level, checkHead).isEmpty()
                || capability.isPassableSolid(level, checkHead, headState);

            if (feetOk && headOk) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if there is solid geometry adjacent to the given coordinates that a mob could cling to.
     *
     * @param level    the world
     * @param x        block X coordinate
     * @param y        block Y coordinate
     * @param z        block Z coordinate
     * @param generous if {@code true}, uses a larger detection radius (1.5 blocks vs 0.5)
     * @return {@code true} if a climbable surface is nearby
     */
    public static boolean isClimbable(Level level, int x, int y, int z, boolean generous) {
        var reachBox = new AABB(x, y, z, x + 1, y + 1, z + 1).inflate(generous ? 1.5D : 0.5D);
        return !level.noBlockCollision(null, reachBox);
    }

    /**
     * Convenience overload of {@link #isClimbable(Level, int, int, int, boolean)} that accepts a {@link BlockPos}.
     */
    public static boolean isClimbable(Level level, BlockPos pos, boolean generous) {
        return isClimbable(level, pos.getX(), pos.getY(), pos.getZ(), generous);
    }

    /**
     * Returns {@code true} if {@code feet} is a valid node for a wall-crawling mob — both the feet and head positions
     * are safe, neither is blocked by solid geometry (unless capability-passable), and the position is adjacent to a
     * climbable surface.
     *
     * @param level the world
     * @param feet  the candidate feet position
     * @param mob   the mob being evaluated, consulted for {@link MovementCapability}
     * @return {@code true} if the mob can cling at this position
     */
    public static boolean isSafeClimbNode(Level level, BlockPos feet, Mob mob) {
        return isSafeClimbNode(level, feet, mob, null);
    }

    /**
     * Cache-aware variant. The adjacent-surface test is a handful of solidity lookups (routed through {@code cache}
     * when supplied) instead of an inflated-AABB sweep, which dominates pathfinding CPU when done per-candidate. It
     * also does not treat the floor below the node as a cling surface — an air cell resting on solid ground is a walk
     * node, not a climb node — which removes a large amount of spurious climb branching on open terrain.
     *
     * @param level the world
     * @param feet  the candidate feet position
     * @param mob   the mob being evaluated, consulted for {@link MovementCapability}
     * @param cache optional per-pathfind cache for the solidity lookups; may be {@code null}
     * @return {@code true} if the mob can cling at this position
     */
    public static boolean isSafeClimbNode(Level level, BlockPos feet, Mob mob, PathNodeCache cache) {
        var head = feet.above();
        var capability = MovementCapability.of(mob);

        if (!TraversalQueries.isSafeBlock(level, feet, mob))
            return false;
        if (!TraversalQueries.isSafeBlock(level, head, mob))
            return false;

        var feetState = level.getBlockState(feet);
        var headState = level.getBlockState(head);

        if (
            !feetState.getCollisionShape(level, feet).isEmpty()
                && !capability.isPassableSolid(level, feet, feetState)
        )
            return false;

        if (
            !headState.getCollisionShape(level, head).isEmpty()
                && !capability.isPassableSolid(level, head, headState)
        )
            return false;

        return hasAdjacentClingSurface(level, feet, head, cache);
    }

    /**
     * A wall-crawler needs a solid face that spans BOTH feet and head height on the same horizontal side (a genuine
     * 2+-block-tall surface), or an overhead ceiling to grip. Deliberately excludes the floor block, so ordinary ground
     * cells are not misclassified as climb nodes. Requiring the pair (rather than either height independently) also
     * excludes single-block-tall lips, which are ordinary auto-step/fall terrain, not something a mob should glue
     * itself to.
     */
    private static boolean hasAdjacentClingSurface(Level level, BlockPos feet, BlockPos head, PathNodeCache cache) {
        if (solidAt(level, head.above(), cache)) {
            return true;
        }
        return (solidAt(level, feet.north(), cache) && solidAt(level, head.north(), cache))
            || (solidAt(level, feet.south(), cache) && solidAt(level, head.south(), cache))
            || (solidAt(level, feet.east(), cache) && solidAt(level, head.east(), cache))
            || (solidAt(level, feet.west(), cache) && solidAt(level, head.west(), cache));
    }

    /**
     * Returns {@code true} if the block is physically solid for pathfinding purposes — has a non-empty collision shape.
     * Blocks like grass, flowers, and carpet return {@code false} here even though they are not air.
     */
    public static boolean isPhysicallySolid(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Routes a solidity check through the cache when one is available. */
    static boolean solidAt(Level level, BlockPos pos, PathNodeCache cache) {
        return cache != null ? cache.isPhysicallySolid(level, pos) : isPhysicallySolid(level, pos);
    }

    /**
     * Marker accessor so callers outside this package (e.g. the crawl evaluator) can classify a fluid state the same
     * way {@link TraversalQueries#isSafeBlock} would, without duplicating the capability lookup.
     */
    public static boolean isHazardFluid(Level level, BlockPos pos, FluidState fluid, Mob mob) {
        return MovementCapability.of(mob).isHazardFluid(level, pos, fluid);
    }
}
