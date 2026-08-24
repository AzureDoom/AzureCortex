package com.azure.azurecortex.navigation.astar;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import com.azure.azurecortex.navigation.crawl.CrawlTraversalEvaluator;
import com.azure.azurecortex.navigation.traversal.CollisionQueries;

/**
 * Short-lived memoization cache for the expensive per-position classifications used by the pathfinders: physical
 * solidity, full-size walkability, tunnel-crawl fit, vertical-shaft fit, and climb nodes.
 * <p>
 * Adjacent A* nodes and per-tick AI checks repeatedly re-classify the same positions — a tight-tunnel check alone
 * queries eight neighbors per candidate, so two adjacent candidates share most of their lookups. Memorizing by
 * {@link BlockPos#asLong()} collapses those repeats into single block-state/collision-shape queries.
 * <h3>Scope rules</h3> Entries are only valid while the world is unchanged, so a cache must never outlive a single
 * pathfind, scan, or AI tick. Reuse an instance across ticks by calling {@link #clear()} at the start of each tick —
 * the backing maps keep their capacity, so steady-state reuse is allocation-free.
 * <p>
 * Results are also implicitly keyed to one mob (crawl height, footprint) and one dimension; never share an instance
 * across mobs or levels without clearing.
 * <h3>Thread safety</h3> Not thread-safe. Server thread only.
 */
@SuppressWarnings("unused")
public final class PathNodeCache {

    private static final byte TRUE = 1;

    private static final byte FALSE = 2;

    /** {@code 0} (the fastutil default return value) means "not cached". */
    private final Long2ByteOpenHashMap solid = new Long2ByteOpenHashMap(256);

    private final Long2ByteOpenHashMap walk = new Long2ByteOpenHashMap(128);

    private final Long2ByteOpenHashMap tunnel = new Long2ByteOpenHashMap(128);

    private final Long2ByteOpenHashMap shaft = new Long2ByteOpenHashMap(128);

    private final Long2ByteOpenHashMap climb = new Long2ByteOpenHashMap(128);

    /** Invalidates all entries while keeping map capacity. Call once per tick / scan / pathfind. */
    public void clear() {
        solid.clear();
        walk.clear();
        tunnel.clear();
        shaft.clear();
        climb.clear();
    }

    /**
     * Invalidates cached classifications for {@code pos} and every position within one block of it in every direction,
     * rather than the whole cache. The classifiers this cache memorizes all query positions up to one block away from
     * the one being classified, so a block change at {@code pos} can silently invalidate a neighbor's cached result
     * too.
     * <p>
     * Use this on a long-lived, session-scoped cache when something specific is known to have changed, so the rest of
     * an in-progress search stays warm instead of being thrown away wholesale via {@link #clear()}.
     */
    public void invalidate(BlockPos pos) {
        for (var dx = -1; dx <= 1; dx++) {
            for (var dy = -1; dy <= 1; dy++) {
                for (var dz = -1; dz <= 1; dz++) {
                    var key = pos.offset(dx, dy, dz).asLong();
                    solid.remove(key);
                    walk.remove(key);
                    tunnel.remove(key);
                    shaft.remove(key);
                    climb.remove(key);
                }
            }
        }
    }

    /** Memoized {@link CollisionQueries#isPhysicallySolid}. */
    public boolean isPhysicallySolid(Level level, BlockPos pos) {
        var key = pos.asLong();
        var cached = solid.get(key);
        if (cached != 0) {
            return cached == TRUE;
        }
        var result = CollisionQueries.isPhysicallySolid(level, pos);
        solid.put(key, result ? TRUE : FALSE);
        return result;
    }

    /** Memoized {@link AStarPathfinder#canStandAt} (full-size footprint walkability). */
    public boolean canStandAt(Level level, Mob mob, BlockPos pos) {
        var key = pos.asLong();
        var cached = walk.get(key);
        if (cached != 0) {
            return cached == TRUE;
        }
        var result = AStarPathfinder.canStandAt(level, mob, pos);
        walk.put(key, result ? TRUE : FALSE);
        return result;
    }

    /** Memoized {@link CrawlTraversalEvaluator#tunnelCanStandAt}. Passes itself down to memoize inner lookups. */
    public boolean tunnelCanStandAt(Level level, Mob mob, BlockPos pos) {
        var key = pos.asLong();
        var cached = tunnel.get(key);
        if (cached != 0) {
            return cached == TRUE;
        }
        var result = CrawlTraversalEvaluator.tunnelCanStandAt(level, mob, pos, this);
        tunnel.put(key, result ? TRUE : FALSE);
        return result;
    }

    /** Memoized {@link CrawlTraversalEvaluator#verticalShaftCanCrawlAt}. Passes itself down for inner lookups. */
    public boolean verticalShaftCanCrawlAt(Level level, Mob mob, BlockPos pos) {
        var key = pos.asLong();
        var cached = shaft.get(key);
        if (cached != 0) {
            return cached == TRUE;
        }
        var result = CrawlTraversalEvaluator.verticalShaftCanCrawlAt(level, mob, pos, this);
        shaft.put(key, result ? TRUE : FALSE);
        return result;
    }

    /** Memoized {@link CollisionQueries#isSafeClimbNode(Level, BlockPos, Mob, PathNodeCache)}. */
    public boolean isSafeClimbNode(Level level, BlockPos pos, Mob mob) {
        var key = pos.asLong();
        var cached = climb.get(key);
        if (cached != 0) {
            return cached == TRUE;
        }
        var result = CollisionQueries.isSafeClimbNode(level, pos, mob, this);
        climb.put(key, result ? TRUE : FALSE);
        return result;
    }
}
