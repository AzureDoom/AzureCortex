package com.azure.azurecortex.navigation.traversal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Supplies the three functions an A*-style search needs to know about movement: which positions are reachable from
 * where, what it costs to move between them, and how to estimate the remaining distance to a goal.
 * <p>
 * The core search loop ({@code com.azure.azurecortex.navigation.astar.IncrementalPathSession}/
 * {@code PhasedPathSession}) is identical regardless of movement model — what differs between a ground-walking mob and
 * a wall-crawling one is only what this interface returns.
 * {@code com.azure.azurecortex.navigation.astar.AStarPathfinder} implements the plain ground-walking model;
 * {@code com.azure.azurecortex.navigation.crawl.CrawlTraversalEvaluator} implements the crawl-aware one. A caller with
 * a genuinely different movement model (a flying mob, say) can implement this directly and get the same incremental
 * search machinery for free.
 */
public interface TraversalEvaluator {

    /**
     * Returns the positions reachable from {@code pos} toward {@code goal}.
     *
     * @param context the search's world/mob/capability/cache bundle
     * @param pos     the position to expand from
     * @param goal    the overall search goal, made available so implementations can prune direction-dependent
     *                candidates (e.g. skip upward branches once the goal is known to be below)
     */
    List<BlockPos> neighbors(TraversalContext context, BlockPos pos, BlockPos goal);

    /**
     * Returns the cost of moving from {@code from} to {@code to}. A value {@code >= 9999.0} means the step is
     * impassable.
     */
    double cost(TraversalContext context, BlockPos from, BlockPos to);

    /**
     * Returns the estimated remaining cost between two positions (must never overestimate the true cost, per standard
     * A* admissibility, though in practice these implementations trade strict admissibility for a bias against
     * unnecessary vertical movement).
     */
    double heuristic(BlockPos a, BlockPos b);

    /**
     * Normalizes a position to this evaluator's canonical "foot level" reference point. Most implementations return the
     * position unchanged; present as a hook for movement models with a different notion of foot position.
     */
    default BlockPos normalizeFeet(BlockPos pos) {
        return pos;
    }

    /**
     * Returns {@code true} if solid geometry sits between {@code pos} and {@code goal} in the goal's vertical column,
     * used to reject "arrived" states that are actually just directly beneath/above the real goal separated by a
     * floor/ceiling.
     */
    default boolean isSolidlySeparatedVertically(Level level, BlockPos pos, BlockPos goal) {
        if (pos.getY() == goal.getY()) {
            return false;
        }
        var loY = Math.min(pos.getY(), goal.getY());
        var hiY = Math.max(pos.getY(), goal.getY());
        for (var y = loY; y < hiY; y++) {
            var check = new BlockPos(goal.getX(), y, goal.getZ());
            if (!level.getBlockState(check).getCollisionShape(level, check).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code pos} is within the goal acceptance zone — within {@code goalRadius} blocks
     * horizontally and within two blocks vertically of {@code goal}.
     */
    default boolean isCloseEnoughToGoal(BlockPos pos, BlockPos goal, int goalRadius) {
        var dx = pos.getX() - goal.getX();
        var dz = pos.getZ() - goal.getZ();

        return dx * dx + dz * dz <= goalRadius * goalRadius
            && Math.abs(pos.getY() - goal.getY()) <= 2;
    }
}
