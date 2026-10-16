package com.azure.azurecortex.action.movement;

import net.minecraft.entity.EntityLiving;
import net.minecraft.util.math.BlockPos;

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
import com.azure.azurecortex.runtime.CooldownTracker;
import com.azure.azurecortex.util.McCompat;

/**
 * A generic reference action that drives an agent toward whatever {@link BlockPos} is currently stored under
 * {@link CommonBlackboardKeys#DESTINATION}, using a supplied {@link Pathfinder} to find the route.
 * <h3>Incremental pathfinding</h3> When {@link CortexConfig#enableIncrementalPathfinding} is on, a repath starts an
 * {@link IncrementalPathSession} instead of calling {@link Pathfinder#findPath} synchronously, and each subsequent tick
 * spends up to {@link CortexConfig#incrementalPathfindingNodeBudget} node expansions on it via
 * {@link IncrementalPathSession#step}, independent of the repath cooldown (which only gates *starting* a new search,
 * not continuing one already in progress). This spreads an expensive search across several ticks instead of paying for
 * it all in one server tick — see {@link IncrementalPathSession}'s class docs for why that matters. When the flag is
 * off, {@link #repath} falls straight back to the old one-shot {@link Pathfinder#findPath} call.
 * <p>
 * The chosen route's endpoint is handed to the entity's own vanilla {@code PathNavigate}, which does the
 * moment-to-moment steering.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public class MoveToDestinationAction<E extends EntityLiving, G> implements Action<E, G> {

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
        BlockPos destination = blackboard.get(CommonBlackboardKeys.DESTINATION);
        if (destination == null) {
            return ActionOutcome.failed();
        }

        BlockPos agentPos = new BlockPos(agent);

        if (McCompat.closerThan(agentPos, destination, arrivalRadius)) {
            blackboard.remove(CommonBlackboardKeys.DESTINATION);
            return ActionOutcome.success();
        }

        if (session != null) {
            IncrementalPathSession.Status status = session.step(CortexConfig.get().incrementalPathfindingNodeBudget);

            if (status == IncrementalPathSession.Status.DONE) {
                applyPath(agent, session.result());
                session = null;
            } else if (status == IncrementalPathSession.Status.FAILED) {
                session = null;
                return ActionOutcome.failed(PlanFailureReason.FAILED_NO_PATH, agentPos);
            }
            // RUNNING: nothing else to do this tick.
        } else if (agent.getNavigator().noPath() && cooldowns.ready(repathCooldownKey)) {
            repath(agent, blackboard, cooldowns);
        }

        double distSq = agentPos.distanceSq(destination);
        if (distSq < lastDistanceSqr - 0.25D) {
            lastDistanceSqr = distSq;
            ticksSinceProgress = 0;
        } else {
            ticksSinceProgress++;
        }

        if (ticksSinceProgress >= stuckTimeoutTicks) {
            return ActionOutcome.failed(PlanFailureReason.FAILED_STUCK, agentPos);
        }

        return ActionOutcome.running();
    }

    private void repath(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        BlockPos destination = blackboard.get(CommonBlackboardKeys.DESTINATION);
        if (destination == null)
            return;

        cooldowns.set(repathCooldownKey, repathIntervalTicks);

        BlockPos start = new BlockPos(agent);

        if (CortexConfig.get().enableIncrementalPathfinding) {
            session = IncrementalPathSession.normal(agent, start, destination, 64, Math.max(arrivalRadius, 1));
            return;
        }

        List<BlockPos> path = pathfinder.findPath(agent, start, destination, 64, Math.max(arrivalRadius, 1));
        applyPath(agent, path);
    }

    private void applyPath(E agent, List<BlockPos> path) {
        if (path.isEmpty())
            return;

        BlockPos target = path.get(path.size() - 1);
        agent.getNavigator().tryMoveToXYZ(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, speed);
    }

    @Override
    public void stop(E agent, Blackboard blackboard, CooldownTracker cooldowns, ActionStatus reason) {
        agent.getNavigator().clearPath();
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
