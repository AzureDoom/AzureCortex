package com.azure.azurecortex.action.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;

import java.util.List;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.action.ActionOutcome;
import com.azure.azurecortex.api.action.ActionStatus;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.api.navigation.Pathfinder;
import com.azure.azurecortex.config.CortexConfig;
import com.azure.azurecortex.goap.PlanFailureReason;
import com.azure.azurecortex.navigation.astar.IncrementalPathSession;
import com.azure.azurecortex.navigation.astar.PathNodeCache;
import com.azure.azurecortex.navigation.crawl.CrawlTraversalEvaluator;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * A generic reference action that drives an agent toward whatever {@link BlockPos} is currently stored under
 * {@link CommonBlackboardKeys#DESTINATION}, using a supplied {@link Pathfinder} to find the route.
 * <p>
 * This is intentionally a minimal building block, not a port of Ovomorphosis's heavily-tuned
 * {@code MoveToDestinationAction}/{@code MoveToTargetAction} (which layer in resin-web awareness, break-to-target GOAP
 * hooks, wall-crawl approach selection, and other creature-specific logic across well over a thousand lines each). Mods
 * with that level of navigation sophistication should compose their own action on top of the
 * {@code com.azure.azurecortex.navigation} package directly, following this class's shape: repath on a cooldown, report
 * {@link ActionOutcome.Blocked}/{@link ActionOutcome.Failed} with a {@link PlanFailureReason} when the navigator
 * stalls, and clear the destination on arrival.
 * <h3>Incremental pathfinding</h3> When {@link CortexConfig#enableIncrementalPathfinding} is on, a repath starts an
 * {@link IncrementalPathSession} instead of calling {@link Pathfinder#findPath} synchronously, and each subsequent tick
 * spends up to {@link CortexConfig#incrementalPathfindingNodeBudget} node expansions on it via
 * {@link IncrementalPathSession#step}, independent of the repath cooldown (which only gates *starting* a new search,
 * not continuing one already in progress). This spreads an expensive search across several ticks instead of paying for
 * it all in one server tick — see {@link IncrementalPathSession}'s class docs for why that matters. When the flag is
 * off, {@link #repath} falls straight back to the old one-shot {@link Pathfinder#findPath} call.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public class MoveToDestinationAction<E extends Mob, G> implements Action<E, G> {

    private final Pathfinder pathfinder;

    private final double speed;

    private final int arrivalRadius;

    private final int repathIntervalTicks;

    private final int stuckTimeoutTicks;

    private final String repathCooldownKey;

    private int ticksSinceProgress;

    private double lastDistanceSqr = Double.MAX_VALUE;

    /** Non-null only while an incremental search is in progress; see the class docs above. */
    private IncrementalPathSession session;

    /**
     * Reused across repaths so a crawling session's node-classification cache stays warm between attempts. Only
     * allocated the first time an incremental crawling search actually runs.
     */
    private PathNodeCache cache;

    /**
     * @param pathfinder          finds the route to the destination
     * @param speed               navigation speed modifier
     * @param arrivalRadius       how close (blocks) counts as "arrived"
     * @param repathIntervalTicks minimum ticks between repath attempts
     * @param stuckTimeoutTicks   how many ticks without meaningful progress before giving up as
     *                            {@link PlanFailureReason#FAILED_STUCK}
     */
    public MoveToDestinationAction(
        Pathfinder pathfinder,
        double speed,
        int arrivalRadius,
        int repathIntervalTicks,
        int stuckTimeoutTicks
    ) {
        this.pathfinder = pathfinder;
        this.speed = speed;
        this.arrivalRadius = arrivalRadius;
        this.repathIntervalTicks = repathIntervalTicks;
        this.stuckTimeoutTicks = stuckTimeoutTicks;
        this.repathCooldownKey = "azurecortex:move_to_destination:" + System.identityHashCode(this);
    }

    @Override
    public void start(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        ticksSinceProgress = 0;
        lastDistanceSqr = Double.MAX_VALUE;
        session = null;
        repath(agent, blackboard, cooldowns);
    }

    @Override
    public ActionOutcome<G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        var destination = blackboard.get(CommonBlackboardKeys.DESTINATION);
        if (destination == null) {
            return ActionOutcome.failed();
        }

        if (agent.blockPosition().closerThan(destination, arrivalRadius)) {
            blackboard.remove(CommonBlackboardKeys.DESTINATION);
            return ActionOutcome.success();
        }

        if (session != null) {
            var status = session.step(CortexConfig.get().incrementalPathfindingNodeBudget);

            if (status == IncrementalPathSession.Status.DONE) {
                applyPath(agent, session.result());
                session = null;
            } else if (status == IncrementalPathSession.Status.FAILED) {
                session = null;
                return ActionOutcome.failed(PlanFailureReason.FAILED_NO_PATH, agent.blockPosition());
            }
            // RUNNING: nothing else to do this tick.
        } else if (agent.getNavigation().isDone() && cooldowns.ready(repathCooldownKey)) {
            repath(agent, blackboard, cooldowns);
        }

        var distSq = agent.blockPosition().distSqr(destination);
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

    private void repath(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        var destination = blackboard.get(CommonBlackboardKeys.DESTINATION);
        if (destination == null)
            return;

        cooldowns.set(repathCooldownKey, repathIntervalTicks);

        if (CortexConfig.get().enableIncrementalPathfinding) {
            if (pathfinder instanceof CrawlTraversalEvaluator) {
                if (cache == null) {
                    cache = new PathNodeCache();
                }
                session = IncrementalPathSession.crawling(
                    agent,
                    agent.blockPosition(),
                    destination,
                    64,
                    Math.max(arrivalRadius, 1),
                    cache
                );
            } else {
                session = IncrementalPathSession.normal(
                    agent,
                    agent.blockPosition(),
                    destination,
                    64,
                    Math.max(arrivalRadius, 1)
                );
            }
            return;
        }

        var path = pathfinder.findPath(agent, agent.blockPosition(), destination, 64, Math.max(arrivalRadius, 1));
        applyPath(agent, path);
    }

    private void applyPath(E agent, List<BlockPos> path) {
        if (path.isEmpty())
            return;

        var target = path.get(path.size() - 1);
        agent.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, speed);
    }

    @Override
    public void stop(E agent, Blackboard blackboard, CooldownTracker cooldowns, ActionStatus reason) {
        agent.getNavigation().stop();
        session = null;
    }

    @Override
    public boolean isInterruptible() {
        return true;
    }

    @Override
    public int priority() {
        return 10;
    }
}
