package com.azure.azurecortex.navigation.astar;

import net.minecraft.entity.EntityLiving;
import net.minecraft.util.math.BlockPos;
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

import com.azure.azurecortex.config.CortexConfig;
import com.azure.azurecortex.navigation.traversal.TraversalContext;
import com.azure.azurecortex.navigation.traversal.TraversalEvaluator;
import com.azure.azurecortex.runtime.CortexDebug;
import com.azure.azurecortex.util.McCompat;

/**
 * A resumable A* search that spends a fixed, small node-expansion budget per call to {@link #step} instead of running
 * to completion (or to its {@code maxSearched} cap) in one synchronous burst.
 * <h3>Why this exists</h3> {@link AStarPathfinder#findPath} is correct but unconditionally synchronous: a single call
 * can expand up to several thousand nodes in one server tick. That's fine for one mob, but a re-invoking action might
 * call it every ~20-40 ticks per mob, and every mob whose repath cooldown happens to expire the same tick pays its full
 * search cost on that one tick — the classic "N pathfinders all wake up on the same frame" hitch. This class is the
 * same search, restructured so its cost can be spread across many ticks: call {@link #step(int)} with a small per-tick
 * node budget from a caller that can tolerate the path not being ready immediately, and it will report
 * {@link Status#RUNNING} until either a path is found or the search is exhausted.
 * <h3>One engine, any movement model</h3> The core loop (open set, closed set, best-cost map, best-partial tracking) is
 * identical regardless of how a mob moves — what differs is entirely captured by the supplied
 * {@link TraversalEvaluator}. {@link #normal} is the built-in wiring over {@link AStarPathfinder}'s plain
 * ground-walking model, but a caller with a genuinely different movement model can supply its own
 * {@link TraversalEvaluator} via {@link #of} and get the same incremental budgeting for free.
 * <h3>Node cache lifetime</h3> A session is expected to live across many ticks, so callers that supply a
 * {@link PathNodeCache} (for a custom evaluator that uses one) should give it a lifetime matching the session — created
 * fresh when the session starts, discarded when it ends. {@link #normal} doesn't need a cache at all.
 * <h3>Usage</h3>
 *
 * <pre>
 *
 * {
 *     &#64;code
 *     IncrementalPathSession session = IncrementalPathSession.normal(
 *         mob,
 *         new BlockPos(mob),
 *         new BlockPos(target),
 *         64,
 *         1
 *     );
 *     // ... once per tick, until it stops returning RUNNING:
 *     IncrementalPathSession.Status status = session.step(300);
 *     if (status == IncrementalPathSession.Status.DONE) {
 *         List<BlockPos> path = session.result();
 *     } else if (status == IncrementalPathSession.Status.FAILED) {
 *         // no path exists within range; fall back or give up — see PhasedPathSession
 *     }
 * }
 * </pre>
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
     * visualization convention the finished path goes through. The 1.12.2 port only ships the ground-walking model.
     */
    public enum Mode {
        NORMAL
    }

    /** Matches {@link AStarPathfinder#findPath}'s cap, so a normal session searches no harder. */
    private static final int DEFAULT_NORMAL_MAX_SEARCHED = 2000;

    private final EntityLiving mob;

    private final BlockPos startFeet;

    private final BlockPos goalFeet;

    private final int goalRadius;

    private final int effectiveRange;

    private final int maxSearched;

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

    private List<BlockPos> result = Collections.emptyList();

    private IncrementalPathSession(
        EntityLiving mob,
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
        this.mode = mode;
        this.context = cache != null
            ? TraversalContext.of(mob.world, mob, cache)
            : TraversalContext.of(mob.world, mob);

        open.add(new AStarNode(startFeet, 0.0D, evaluator.heuristic(startFeet, goalFeet), null));
        bestCost.put(startFeet, 0.0D);
    }

    /**
     * Builds a session using {@link AStarPathfinder}'s plain ground-walking neighbor/cost/heuristic model, with the
     * same {@code maxSearched} cap as its synchronous {@code findPath}. No {@link PathNodeCache} is needed.
     */
    public static IncrementalPathSession normal(
        EntityLiving mob,
        BlockPos start,
        BlockPos goal,
        int maxRange,
        int goalRadius
    ) {
        return normal(mob, start, goal, maxRange, goalRadius, DEFAULT_NORMAL_MAX_SEARCHED);
    }

    public static IncrementalPathSession normal(
        EntityLiving mob,
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
     *
     * @param cache a {@link PathNodeCache} dedicated to this session's lifetime, or {@code null}
     */
    public static IncrementalPathSession of(
        EntityLiving mob,
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

        World world = mob.world;
        int spent = 0;

        while (!open.isEmpty() && spent < nodeBudget) {
            if (searched >= maxSearched) {
                return finish();
            }

            searched++;
            spent++;

            AStarNode current = open.poll();

            double partialScore = evaluator.heuristic(current.pos(), goalFeet);
            if (partialScore < bestPartialScore) {
                bestPartialScore = partialScore;
                bestPartial = current;
            }

            if (!closed.add(current.pos())) {
                continue;
            }

            if (
                evaluator.isCloseEnoughToGoal(current.pos(), goalFeet, goalRadius)
                    && !evaluator.isSolidlySeparatedVertically(world, current.pos(), goalFeet)
            ) {
                result = finalizePath(AStarNode.reconstruct(current));
                status = Status.DONE;
                return status;
            }

            for (BlockPos next : evaluator.neighbors(context, current.pos(), goalFeet)) {
                if (closed.contains(next)) {
                    continue;
                }

                if (McCompat.manhattan(next, startFeet) > effectiveRange) {
                    continue;
                }

                double stepCost = evaluator.cost(context, current.pos(), next);

                if (stepCost >= 9999.0D) {
                    continue;
                }

                double newG = current.g() + stepCost;
                double oldG = bestCost.getOrDefault(next, Double.MAX_VALUE);

                if (newG < oldG) {
                    bestCost.put(next, newG);
                    double f = newG + evaluator.heuristic(next, goalFeet);
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
            result = finalizePath(AStarNode.reconstruct(bestPartial));
            status = Status.DONE;
        } else {
            result = new ArrayList<>();
            status = Status.FAILED;
        }
        return status;
    }

    /** Applies the same debug visualization the synchronous {@link AStarPathfinder#findPath} would. */
    private List<BlockPos> finalizePath(List<BlockPos> rawPath) {
        if (CortexConfig.get().enablePathfindingDebug) {
            for (int i = 0; i < rawPath.size() - 1; i++) {
                CortexDebug.sendParticlePath(
                    mob,
                    McCompat.atCenterOf(rawPath.get(i)),
                    McCompat.atCenterOf(rawPath.get(i + 1))
                );
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
