package com.azure.azurecortex.navigation.astar;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.azure.azurecortex.navigation.traversal.CollisionQueries;

/**
 * Short-lived memoization cache for the expensive per-position classifications used by the pathfinders: physical
 * solidity and full-size walkability.
 * <p>
 * Adjacent A* nodes and per-tick AI checks repeatedly re-classify the same positions. Memoizing by
 * {@link BlockPos#toLong()} collapses those repeats into single block-state/collision-box queries. Custom
 * {@link com.azure.azurecortex.navigation.traversal.TraversalEvaluator}s can route their lookups through this via
 * {@link com.azure.azurecortex.navigation.traversal.TraversalContext#cache()}.
 * <h3>Scope rules</h3> Entries are only valid while the world is unchanged, so a cache must never outlive a single
 * pathfind, scan, or AI tick. Reuse an instance across ticks by calling {@link #clear()} at the start of each tick —
 * the backing maps keep their capacity, so steady-state reuse is allocation-free.
 * <p>
 * Results are also implicitly keyed to one mob (footprint) and one dimension; never share an instance across mobs or
 * worlds without clearing.
 * <h3>Thread safety</h3> Not thread-safe. Server thread only.
 */
@SuppressWarnings("unused")
public final class PathNodeCache {

    private static final byte TRUE = 1;

    private static final byte FALSE = 2;

    /** {@code 0} (the fastutil default return value) means "not cached". */
    private final Long2ByteOpenHashMap solid = new Long2ByteOpenHashMap(256);

    private final Long2ByteOpenHashMap walk = new Long2ByteOpenHashMap(128);

    /** Invalidates all entries while keeping map capacity. Call once per tick / scan / pathfind. */
    public void clear() {
        solid.clear();
        walk.clear();
    }

    /**
     * Invalidates cached classifications for {@code pos} and every position within one block of it in every direction,
     * rather than the whole cache. The classifiers this cache memoizes query positions up to one block away from the
     * one being classified, so a block change at {@code pos} can silently invalidate a neighbor's cached result too.
     * <p>
     * Use this on a long-lived, session-scoped cache when something specific is known to have changed, so the rest of
     * an in-progress search stays warm instead of being thrown away wholesale via {@link #clear()}.
     */
    public void invalidate(BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long key = pos.add(dx, dy, dz).toLong();
                    solid.remove(key);
                    walk.remove(key);
                }
            }
        }
    }

    /** Memoized {@link CollisionQueries#isPhysicallySolid}. */
    public boolean isPhysicallySolid(World world, BlockPos pos) {
        long key = pos.toLong();
        byte cached = solid.get(key);
        if (cached != 0) {
            return cached == TRUE;
        }
        boolean result = CollisionQueries.isPhysicallySolid(world, pos);
        solid.put(key, result ? TRUE : FALSE);
        return result;
    }

    /** Memoized {@link AStarPathfinder#canStandAt} (full-size footprint walkability). */
    public boolean canStandAt(World world, EntityLiving mob, BlockPos pos) {
        long key = pos.toLong();
        byte cached = walk.get(key);
        if (cached != 0) {
            return cached == TRUE;
        }
        boolean result = AStarPathfinder.canStandAt(world, mob, pos);
        walk.put(key, result ? TRUE : FALSE);
        return result;
    }
}
