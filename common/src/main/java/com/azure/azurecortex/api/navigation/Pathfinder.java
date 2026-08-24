package com.azure.azurecortex.api.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * The public entry point for finding a path between two positions.
 * <p>
 * This is a thin, synchronous-facing facade over {@code com.azure.azurecortex.navigation.astar.AStarPathfinder} (plain
 * ground movement) and {@code com.azure.azurecortex.navigation.crawl.CrawlTraversalEvaluator} (wall-crawl-aware
 * movement). Callers that need to spread search cost across ticks instead of paying it synchronously should use
 * {@code com.azure.azurecortex.navigation.astar.IncrementalPathSession} /
 * {@code com.azure.azurecortex.navigation.astar.PhasedPathSession} directly rather than this interface.
 *
 * @see com.azure.azurecortex.navigation.astar.AStarPathfinder
 */
public interface Pathfinder {

    /**
     * Runs a synchronous search from {@code start} to {@code goal} and returns an ordered list of waypoints.
     * <p>
     * Returns the best partial path (closest to the goal) when the full path cannot be found within the
     * implementation's search budget.
     *
     * @param mob        the mob to path for (used for footprint, capability, and safety checks)
     * @param start      the starting block position
     * @param goal       the target block position
     * @param maxRange   maximum Manhattan distance from {@code start} any node may be
     * @param goalRadius horizontal radius (blocks) within which the goal is considered reached
     * @return the ordered path as foot-level positions, or an empty list if no path was found
     */
    List<BlockPos> findPath(Mob mob, BlockPos start, BlockPos goal, int maxRange, int goalRadius);
}
