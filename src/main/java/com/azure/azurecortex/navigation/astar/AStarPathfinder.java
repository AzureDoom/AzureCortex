package com.azure.azurecortex.navigation.astar;

import net.minecraft.entity.EntityLiving;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

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
import com.azure.azurecortex.util.McCompat;

/**
 * A grid-based A* pathfinder for ground-walking mobs.
 * <p>
 * Searches up to 2 000 nodes before returning the best partial path found.
 * <p>
 * All path results are lists of foot-level {@link BlockPos} waypoints.
 */
public final class AStarPathfinder implements Pathfinder, TraversalEvaluator {

    /** Shared stateless instance — this evaluator holds no per-search state of its own. */
    public static final AStarPathfinder INSTANCE = new AStarPathfinder();

    private static final int[][] CARDINALS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    @Override
    public List<BlockPos> findPath(EntityLiving mob, BlockPos start, BlockPos goal, int maxRange, int goalRadius) {
        World world = mob.world;
        TraversalContext context = TraversalContext.of(world, mob);

        PriorityQueue<AStarNode> open = new PriorityQueue<>(Comparator.comparingDouble(AStarNode::f));
        Map<BlockPos, Double> bestCost = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        BlockPos startFeet = normalizeFeet(start);
        BlockPos goalFeet = normalizeFeet(goal);

        open.add(new AStarNode(startFeet, 0.0D, heuristic(startFeet, goalFeet), null));
        bestCost.put(startFeet, 0.0D);

        int searched = 0;
        int maxSearched = 2000;
        AStarNode bestPartial = null;
        double bestPartialScore = Double.MAX_VALUE;

        while (!open.isEmpty() && searched++ < maxSearched) {
            AStarNode current = open.poll();

            double partialScore = heuristic(current.pos(), goalFeet);

            if (partialScore < bestPartialScore) {
                bestPartialScore = partialScore;
                bestPartial = current;
            }

            if (!closed.add(current.pos())) {
                continue;
            }

            if (
                isCloseEnoughToGoal(current.pos(), goalFeet, goalRadius)
                    && !isSolidlySeparatedVertically(world, current.pos(), goalFeet)
            ) {
                List<BlockPos> path = AStarNode.reconstruct(current);
                debugPath(mob, path);
                return path;
            }

            for (BlockPos next : neighbors(context, current.pos(), goalFeet)) {
                if (closed.contains(next)) {
                    continue;
                }

                if (McCompat.manhattan(next, startFeet) > maxRange) {
                    continue;
                }

                double stepCost = cost(context, current.pos(), next);

                if (stepCost >= 9999.0D) {
                    continue;
                }

                double newG = current.g() + stepCost;
                double oldG = bestCost.getOrDefault(next, Double.MAX_VALUE);

                if (newG < oldG) {
                    bestCost.put(next, newG);
                    double f = newG + heuristic(next, goalFeet);
                    open.add(new AStarNode(next, newG, f, current));
                }
            }
        }

        if (bestPartial != null && bestPartial.parent() != null) {
            List<BlockPos> path = AStarNode.reconstruct(bestPartial);
            debugPath(mob, path);
            return path;
        }

        return Collections.emptyList();
    }

    private void debugPath(EntityLiving mob, List<BlockPos> path) {
        if (!CortexConfig.get().enablePathfindingDebug)
            return;
        for (int i = 0; i < path.size() - 1; i++) {
            CortexDebug.sendParticlePath(mob, McCompat.atCenterOf(path.get(i)), McCompat.atCenterOf(path.get(i + 1)));
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
        return neighbors(context.world(), context.mob(), pos);
    }

    /**
     * Returns the walkable neighbor positions reachable from {@code pos} for a ground-walking mob. Considers one block
     * up and up to three blocks down in each cardinal direction.
     */
    public List<BlockPos> neighbors(World world, EntityLiving mob, BlockPos pos) {
        List<BlockPos> result = new ArrayList<>();

        for (int[] dir : CARDINALS) {
            BlockPos base = pos.add(dir[0], 0, dir[1]);

            tryAdd(world, mob, result, base);
            tryAdd(world, mob, result, base.up());

            for (int drop = 1; drop <= 3; drop++) {
                tryAdd(world, mob, result, base.down(drop));
            }
        }

        return result;
    }

    private static void tryAdd(World world, EntityLiving mob, List<BlockPos> result, BlockPos feet) {
        if (canStandAt(world, mob, feet)) {
            result.add(feet);
        }
    }

    /**
     * Returns {@code true} if the mob can stand at {@code feet} — delegates to {@link CollisionQueries#canStandAt}.
     */
    public static boolean canStandAt(World world, EntityLiving mob, BlockPos feet) {
        return CollisionQueries.canStandAt(world, mob, feet);
    }

    @Override
    public double cost(TraversalContext context, BlockPos from, BlockPos to) {
        return movementCost(context.world(), context.mob(), from, to);
    }

    /**
     * Computes the movement cost of stepping from {@code from} to {@code to}.
     * <p>
     * Returns {@code 9999.0} (effectively impassable) if the destination is unsafe. Climbing adds 1.5 to cost;
     * descending adds 0.5. Adjacent hazardous positions add a 4-block penalty each to discourage walking near them.
     */
    public double movementCost(World world, EntityLiving mob, BlockPos from, BlockPos to) {
        if (!TraversalQueries.isSafeBlock(world, to, mob)) {
            return 9999.0D;
        }

        boolean inFluid = McCompat.isLiquid(world.getBlockState(to));
        if (!inFluid && !TraversalQueries.isSafeBlock(world, to.down(), mob)) {
            return 9999.0D;
        }

        double cost = 1.0D;

        int dy = to.getY() - from.getY();

        if (inFluid) {
            cost += 2.0D;
        }

        if (dy > 0) {
            cost += 1.5D;
        } else if (dy < 0) {
            cost += 0.5D;
        }

        int dangerPaddingBlocks = Math.max(1, MathHelper.ceil(mob.width / 2.0D));

        for (
            BlockPos near : BlockPos.getAllInBoxMutable(
                to.add(-dangerPaddingBlocks, -1, -dangerPaddingBlocks),
                to.add(dangerPaddingBlocks, 1, dangerPaddingBlocks)
            )
        ) {
            if (!TraversalQueries.isSafeBlock(world, near, mob)) {
                cost += 4.0D;
            }
        }

        return cost;
    }
}
