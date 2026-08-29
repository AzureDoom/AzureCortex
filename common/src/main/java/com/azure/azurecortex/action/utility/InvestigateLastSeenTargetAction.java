package com.azure.azurecortex.action.utility;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.function.Function;
import java.util.function.Predicate;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.action.ActionOutcome;
import com.azure.azurecortex.api.action.ActionStatus;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.api.navigation.Pathfinder;
import com.azure.azurecortex.goap.PlanFailureReason;
import com.azure.azurecortex.runtime.CooldownTracker;
import com.azure.azurecortex.sensing.TargetPrediction;
import com.azure.azurecortex.sensing.TargetSensor;

/**
 * A generic reference action that walks toward an extrapolated interception point for a target the agent recently saw
 * but has since lost, instead of beelining for the exact block it last occupied.
 * <p>
 * Requires {@link CommonBlackboardKeys#LAST_SEEN_POS} to be populated, which in turn requires the agent's
 * {@link TargetSensor} to have been constructed with a {@code visibilityPredicate} — see that class's docs. On
 * {@link #start}, this action reads {@link CommonBlackboardKeys#LAST_SEEN_POS}/
 * {@link CommonBlackboardKeys#LAST_SEEN_VELOCITY}/{@link CommonBlackboardKeys#LAST_SEEN_TICK} once, computes a single
 * search point via {@link TargetPrediction#predictInterceptPosition}, and paths to it — the point is not recomputed
 * mid-walk, since a fresh sighting means the target was reacquired, at which point the planner's next replan should
 * simply select a different goal rather than this action retargeting itself. Succeeds on arrival (the caller's behavior
 * tree / GOAP layer decides what "search" means next — a brief look-around, or falling back to wander); fails with
 * {@link PlanFailureReason#FAILED_PRECONDITION} if there's no usable sighting to investigate at all, or one already too
 * stale to be worth investigating; fails with {@link PlanFailureReason#FAILED_NO_PATH} /
 * {@link PlanFailureReason#FAILED_STUCK} if the walk itself doesn't pan out.
 * <h3>Validating the extrapolated point</h3> By default, the predicted point is validated with
 * {@link TargetPrediction#standableIn}, the ordinary ground-mob check (open space with solid footing directly below).
 * Mods whose agent doesn't have a ground-only footprint (a wall/ceiling crawler, a flyer) should supply their own
 * {@code standableFactory} via the second constructor instead of accepting that default — see
 * {@code com.azure.azurecortex.example.spider.SpiderTraversalPredicates} for a crawler-aware replacement, used exactly
 * this way by the bundled spider example.
 * <p>
 * Like {@code com.azure.azurecortex.action.movement.MoveToDestinationAction}, this is intentionally a minimal building
 * block: a single one-shot path, no incremental search, no repathing. Mods wanting more (e.g. periodic re-scanning for
 * incidental clues while walking) should compose their own action around {@link TargetPrediction} directly.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public class InvestigateLastSeenTargetAction<E extends Mob, G> implements Action<E, G> {

    private final Pathfinder pathfinder;

    private final double speed;

    private final int arrivalRadius;

    private final int stuckTimeoutTicks;

    private final int maxSearchAgeTicks;

    private final int maxPredictionStalenessTicks;

    private final double minPredictionSpeed;

    private final double minPredictionDistance;

    private final double maxPredictionDistance;

    private final Function<Level, Predicate<BlockPos>> standableFactory;

    private BlockPos searchPoint;

    private int ticksSinceProgress;

    private double lastDistanceSqr = Double.MAX_VALUE;

    /**
     * @param pathfinder                  finds the route to the predicted search point
     * @param speed                       navigation speed modifier
     * @param arrivalRadius               how close (blocks) counts as "arrived" at the search point
     * @param stuckTimeoutTicks           how many ticks without meaningful progress before giving up as
     *                                    {@link PlanFailureReason#FAILED_STUCK}
     * @param maxSearchAgeTicks           if {@link CommonBlackboardKeys#LAST_SEEN_TICK} is already older than this when
     *                                    the action starts, it fails immediately with
     *                                    {@link PlanFailureReason#FAILED_PRECONDITION} rather than walking to a point
     *                                    the target could no longer plausibly be near
     * @param maxPredictionStalenessTicks passed through to {@link TargetPrediction#predictInterceptPosition}
     * @param minPredictionSpeed          passed through to {@link TargetPrediction#predictInterceptPosition}
     * @param minPredictionDistance       passed through to {@link TargetPrediction#predictInterceptPosition}
     * @param maxPredictionDistance       passed through to {@link TargetPrediction#predictInterceptPosition}
     */
    public InvestigateLastSeenTargetAction(
        Pathfinder pathfinder,
        double speed,
        int arrivalRadius,
        int stuckTimeoutTicks,
        int maxSearchAgeTicks,
        int maxPredictionStalenessTicks,
        double minPredictionSpeed,
        double minPredictionDistance,
        double maxPredictionDistance
    ) {
        this(
            pathfinder,
            speed,
            arrivalRadius,
            stuckTimeoutTicks,
            maxSearchAgeTicks,
            maxPredictionStalenessTicks,
            minPredictionSpeed,
            minPredictionDistance,
            maxPredictionDistance,
            TargetPrediction::standableIn
        );
    }

    /**
     * As the nine-argument constructor, but with an explicit {@code standableFactory} in place of the default
     * {@link TargetPrediction#standableIn}, for agents whose footprint isn't ground-only — see the class docs.
     *
     * @param standableFactory produces the predicate the extrapolated point is validated against, given the agent's
     *                         current level
     */
    public InvestigateLastSeenTargetAction(
        Pathfinder pathfinder,
        double speed,
        int arrivalRadius,
        int stuckTimeoutTicks,
        int maxSearchAgeTicks,
        int maxPredictionStalenessTicks,
        double minPredictionSpeed,
        double minPredictionDistance,
        double maxPredictionDistance,
        Function<Level, Predicate<BlockPos>> standableFactory
    ) {
        this.pathfinder = pathfinder;
        this.speed = speed;
        this.arrivalRadius = arrivalRadius;
        this.stuckTimeoutTicks = stuckTimeoutTicks;
        this.maxSearchAgeTicks = maxSearchAgeTicks;
        this.maxPredictionStalenessTicks = maxPredictionStalenessTicks;
        this.minPredictionSpeed = minPredictionSpeed;
        this.minPredictionDistance = minPredictionDistance;
        this.maxPredictionDistance = maxPredictionDistance;
        this.standableFactory = standableFactory;
    }

    @Override
    public void start(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        searchPoint = null;
        ticksSinceProgress = 0;
        lastDistanceSqr = Double.MAX_VALUE;
    }

    @Override
    public ActionOutcome<G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        if (searchPoint == null) {
            var outcome = beginSearch(agent, blackboard);
            if (outcome != null)
                return outcome;
            return ActionOutcome.running();
        }

        if (agent.blockPosition().closerThan(searchPoint, arrivalRadius)) {
            return ActionOutcome.success();
        }

        var distSq = agent.blockPosition().distSqr(searchPoint);
        if (distSq < lastDistanceSqr - 0.25D) {
            lastDistanceSqr = distSq;
            ticksSinceProgress = 0;
        } else {
            ticksSinceProgress++;
        }

        if (ticksSinceProgress >= stuckTimeoutTicks) {
            return ActionOutcome.failed(PlanFailureReason.FAILED_STUCK, agent.blockPosition());
        }

        return ActionOutcome.running();
    }

    /**
     * Computes the search point from whatever last-seen state is currently on the blackboard and commits to a path
     * toward it.
     *
     * @return a terminal {@link ActionOutcome} if the search couldn't even start, or {@code null} to proceed
     */
    private ActionOutcome<G> beginSearch(E agent, Blackboard blackboard) {
        var lastSeenPos = blackboard.get(CommonBlackboardKeys.LAST_SEEN_POS);
        if (lastSeenPos == null) {
            return ActionOutcome.failed(PlanFailureReason.FAILED_PRECONDITION);
        }

        var lastSeenTick = blackboard.get(CommonBlackboardKeys.LAST_SEEN_TICK);
        var currentTick = (int) agent.level().getGameTime();

        if (lastSeenTick != null && currentTick - lastSeenTick > maxSearchAgeTicks) {
            return ActionOutcome.failed(PlanFailureReason.FAILED_PRECONDITION, lastSeenPos);
        }

        var lastSeenVelocity = blackboard.get(CommonBlackboardKeys.LAST_SEEN_VELOCITY);

        searchPoint = TargetPrediction.predictInterceptPosition(
            lastSeenPos,
            lastSeenVelocity,
            lastSeenTick,
            currentTick,
            maxPredictionStalenessTicks,
            minPredictionSpeed,
            minPredictionDistance,
            maxPredictionDistance,
            standableFactory.apply(agent.level())
        );

        var path = pathfinder.findPath(agent, agent.blockPosition(), searchPoint, 64, Math.max(arrivalRadius, 1));
        if (path.isEmpty()) {
            return ActionOutcome.failed(PlanFailureReason.FAILED_NO_PATH, searchPoint);
        }

        var target = path.get(path.size() - 1);
        agent.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, speed);
        return null;
    }

    @Override
    public void stop(E agent, Blackboard blackboard, CooldownTracker cooldowns, ActionStatus reason) {
        agent.getNavigation().stop();
        searchPoint = null;
    }

    @Override
    public boolean isInterruptible() {
        return true;
    }

    @Override
    public int priority() {
        return 8;
    }
}
