package com.azure.azurecortex.navigation.astar;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.azure.azurecortex.api.navigation.Pathfinder;
import com.azure.azurecortex.config.CortexConfig;
import com.azure.azurecortex.navigation.traversal.CollisionQueries;
import com.azure.azurecortex.navigation.traversal.TraversalContext;
import com.azure.azurecortex.navigation.traversal.TraversalEvaluator;
import com.azure.azurecortex.navigation.traversal.TraversalQueries;
import com.azure.azurecortex.runtime.CortexDebug;

/**
 * A grid-based A* pathfinder for ground-walking mobs.
 * <p>
 * Searches up to 2 000 nodes before returning the best partial path found. This is the plain ground-walking movement
 * model; see {@code com.azure.azurecortex.navigation.crawl.CrawlTraversalEvaluator} for the wall-crawl-aware variant.
 * <p>
 * All path results are lists of foot-level {@link BlockPos} waypoints.
 */
public final class AStarPathfinder implements Pathfinder, TraversalEvaluator {

    /** Shared stateless instance — this evaluator holds no per-search state of its own. */
    public static final AStarPathfinder INSTANCE = new AStarPathfinder();

    @Override
    public List<BlockPos> findPath(Mob mob, BlockPos start, BlockPos goal, int maxRange, int goalRadius) {
        var level = mob.level;
        var context = TraversalContext.of(level, mob);

        var open = new PriorityQueue<>(Comparator.comparingDouble(AStarNode::f));
        Map<BlockPos, Double> bestCost = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        var startFeet = normalizeFeet(start);
        var goalFeet = normalizeFeet(goal);

        open.add(new AStarNode(startFeet, 0.0D, heuristic(startFeet, goalFeet), null));
        bestCost.put(startFeet, 0.0D);

        var searched = 0;
        var maxSearched = 2000;
        AStarNode bestPartial = null;
        var bestPartialScore = Double.MAX_VALUE;

        while (!open.isEmpty() && searched++ < maxSearched) {
            var current = open.poll();

            var partialScore = heuristic(current.pos(), goalFeet);

            if (partialScore < bestPartialScore && !isSolidlySeparatedVertically(level, current.pos(), goalFeet)) {
                bestPartialScore = partialScore;
                bestPartial = current;
            }

            if (!closed.add(current.pos())) {
                continue;
            }

            if (
                isCloseEnoughToGoal(current.pos(), goalFeet, goalRadius)
                    && !isSolidlySeparatedVertically(level, current.pos(), goalFeet)
            ) {
                var path = AStarNode.reconstruct(current);
                debugPath(mob, path);
                return path;
            }

            for (var next : neighbors(context, current.pos(), goalFeet)) {
                if (closed.contains(next)) {
                    continue;
                }

                if (next.distManhattan(startFeet) > maxRange) {
                    continue;
                }

                var stepCost = cost(context, current.pos(), next);

                if (stepCost >= 9999.0D) {
                    continue;
                }

                var newG = current.g() + stepCost;
                var oldG = bestCost.getOrDefault(next, Double.MAX_VALUE);

                if (newG < oldG) {
                    bestCost.put(next, newG);
                    var f = newG + heuristic(next, goalFeet);
                    open.add(new AStarNode(next, newG, f, current));
                }
            }
        }

        if (bestPartial != null && bestPartial.parent() != null) {
            var path = AStarNode.reconstruct(bestPartial);
            debugPath(mob, path);
            return path;
        }

        return Collections.emptyList();
    }

    private void debugPath(Mob mob, List<BlockPos> path) {
        if (!CortexConfig.get().enablePathfindingDebug)
            return;
        for (var i = 0; i < path.size() - 1; i++) {
            CortexDebug.sendParticlePath(mob, Vec3.atCenterOf(path.get(i)), Vec3.atCenterOf(path.get(i + 1)));
        }
    }

    @Override
    public double heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX())
            + Math.abs(a.getY() - b.getY()) * 1.5D
            + Math.abs(a.getZ() - b.getZ());
    }

    @Override
    public List<BlockPos> neighbors(TraversalContext context, BlockPos pos, BlockPos goal) {
        return neighbors(context.level(), context.mob(), pos);
    }

    /**
     * Returns the walkable neighbor positions reachable from {@code pos} for a ground-walking mob. Considers one block
     * up and up to three blocks down in each cardinal direction.
     */
    public List<BlockPos> neighbors(Level level, Mob mob, BlockPos pos) {
        List<BlockPos> result = new ArrayList<>();

        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        for (var dir : dirs) {
            var base = pos.offset(dir[0], 0, dir[1]);

            tryAdd(level, mob, result, base);
            tryAdd(level, mob, result, base.above());

            for (var drop = 1; drop <= 3; drop++) {
                tryAdd(level, mob, result, base.below(drop));
            }
        }

        return result;
    }

    private static void tryAdd(Level level, Mob mob, List<BlockPos> result, BlockPos feet) {
        if (canStandAt(level, mob, feet)) {
            result.add(feet);
        }
    }

    /**
     * Returns {@code true} if the mob can stand at {@code feet} — delegates to {@link CollisionQueries#canStandAt}.
     */
    public static boolean canStandAt(Level level, Mob mob, BlockPos feet) {
        return CollisionQueries.canStandAt(level, mob, feet);
    }

    @Override
    public double cost(TraversalContext context, BlockPos from, BlockPos to) {
        return movementCost(context.level(), context.mob(), from, to);
    }

    /**
     * Computes the movement cost of stepping from {@code from} to {@code to}.
     * <p>
     * Returns {@code 9999.0} (effectively impassable) if the destination is unsafe. Climbing adds 1.5 to cost;
     * descending adds 0.5. Adjacent hazardous positions add a 4-block penalty each to discourage walking near them.
     */
    public double movementCost(Level level, Mob mob, BlockPos from, BlockPos to) {
        if (!TraversalQueries.isSafeBlock(level, to, mob)) {
            return 9999.0D;
        }

        var toState = level.getBlockState(to);
        var inFluid = !toState.getFluidState().isEmpty();
        if (!inFluid && !TraversalQueries.isSafeBlock(level, to.below(), mob)) {
            return 9999.0D;
        }

        var cost = 1.0D;

        var dy = to.getY() - from.getY();

        if (inFluid) {
            cost += 2.0D;
        }

        if (dy > 0) {
            cost += 1.5D;
        } else if (dy < 0) {
            cost += 0.5D;
        }

        var dangerPaddingBlocks = Math.max(1, Mth.ceil(mob.getBbWidth() / 2.0D));

        for (
            var near : BlockPos.betweenClosed(
                to.offset(-dangerPaddingBlocks, -1, -dangerPaddingBlocks),
                to.offset(dangerPaddingBlocks, 1, dangerPaddingBlocks)
            )
        ) {
            if (!TraversalQueries.isSafeBlock(level, near, mob)) {
                cost += 4.0D;
            }
        }

        return cost;
    }
}
