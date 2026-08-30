package com.azure.azurecortex.navigation.astar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.azure.azurecortex.config.CortexConfig;
import com.azure.azurecortex.navigation.crawl.CrawlTraversalEvaluator;
import com.azure.azurecortex.navigation.traversal.TraversalContext;
import com.azure.azurecortex.navigation.traversal.TraversalEvaluator;
import com.azure.azurecortex.runtime.CortexDebug;

/**
 * A resumable A* search that spends a fixed, small node-expansion budget per call to {@link #step} instead of running
 * to completion (or to its {@code maxSearched} cap) in one synchronous burst.
 * <h3>Why this exists</h3> {@link AStarPathfinder#findPath} and {@code CrawlTraversalEvaluator#findPath} are correct
 * but unconditionally synchronous: a single call can expand up to several thousand nodes in one server tick. That's
 * fine for one mob, but a re-invoking action might call it every ~20-40 ticks per mob, and every mob whose repath
 * cooldown happens to expire the same tick pays its full search cost on that one tick — the classic "N pathfinders all
 * wake up on the same frame" hitch. This class is the same search, restructured so its cost can be spread across many
 * ticks: call {@link #step(int)} with a small per-tick node budget from a caller that can tolerate the path not being
 * ready immediately, and it will report {@link Status#RUNNING} until either a path is found or the search is exhausted.
 * <h3>One engine, any movement model</h3> The core loop (open set, closed set, best-cost map, best-partial tracking) is
 * identical regardless of how a mob moves — what differs is entirely captured by the supplied
 * {@link TraversalEvaluator}. {@link #crawling} and {@link #normal} are the two built-in wirings — one over
 * {@link CrawlTraversalEvaluator}'s crawl-aware model, one over {@link AStarPathfinder}'s plain ground-walking model —
 * but a caller with a genuinely different movement model can supply its own {@link TraversalEvaluator} and get the same
 * incremental budgeting for free.
 * <h3>Node cache lifetime</h3> A session is expected to live across many ticks, so (unlike the single-shot
 * {@code findPath} calls, which typically get a cache cleared at the start of the search) callers should give a session
 * a {@link PathNodeCache} whose lifetime matches the session — created fresh when the session starts, discarded when it
 * ends. {@link #normal} doesn't need a cache at all — the plain ground-walking model doesn't use one.
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * var session = IncrementalPathSession.crawling(mob, mob.blockPosition(), target.blockPosition(), 96, 1, cache);
 * // ... once per tick, until it stops returning RUNNING:
 * var status = session.step(300);
 * if (status == IncrementalPathSession.Status.DONE) {
 *     var path = session.result();
 * } else if (status == IncrementalPathSession.Status.FAILED) {
 *     // no path exists within range; fall back or give up — see PhasedPathSession
 * }
 * }</pre>
 *
 * @see PhasedPathSession
 */
@SuppressWarnings("unused")
public final class IncrementalPathSession {

    public enum Status {
        RUNNING,
        DONE,
        FAILED
    }

    /**
     * Which movement model this session searches under. Doesn't affect the core search algorithm at all (that's
     * entirely determined by the supplied {@link TraversalEvaluator}) — it only selects which post-processing/debug
     * visualization convention the finished path goes through, matching whatever the corresponding synchronous
     * {@code findPath} already does.
     */
    public enum Mode {
        NORMAL,
        CRAWLING
    }

    /** Matches {@code CrawlTraversalEvaluator#findPath}'s cap, so a crawling session searches no harder. */
    private static final int DEFAULT_CRAWLING_MAX_SEARCHED = 6000;

    /** Matches {@link AStarPathfinder#findPath}'s cap, so a normal session searches no harder. */
    private static final int DEFAULT_NORMAL_MAX_SEARCHED = 2000;

    private final Mob mob;

    private final BlockPos startFeet;

    private final BlockPos goalFeet;

    private final int goalRadius;

    private final int effectiveRange;

    private final int maxSearched;

    private final PathNodeCache cache;

    private final Mode mode;

    private final TraversalEvaluator evaluator;

    private final TraversalContext context;

    private final PriorityQueue<AStarNode> open = new PriorityQueue<>(Comparator.comparingDouble(AStarNode::f));

    private final Map<BlockPos, Double> bestCost = new HashMap<>();

    private final Set<BlockPos> closed = new HashSet<>();

    private AStarNode bestPartial;

    private double bestPartialScore = Double.MAX_VALUE;

    private int searched = 0;

    private Status status = Status.RUNNING;

    private List<BlockPos> result = List.of();

    private IncrementalPathSession(
        Mob mob,
        BlockPos start,
        BlockPos goal,
        int maxRange,
        int goalRadius,
        int maxSearched,
        PathNodeCache cache,
        Mode mode,
        TraversalEvaluator evaluator
    ) {
        this.mob = mob;
        this.evaluator = evaluator;
        this.startFeet = evaluator.normalizeFeet(start);
        this.goalFeet = evaluator.normalizeFeet(goal);
        this.goalRadius = goalRadius;
        this.effectiveRange = Math.min(maxRange, 48);
        this.maxSearched = maxSearched;
        this.cache = cache;
        this.mode = mode;
        this.context = cache != null
            ? TraversalContext.of(mob.level(), mob, cache)
            : TraversalContext.of(mob.level(), mob);

        open.add(new AStarNode(startFeet, 0.0D, evaluator.heuristic(startFeet, goalFeet), null));
        bestCost.put(startFeet, 0.0D);
    }

    /**
     * Builds a session using {@link CrawlTraversalEvaluator}'s crawl-aware neighbor/cost/heuristic model, with the same
     * {@code maxSearched} cap as its synchronous {@code findPath}.
     *
     * @param cache a {@link PathNodeCache} dedicated to this session's lifetime
     */
    public static IncrementalPathSession crawling(
        Mob mob,
        BlockPos start,
        BlockPos goal,
        int maxRange,
        int goalRadius,
        PathNodeCache cache
    ) {
        return crawling(mob, start, goal, maxRange, goalRadius, DEFAULT_CRAWLING_MAX_SEARCHED, cache);
    }

    public static IncrementalPathSession crawling(
        Mob mob,
        BlockPos start,
        BlockPos goal,
        int maxRange,
        int goalRadius,
        int maxSearched,
        PathNodeCache cache
    ) {
        return new IncrementalPathSession(
            mob,
            start,
            goal,
            maxRange,
            goalRadius,
            maxSearched,
            cache,
            Mode.CRAWLING,
            CrawlTraversalEvaluator.INSTANCE
        );
    }

    /**
     * Builds a session using {@link AStarPathfinder}'s plain ground-walking neighbor/cost/heuristic model, with the
     * same {@code maxSearched} cap as its synchronous {@code findPath}. No {@link PathNodeCache} is needed.
     */
    public static IncrementalPathSession normal(Mob mob, BlockPos start, BlockPos goal, int maxRange, int goalRadius) {
        return normal(mob, start, goal, maxRange, goalRadius, DEFAULT_NORMAL_MAX_SEARCHED);
    }

    public static IncrementalPathSession normal(
        Mob mob,
        BlockPos start,
        BlockPos goal,
        int maxRange,
        int goalRadius,
        int maxSearched
    ) {
        return new IncrementalPathSession(
            mob,
            start,
            goal,
            maxRange,
            goalRadius,
            maxSearched,
            null,
            Mode.NORMAL,
            AStarPathfinder.INSTANCE
        );
    }

    /**
     * Builds a session using an arbitrary caller-supplied {@link TraversalEvaluator}, for movement models AzureCortex
     * doesn't ship a built-in wiring for.
     */
    public static IncrementalPathSession of(
        Mob mob,
        BlockPos start,
        BlockPos goal,
        int maxRange,
        int goalRadius,
        int maxSearched,
        PathNodeCache cache,
        TraversalEvaluator evaluator
    ) {
        return new IncrementalPathSession(
            mob,
            start,
            goal,
            maxRange,
            goalRadius,
            maxSearched,
            cache,
            Mode.NORMAL,
            evaluator
        );
    }

    /**
     * Spends up to {@code nodeBudget} node expansions on this search and returns the resulting status. Safe to call
     * repeatedly after the search has already finished.
     *
     * @param nodeBudget the maximum number of nodes to expand in this call; a few hundred is a reasonable per-tick
     *                   budget for most agent populations
     * @return {@link Status#RUNNING} if the budget was exhausted before the search concluded, {@link Status#DONE} if a
     *         path (full or best-effort partial) is ready in {@link #result()}, or {@link Status#FAILED} if no path —
     *         not even a partial one — could be found
     */
    public Status step(int nodeBudget) {
        if (status != Status.RUNNING)
            return status;

        var level = mob.level();
        var spent = 0;

        while (!open.isEmpty() && spent < nodeBudget) {
            if (searched >= maxSearched) {
                return finish();
            }

            searched++;
            spent++;

            var current = open.poll();

            var partialScore = evaluator.heuristic(current.pos(), goalFeet);
            if (partialScore < bestPartialScore) {
                bestPartialScore = partialScore;
                bestPartial = current;
            }

            if (!closed.add(current.pos())) {
                continue;
            }

            if (
                evaluator.isCloseEnoughToGoal(current.pos(), goalFeet, goalRadius)
                    && !evaluator.isSolidlySeparatedVertically(level, current.pos(), goalFeet)
            ) {
                result = finalizePath(AStarNode.reconstruct(current), true);
                status = Status.DONE;
                return status;
            }

            for (var next : evaluator.neighbors(context, current.pos(), goalFeet)) {
                if (closed.contains(next)) {
                    continue;
                }

                if (next.distManhattan(startFeet) > effectiveRange) {
                    continue;
                }

                var stepCost = evaluator.cost(context, current.pos(), next);

                if (stepCost >= 9999.0D) {
                    continue;
                }

                var newG = current.g() + stepCost;
                var oldG = bestCost.getOrDefault(next, Double.MAX_VALUE);

                if (newG < oldG) {
                    bestCost.put(next, newG);
                    var f = newG + evaluator.heuristic(next, goalFeet);
                    open.add(new AStarNode(next, newG, f, current));
                }
            }
        }

        if (open.isEmpty()) {
            return finish();
        }

        return Status.RUNNING;
    }

    private Status finish() {
        if (bestPartial != null && bestPartial.parent() != null) {
            result = finalizePath(AStarNode.reconstruct(bestPartial), false);
            status = Status.DONE;
        } else {
            result = new ArrayList<>();
            status = Status.FAILED;
        }
        return status;
    }

    /** Applies the same post-processing/debug-visualization the corresponding synchronous {@code findPath} would. */
    private List<BlockPos> finalizePath(List<BlockPos> rawPath, boolean fullPath) {
        if (mode == Mode.CRAWLING) {
            var filtered = CrawlTraversalEvaluator.filterTransitionNodes(rawPath, mob.level(), mob, cache);
            CrawlTraversalEvaluator.debugParticlePath(mob, filtered, fullPath);
            return filtered;
        }

        if (CortexConfig.get().enablePathfindingDebug) {
            for (var i = 0; i < rawPath.size() - 1; i++) {
                CortexDebug.sendParticlePath(mob, Vec3.atCenterOf(rawPath.get(i)), Vec3.atCenterOf(rawPath.get(i + 1)));
            }
        }
        return rawPath;
    }

    /** Returns the current status without doing any further search work. */
    public Status status() {
        return status;
    }

    /**
     * Returns the path found so far. Only meaningful once {@link #step} has returned {@link Status#DONE}; empty before
     * then or on {@link Status#FAILED}.
     */
    public List<BlockPos> result() {
        return result;
    }

    /** Total nodes expanded across all {@link #step} calls so far, for diagnostics/tuning the per-tick budget. */
    public int nodesSearched() {
        return searched;
    }

    /** Which movement model this session searches under. */
    public Mode mode() {
        return mode;
    }
}
