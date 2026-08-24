package com.azure.azurecortex.navigation.crawl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
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

import com.azure.azurecortex.api.navigation.MovementCapability;
import com.azure.azurecortex.api.navigation.Pathfinder;
import com.azure.azurecortex.config.CortexConfig;
import com.azure.azurecortex.navigation.astar.AStarNode;
import com.azure.azurecortex.navigation.astar.PathNodeCache;
import com.azure.azurecortex.navigation.traversal.CollisionQueries;
import com.azure.azurecortex.navigation.traversal.TraversalContext;
import com.azure.azurecortex.navigation.traversal.TraversalEvaluator;
import com.azure.azurecortex.navigation.traversal.TraversalQueries;

/**
 * A wall-crawl-aware A* pathfinder: the generalization of the historical Ovomorphosis {@code CrawlingCustomAStar}.
 * <p>
 * Understands three kinds of node beyond plain ground-walking: tight-tunnel crawling (a mob squeezed into a confined
 * space), vertical-shaft climbing, and surface-cling climb nodes reached via {@link CrawlCapability}. Everything that
 * used to be hard-coded to Ovomorphosis's resin tag is now routed through {@link MovementCapability#isPassableSolid}.
 */
@SuppressWarnings("unused")
public final class CrawlTraversalEvaluator implements Pathfinder, TraversalEvaluator {

    /** Shared stateless instance. */
    public static final CrawlTraversalEvaluator INSTANCE = new CrawlTraversalEvaluator();

    @Override
    public List<BlockPos> findPath(Mob mob, BlockPos start, BlockPos goal, int maxRange, int goalRadius) {
        return findPath(mob, start, goal, maxRange, goalRadius, new PathNodeCache());
    }

    /**
     * Variant taking the caller's {@link PathNodeCache}. A* revisits the same positions constantly; the cache collapses
     * those repeats. Callers that run a pathfind inside a larger per-tick scope should pass their per-tick cache so
     * warm entries are shared.
     */
    public List<BlockPos> findPath(
        Mob mob,
        BlockPos start,
        BlockPos goal,
        int maxRange,
        int goalRadius,
        PathNodeCache cache
    ) {
        var level = mob.level();
        var context = TraversalContext.of(level, mob, cache);

        var open = new PriorityQueue<>(Comparator.comparingDouble(AStarNode::f));
        Map<BlockPos, Double> bestCost = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        var startFeet = normalizeFeet(start);
        var goalFeet = normalizeFeet(goal);

        open.add(new AStarNode(startFeet, 0.0D, heuristic(startFeet, goalFeet), null));
        bestCost.put(startFeet, 0.0D);

        var searched = 0;
        var maxSearched = 6000;
        AStarNode bestPartial = null;
        var bestPartialScore = Double.MAX_VALUE;

        var effectiveRange = Math.min(maxRange, 48);

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
                var fullPath = filterTransitionNodes(AStarNode.reconstruct(current), level, mob, cache);
                debugParticlePath(mob, fullPath, true);
                return fullPath;
            }

            for (var next : neighbors(context, current.pos(), goalFeet)) {
                if (closed.contains(next)) {
                    continue;
                }

                if (next.distManhattan(startFeet) > effectiveRange) {
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
            var partialPath = filterTransitionNodes(AStarNode.reconstruct(bestPartial), level, mob, cache);
            debugParticlePath(mob, partialPath, false);
            return partialPath;
        }

        return Collections.emptyList();
    }

    /**
     * Package-visible (not {@code private}) so {@code IncrementalPathSession} can apply the same post-processing to
     * paths assembled incrementally across several ticks as {@link #findPath} applies to a path found in one
     * synchronous call.
     */
    public static List<BlockPos> filterTransitionNodes(
        List<BlockPos> path,
        Level level,
        Mob mob,
        PathNodeCache cache
    ) {
        if (path.size() <= 2)
            return path;
        var filtered = new ArrayList<BlockPos>();
        for (var i = 0; i < path.size(); i++) {
            var pos = path.get(i);
            var isWalk = cache.canStandAt(level, mob, pos);
            var isClimb = cache.isSafeClimbNode(level, pos, mob);
            var isTunnel = cache.tunnelCanStandAt(level, mob, pos);
            var isFluid = !level.getBlockState(pos).getFluidState().isEmpty();
            if (i == path.size() - 1 || isWalk || isClimb || isTunnel || isFluid) {
                filtered.add(pos);
            }
        }
        if (filtered.isEmpty() && !path.isEmpty()) {
            filtered.add(path.getLast());
        }
        return filtered;
    }

    /** Package-visible so {@code IncrementalPathSession} can reuse it for consistent debug visualization. */
    public static void debugParticlePath(Mob mob, List<BlockPos> path, boolean fullPath) {
        if (!(mob.level() instanceof ServerLevel serverLevel))
            return;
        if (path.isEmpty())
            return;
        if (!CortexConfig.get().enablePathfindingDebug)
            return;

        var level = mob.level();

        for (var pos : path) {
            var cx = pos.getX() + 0.5D;
            var cy = pos.getY() + 0.5D;
            var cz = pos.getZ() + 0.5D;

            var isClimb = CollisionQueries.isSafeClimbNode(level, pos, mob);
            var isWalk = CollisionQueries.canStandAt(level, mob, pos);
            var isFluid = !level.getBlockState(pos).getFluidState().isEmpty();

            // BLUE = climb node, YELLOW = walk node, WHITE = both/unknown
            var markerParticle = isClimb && !isWalk
                ? ParticleTypes.DRIPPING_WATER
                : isWalk && !isClimb
                    ? ParticleTypes.FLAME
                    : isFluid
                        ? ParticleTypes.BUBBLE
                        : ParticleTypes.END_ROD;

            serverLevel.sendParticles(markerParticle, cx, cy, cz, 3, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        for (var i = 0; i < path.size() - 1; i++) {
            var a = path.get(i);
            var b = path.get(i + 1);
            var ax = a.getX() + 0.5D;
            var ay = a.getY() + 0.5D;
            var az = a.getZ() + 0.5D;
            var bx = b.getX() + 0.5D;
            var by = b.getY() + 0.5D;
            var bz = b.getZ() + 0.5D;
            for (var s = 0; s <= 4; s++) {
                var t = s / 4.0D;
                serverLevel.sendParticles(
                    fullPath ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SMOKE,
                    ax + (bx - ax) * t,
                    ay + (by - ay) * t,
                    az + (bz - az) * t,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
                );
            }
        }
    }

    @Override
    public List<BlockPos> neighbors(TraversalContext context, BlockPos pos, BlockPos goal) {
        return neighbors(
            context.level(),
            context.mob(),
            pos,
            goal,
            context.cache() != null ? context.cache() : new PathNodeCache()
        );
    }

    public List<BlockPos> neighbors(Level level, Mob mob, BlockPos pos, BlockPos goal) {
        return neighbors(level, mob, pos, goal, new PathNodeCache());
    }

    public List<BlockPos> neighbors(Level level, Mob mob, BlockPos pos, BlockPos goal, PathNodeCache cache) {
        List<BlockPos> result = new ArrayList<>();
        var goalIsBelow = goal.getY() < pos.getY() - 1;

        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        for (var dir : dirs) {
            var base = pos.offset(dir[0], 0, dir[1]);

            tryAddWalk(level, mob, result, base, cache);
            tryAddFluid(level, mob, result, base);

            if (!goalIsBelow) {
                tryAddWalk(level, mob, result, base.above(), cache);
                tryAddFluid(level, mob, result, base.above());
            }

            for (var drop = 1; drop <= 3; drop++) {
                tryAddWalk(level, mob, result, base.below(drop), cache);
                tryAddFluid(level, mob, result, base.below(drop));
            }

            tryAddTunnelWalk(level, mob, result, base, cache);

            if (!goalIsBelow) {
                tryAddTunnelWalk(level, mob, result, base.above(), cache);
            }

            for (var drop = 1; drop <= 3; drop++) {
                tryAddTunnelWalk(level, mob, result, base.below(drop), cache);
            }
        }

        for (var drop = 1; drop <= 8; drop++) {
            var candidate = pos.below(drop);
            if (!level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty())
                break;
            if (cache.tunnelCanStandAt(level, mob, candidate) && !result.contains(candidate)) {
                result.add(candidate);
                break;
            }
        }

        if (
            CrawlController.canWallCrawl(mob)
                && (cache.tunnelCanStandAt(level, mob, pos) || hasClimbSurfaceNearby(level, pos, cache))
        ) {
            var posIsTunnel = cache.tunnelCanStandAt(level, mob, pos);
            for (var dir : Direction.values()) {
                var next = pos.relative(dir);

                if (goalIsBelow && next.getY() > pos.getY()) {
                    continue;
                }

                tryAddClimb(level, mob, result, next, cache);
            }

            if (!goalIsBelow && cache.verticalShaftCanCrawlAt(level, mob, pos)) {
                var shaftUp = pos.above();
                if (!result.contains(shaftUp)) {
                    result.add(shaftUp);
                }
            }

            int[][] hDirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

            if (!posIsTunnel && !goalIsBelow) {
                for (var rise = 1; rise <= 2; rise++) {
                    var candidate = pos.above(rise);
                    if (
                        cache.isSafeClimbNode(level, candidate, mob) && hasClimbClearance(level, mob, candidate, cache)
                    ) {
                        result.add(candidate);
                        break;
                    }
                }

                for (var dir : hDirs) {
                    var side = pos.offset(dir[0], 0, dir[1]);
                    for (var rise = 1; rise <= 2; rise++) {
                        var candidate = side.above(rise);
                        if (
                            cache.isSafeClimbNode(level, candidate, mob)
                                && hasClimbClearance(level, mob, candidate, cache)
                        ) {
                            result.add(candidate);
                            break;
                        }
                    }
                }
            }

            for (var dir : hDirs) {
                var side = pos.offset(dir[0], 0, dir[1]);
                for (var drop = 1; drop <= 2; drop++) {
                    var candidate = side.below(drop);
                    if (
                        cache.isSafeClimbNode(level, candidate, mob) && hasClimbClearance(level, mob, candidate, cache)
                    ) {
                        result.add(candidate);
                        break;
                    }
                }
            }
        }

        return result;
    }

    private static void tryAddFluid(Level level, Mob mob, List<BlockPos> result, BlockPos feet) {
        var state = level.getBlockState(feet);
        if (state.getFluidState().isEmpty())
            return;
        if (!TraversalQueries.isSafeBlock(level, feet, mob))
            return;
        var head = feet.above();
        if (!TraversalQueries.isSafeBlock(level, head, mob))
            return;
        if (!result.contains(feet))
            result.add(feet);
    }

    private static void tryAddTunnelWalk(
        Level level,
        Mob mob,
        List<BlockPos> result,
        BlockPos feet,
        PathNodeCache cache
    ) {
        if (!result.contains(feet) && cache.tunnelCanStandAt(level, mob, feet)) {
            result.add(feet);
        }
    }

    private static float getEffectiveCrawlHeight(Mob mob) {
        if (mob instanceof CrawlCapability) {
            return Math.min(mob.getBbWidth(), mob.getBbHeight());
        }
        return mob.getBbHeight();
    }

    /**
     * Half-width of the mob's crawl footprint. Tall mobs squeeze to a slim {@code slimCap} profile when crawling; mobs
     * that are wider than they are tall keep their real half-width so the pathfinder does not route them through gaps
     * narrower than they actually are.
     */
    private static double crawlHalfWidth(Mob mob, double slimCap) {
        var halfW = mob.getBbWidth() / 2.0D;
        return mob.getBbWidth() > mob.getBbHeight() ? halfW : Math.min(halfW, slimCap);
    }

    private static boolean isPassableForCrawl(Level level, BlockPos pos, Mob mob) {
        var state = level.getBlockState(pos);

        if (state.is(Blocks.BARRIER))
            return true;
        if (state.is(Blocks.STRUCTURE_VOID))
            return true;
        if (MovementCapability.of(mob).isPassableSolid(level, pos, state))
            return true;

        return state.getCollisionShape(level, pos).isEmpty();
    }

    public static boolean tunnelCanStandAt(Level level, Mob mob, BlockPos feet) {
        return tunnelCanStandAt(level, mob, feet, null);
    }

    /**
     * Variant taking an optional {@link PathNodeCache} to memoize the solidity lookups shared with neighboring
     * candidates. Checks are ordered cheapest-rejection-first: feet passability (1 query), then ground solidity (1
     * query), then confinement, clearance, and finally the AABB collision sweep.
     */
    public static boolean tunnelCanStandAt(Level level, Mob mob, BlockPos feet, PathNodeCache cache) {
        if (!isPassableForCrawl(level, feet, mob))
            return false;

        if (!solidAt(level, feet.below(), cache))
            return false;

        if (!isTightTunnel(level, feet, cache))
            return false;

        var crawlHeight = getEffectiveCrawlHeight(mob);
        var blocksOccupied = Math.max(1, (int) Math.ceil(crawlHeight));

        for (var dy = 0; dy < blocksOccupied; dy++) {
            var check = feet.above(dy);
            if (!isPassableForCrawl(level, check, mob))
                return false;
            var state = level.getBlockState(check);
            if (
                !state.is(Blocks.BARRIER)
                    && !state.is(Blocks.STRUCTURE_VOID)
                    && !TraversalQueries.isSafeBlock(level, check, mob)
            )
                return false;
        }

        var testHalfW = crawlHalfWidth(mob, 0.3D);
        var mobBox = new AABB(
            feet.getX() + 0.5D - testHalfW,
            feet.getY(),
            feet.getZ() + 0.5D - testHalfW,
            feet.getX() + 0.5D + testHalfW,
            feet.getY() + crawlHeight,
            feet.getZ() + 0.5D + testHalfW
        );
        return level.noCollision(mob, mobBox);
    }

    @Override
    public double heuristic(BlockPos a, BlockPos b) {
        var dx = Math.abs(a.getX() - b.getX());
        var dz = Math.abs(a.getZ() - b.getZ());
        var rawDy = b.getY() - a.getY();
        var yPenalty = rawDy < 0 ? Math.abs(rawDy) * 0.6D : rawDy * 1.5D;
        var tieBreak = Math.min(dx, dz) * 0.001;
        return dx + dz + yPenalty + tieBreak;
    }

    private static void tryAddWalk(Level level, Mob mob, List<BlockPos> result, BlockPos feet, PathNodeCache cache) {
        if (cache.canStandAt(level, mob, feet)) {
            result.add(feet);
        }
    }

    private static void tryAddClimb(Level level, Mob mob, List<BlockPos> result, BlockPos feet, PathNodeCache cache) {
        if (cache.isSafeClimbNode(level, feet, mob) && hasClimbClearance(level, mob, feet, cache)) {
            result.add(feet);
        }
    }

    private static boolean hasClimbClearance(Level level, Mob mob, BlockPos feet, PathNodeCache cache) {
        var below = feet.below();
        var belowShape = level.getBlockState(below).getCollisionShape(level, below);
        var belowTopY = belowShape.isEmpty() ? 0.0D : belowShape.max(Direction.Axis.Y);
        var bottomY = feet.getY() + belowTopY;

        var tight = isTightTunnel(level, feet, cache);
        var testHalfW = tight ? crawlHalfWidth(mob, 0.35D) : crawlHalfWidth(mob, 0.3D);
        var testHeight = tight ? getEffectiveCrawlHeight(mob) : mob.getBbHeight();

        var mobBox = new AABB(
            feet.getX() + 0.5D - testHalfW,
            bottomY,
            feet.getZ() + 0.5D - testHalfW,
            feet.getX() + 0.5D + testHalfW,
            bottomY + testHeight,
            feet.getZ() + 0.5D + testHalfW
        );
        return level.noCollision(mob, mobBox);
    }

    /** Routes a solidity check through the cache when one is available. */
    private static boolean solidAt(Level level, BlockPos pos, PathNodeCache cache) {
        return cache != null ? cache.isPhysicallySolid(level, pos) : CollisionQueries.isPhysicallySolid(level, pos);
    }

    /**
     * Cheap gate deciding whether it is worth generating any wall-crawl candidates from {@code pos}. Climb nodes
     * require a solid face to grip, so on open terrain this rejects the whole climb-candidate block before it runs.
     * Matches {@link CollisionQueries#isSafeClimbNode}'s notion of a cling surface: a horizontal neighbor at feet or
     * head height, or an overhead ceiling — never the floor.
     */
    private static boolean hasClimbSurfaceNearby(Level level, BlockPos pos, PathNodeCache cache) {
        var head = pos.above();
        return solidAt(level, pos.north(), cache)
            || solidAt(level, pos.south(), cache)
            || solidAt(level, pos.east(), cache)
            || solidAt(level, pos.west(), cache)
            || solidAt(level, head.north(), cache)
            || solidAt(level, head.south(), cache)
            || solidAt(level, head.east(), cache)
            || solidAt(level, head.west(), cache)
            || solidAt(level, head.above(), cache);
    }

    public static boolean isTightTunnel(Level level, BlockPos pos) {
        return isTightTunnel(level, pos, null);
    }

    public static boolean isTightTunnel(Level level, BlockPos pos, PathNodeCache cache) {
        var head = pos.above();

        var eastFeet = solidAt(level, pos.east(), cache);
        var westFeet = solidAt(level, pos.west(), cache);
        var eastHead = solidAt(level, head.east(), cache);
        var westHead = solidAt(level, head.west(), cache);
        var northFeet = solidAt(level, pos.north(), cache);
        var southFeet = solidAt(level, pos.south(), cache);
        var northHead = solidAt(level, head.north(), cache);
        var southHead = solidAt(level, head.south(), cache);
        var confinedX = (eastFeet && eastHead) && (westFeet && westHead);
        var confinedZ = (northFeet && northHead) && (southFeet && southHead);
        if (confinedX || confinedZ)
            return true;

        var ceiling = !level.getBlockState(head).getCollisionShape(level, head).isEmpty();
        if (ceiling) {
            return (eastFeet || eastHead) || (westFeet || westHead) || (northFeet || northHead) || (southFeet
                || southHead);
        }

        return false;
    }

    @Override
    public double cost(TraversalContext context, BlockPos from, BlockPos to) {
        return movementCost(
            context.level(),
            context.mob(),
            from,
            to,
            context.cache() != null ? context.cache() : new PathNodeCache()
        );
    }

    public double movementCost(Level level, Mob mob, BlockPos from, BlockPos to) {
        return movementCost(level, mob, from, to, new PathNodeCache());
    }

    public double movementCost(Level level, Mob mob, BlockPos from, BlockPos to, PathNodeCache cache) {
        if (!TraversalQueries.isSafeBlock(level, to, mob)) {
            return 9999.0D;
        }

        var toState = level.getBlockState(to);
        var inFluid = !toState.getFluidState().isEmpty();
        if (!inFluid && !TraversalQueries.isSafeBlock(level, to.below(), mob)) {
            return 9999.0D;
        }

        var toIsWalkable = cache.canStandAt(level, mob, to);
        var toIsTunnelWalk = !toIsWalkable && (cache.tunnelCanStandAt(level, mob, to)
            || (isTightTunnel(level, to, cache) && canStandAtCrawlSize(level, mob, to)));
        var toIsClimbable = cache.isSafeClimbNode(level, to, mob);

        var toIsFluid = !level.getBlockState(to).getFluidState().isEmpty()
            && TraversalQueries.isSafeBlock(level, to, mob);
        if (!toIsWalkable && !toIsTunnelWalk && !toIsClimbable && !toIsFluid) {
            return 9999.0D;
        }

        if (toIsClimbable && !toIsWalkable && !toIsTunnelWalk) {
            var testHalfW = crawlHalfWidth(mob, 0.3D);
            var testHeight = isTightTunnel(level, to, cache) ? getEffectiveCrawlHeight(mob) : mob.getBbHeight();
            var belowTo = to.below();
            var belowShape = level.getBlockState(belowTo).getCollisionShape(level, belowTo);
            var belowTopY = belowShape.isEmpty() ? 0.0D : belowShape.max(Direction.Axis.Y);
            var bottomY = to.getY() + belowTopY;
            var mobBox = new AABB(
                to.getX() + 0.5D - testHalfW,
                bottomY,
                to.getZ() + 0.5D - testHalfW,
                to.getX() + 0.5D + testHalfW,
                bottomY + testHeight,
                to.getZ() + 0.5D + testHalfW
            );
            if (!level.noCollision(mob, mobBox)) {
                return 9999.0D;
            }
        }

        var cost = 1.0D;

        var dy = to.getY() - from.getY();

        if (inFluid) {
            cost += 2.0D;
        }

        if (toIsTunnelWalk) {
            if (dy > 0)
                return 1.6D;
            if (dy < 0)
                return 1.1D;
            return 1.0D;
        }

        if (toIsClimbable && !toIsWalkable) {
            if (dy > 0) {
                cost += 2.0D;
            } else if (dy < 0) {
                cost += 2.0D;
            } else {
                cost += 3.0D;
            }
        } else {
            if (dy > 0) {
                cost += 1.5D;
            } else if (dy < 0) {
                cost += 0.5D;
            }
        }

        if (toIsWalkable && !toIsClimbable) {
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
        }

        var needsTransitionCheck = dy > 0 || toIsClimbable && !toIsWalkable;
        if (needsTransitionCheck && !toIsFluid && !hasTransitionClearance(level, mob, from, to, cache)) {
            return 9999.0D;
        }

        return cost;
    }

    public static boolean verticalShaftCanCrawlAt(Level level, Mob mob, BlockPos feet) {
        return verticalShaftCanCrawlAt(level, mob, feet, null);
    }

    public static boolean verticalShaftCanCrawlAt(Level level, Mob mob, BlockPos feet, PathNodeCache cache) {
        var crawlHeight = getEffectiveCrawlHeight(mob);
        var blocksOccupied = Math.max(1, (int) Math.ceil(crawlHeight));

        for (var dy = 0; dy < blocksOccupied; dy++) {
            var check = feet.above(dy);

            if (!isPassableForCrawl(level, check, mob))
                return false;
            if (!TraversalQueries.isSafeBlock(level, check, mob))
                return false;
        }

        var head = feet.above();
        var hasSideWall =
            (solidAt(level, feet.east(), cache) && solidAt(level, head.east(), cache))
                || (solidAt(level, feet.west(), cache) && solidAt(level, head.west(), cache))
                || (solidAt(level, feet.north(), cache) && solidAt(level, head.north(), cache))
                || (solidAt(level, feet.south(), cache) && solidAt(level, head.south(), cache));

        if (!hasSideWall)
            return false;

        var testHalfW = crawlHalfWidth(mob, 0.3D);
        var mobBox = new AABB(
            feet.getX() + 0.5D - testHalfW,
            feet.getY(),
            feet.getZ() + 0.5D - testHalfW,
            feet.getX() + 0.5D + testHalfW,
            feet.getY() + crawlHeight,
            feet.getZ() + 0.5D + testHalfW
        );

        return level.noCollision(mob, mobBox);
    }

    public static boolean canStandAtCrawlSize(Level level, Mob mob, BlockPos feet) {
        var crawlHeight = getEffectiveCrawlHeight(mob);
        var halfW = mob.getBbWidth() / 2.0D;
        var padding = 0.02D;
        var radius = halfW + padding;

        var centerX = feet.getX() + 0.5D;
        var centerZ = feet.getZ() + 0.5D;

        var minX = Mth.floor(centerX - radius);
        var maxX = Mth.floor(centerX + radius);
        var minZ = Mth.floor(centerZ - radius);
        var maxZ = Mth.floor(centerZ + radius);

        for (var x = minX; x <= maxX; x++) {
            for (var z = minZ; z <= maxZ; z++) {
                var checkFeet = new BlockPos(x, feet.getY(), z);

                if (!TraversalQueries.isSafeBlock(level, checkFeet, mob))
                    return false;
                if (!level.getBlockState(checkFeet).getCollisionShape(level, checkFeet).isEmpty())
                    return false;

                var blocksToCheck = Math.max(1, (int) Math.ceil(crawlHeight));
                for (var dy = 1; dy < blocksToCheck + 1; dy++) {
                    var checkAbove = checkFeet.above(dy);
                    if (!TraversalQueries.isSafeBlock(level, checkAbove, mob))
                        return false;
                    if (!level.getBlockState(checkAbove).getCollisionShape(level, checkAbove).isEmpty())
                        return false;
                }
            }
        }
        var feetInFluid = !level.getBlockState(feet).getFluidState().isEmpty();
        var below = feet.below();
        return feetInFluid || !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    private static boolean hasTransitionClearance(
        Level level,
        Mob mob,
        BlockPos from,
        BlockPos to,
        PathNodeCache cache
    ) {
        var fromCenter = Vec3.atBottomCenterOf(from);
        var toCenter = Vec3.atBottomCenterOf(to);

        var halfW = crawlHalfWidth(mob, 0.35D);
        var height = getEffectiveCrawlHeight(mob);
        var dy = to.getY() - from.getY();

        if (dy > 0) {
            var toIsClimb = cache.isSafeClimbNode(level, to, mob);
            var fromIsClimb = cache.isSafeClimbNode(level, from, mob);
            var sweepOriginX = (toIsClimb && !fromIsClimb) ? toCenter.x : fromCenter.x;
            var sweepOriginZ = (toIsClimb && !fromIsClimb) ? toCenter.z : fromCenter.z;

            var vertSteps = Math.max(2, dy * 4);
            for (var i = 1; i <= vertSteps; i++) {
                var t = i / (double) vertSteps;
                var p = new Vec3(sweepOriginX, fromCenter.y + dy * t, sweepOriginZ);
                var box = new AABB(p.x - halfW, p.y, p.z - halfW, p.x + halfW, p.y + height, p.z + halfW);
                if (!level.noCollision(mob, box)) {
                    return false;
                }
            }

            if (!toIsClimb) {
                var horizDelta = new Vec3(toCenter.x - fromCenter.x, 0.0D, toCenter.z - fromCenter.z);
                var horizSteps = Math.max(2, Mth.ceil(horizDelta.length() * 4.0D));
                for (var i = 1; i <= horizSteps; i++) {
                    var t = i / (double) horizSteps;
                    var p = new Vec3(fromCenter.x + horizDelta.x * t, toCenter.y, fromCenter.z + horizDelta.z * t);
                    var box = new AABB(p.x - halfW, p.y, p.z - halfW, p.x + halfW, p.y + height, p.z + halfW);
                    if (!level.noCollision(mob, box)) {
                        return false;
                    }
                }
            }
            return true;
        }

        var delta = toCenter.subtract(fromCenter);
        var steps = Math.max(2, Mth.ceil(delta.length() * 4.0D));

        for (var i = 1; i <= steps; i++) {
            var t = i / (double) steps;
            var p = fromCenter.add(delta.scale(t));

            var box = new AABB(p.x - halfW, p.y, p.z - halfW, p.x + halfW, p.y + height, p.z + halfW);

            if (!level.noCollision(mob, box)) {
                return false;
            }
        }

        return true;
    }
}
