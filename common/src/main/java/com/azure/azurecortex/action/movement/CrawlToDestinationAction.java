package com.azure.azurecortex.action.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.action.ActionOutcome;
import com.azure.azurecortex.api.action.ActionStatus;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.goap.PlanFailureReason;
import com.azure.azurecortex.navigation.astar.PathNodeCache;
import com.azure.azurecortex.navigation.crawl.CrawlCapability;
import com.azure.azurecortex.navigation.crawl.CrawlController;
import com.azure.azurecortex.navigation.crawl.CrawlTraversalEvaluator;
import com.azure.azurecortex.navigation.movement.NavigationQueries;
import com.azure.azurecortex.navigation.traversal.CollisionQueries;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * A crawl-aware counterpart to {@code MoveToDestinationAction}: drives an agent toward
 * {@link CommonBlackboardKeys#DESTINATION} by walking each waypoint of a {@link CrawlTraversalEvaluator} path in turn,
 * rather than handing only the final point off to the agent's vanilla {@code PathNavigation}.
 * <h3>Why this exists, and how it differs from {@code MoveToDestinationAction}</h3> {@code MoveToDestinationAction} and
 * {@code InvestigateLastSeenTargetAction} both compute a full route with whatever
 * {@link com.azure.azurecortex.api.navigation.Pathfinder} they're given, but then execute it by handing only the
 * <em>last</em> waypoint to {@code agent.getNavigation().moveTo(...)} — vanilla's own ground-only node evaluator then
 * has to independently find a way to reach that point. For an ordinary ground mob that's harmless (vanilla can walk to
 * a point another ground search already proved reachable), but a destination on a wall or ceiling is often not
 * something vanilla's navigator can route to on its own at all. This action instead follows the AzureCortex-computed
 * path directly, one waypoint at a time, toggling {@link CrawlCapability#isWallCrawling} on and off per waypoint (via
 * {@link CrawlController#setWallCrawling}) and driving velocity straight through
 * {@link NavigationQueries#computeWallCrawlVelocity} rather than vanilla's move control.
 * <p>
 * Requires the agent to implement {@link CrawlCapability} (see {@link com.azure.azurecortex.navigation.crawl}) and, for
 * the velocity this action sets each tick to actually move the agent along a wall or ceiling rather than being fought
 * by vanilla's own gravity-driven {@code travel()}, the agent must also route its movement application through
 * {@link CrawlController} while {@link CrawlCapability#isWallCrawling} is {@code true} — see the class docs on
 * {@code com.azure.azurecortex.navigation.crawl.CrawlController} and the worked example in the AzureCortex wiki's
 * spider walkthrough for the {@code travel(Vec3)} override this implies.
 *
 * @param <E> the agent type; must implement {@link CrawlCapability}
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public class CrawlToDestinationAction<E extends Mob, G> implements Action<E, G> {

    private final CrawlTraversalEvaluator pathfinder;

    private final double speed;

    private final int arrivalRadius;

    private final int repathIntervalTicks;

    private final int stuckTimeoutTicks;

    private final String repathCooldownKey;

    private List<BlockPos> path = List.of();

    private int waypointIndex;

    private int ticksSinceProgress;

    private double lastDistanceSqr = Double.MAX_VALUE;

    private PathNodeCache cache;

    /**
     * @param pathfinder          the crawl-aware pathfinder to route with — almost always
     *                            {@link CrawlTraversalEvaluator#INSTANCE}
     * @param speed               per-tick speed cap passed to {@link NavigationQueries#computeWallCrawlVelocity}
     * @param arrivalRadius       how close (blocks) to the final destination counts as "arrived"
     * @param repathIntervalTicks minimum ticks between repath attempts once the current path runs out
     * @param stuckTimeoutTicks   how many ticks without meaningful progress toward the destination before giving up as
     *                            {@link PlanFailureReason#FAILED_STUCK}
     */
    public CrawlToDestinationAction(
        CrawlTraversalEvaluator pathfinder,
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
        this.repathCooldownKey = "azurecortex:crawl_to_destination:" + System.identityHashCode(this);
    }

    @Override
    public void start(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        ticksSinceProgress = 0;
        lastDistanceSqr = Double.MAX_VALUE;
        waypointIndex = 0;
        path = List.of();
        repath(agent, blackboard, cooldowns);
    }

    @Override
    public ActionOutcome<G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        var destination = blackboard.get(CommonBlackboardKeys.DESTINATION);
        if (destination == null) {
            CrawlController.setWallCrawling(agent, false);
            return ActionOutcome.failed();
        }

        if (agent.blockPosition().closerThan(destination, arrivalRadius)) {
            blackboard.remove(CommonBlackboardKeys.DESTINATION);
            CrawlController.setWallCrawling(agent, false);
            agent.setDeltaMovement(Vec3.ZERO);
            return ActionOutcome.success();
        }

        if (path.isEmpty() || waypointIndex >= path.size()) {
            if (!cooldowns.ready(repathCooldownKey)) {
                return ActionOutcome.running();
            }
            repath(agent, blackboard, cooldowns);
            if (path.isEmpty()) {
                return ActionOutcome.failed(PlanFailureReason.FAILED_NO_PATH, agent.blockPosition());
            }
        }

        var level = agent.level();
        var waypoint = path.get(waypointIndex);
        var waypointCenter = Vec3.atBottomCenterOf(waypoint);

        var isClimbNode = CollisionQueries.isSafeClimbNode(level, waypoint, agent)
            || CrawlTraversalEvaluator.tunnelCanStandAt(level, agent, waypoint)
            || CrawlTraversalEvaluator.verticalShaftCanCrawlAt(level, agent, waypoint);

        CrawlController.setWallCrawling(agent, isClimbNode && CrawlController.canWallCrawl(agent));

        var velocity = NavigationQueries.computeWallCrawlVelocity(agent, waypointCenter, speed);
        agent.setDeltaMovement(velocity);
        faceMovementDirection(agent, velocity);

        if (agent.position().closerThan(waypointCenter, 0.6D)) {
            waypointIndex++;
        }

        var distSq = agent.blockPosition().distSqr(destination);
        if (distSq < lastDistanceSqr - 0.25D) {
            lastDistanceSqr = distSq;
            ticksSinceProgress = 0;
        } else {
            ticksSinceProgress++;
        }

        if (ticksSinceProgress >= stuckTimeoutTicks) {
            CrawlController.setWallCrawling(agent, false);
            return ActionOutcome.failed(PlanFailureReason.FAILED_STUCK, agent.blockPosition());
        }

        return ActionOutcome.running();
    }

    private void repath(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        var destination = blackboard.get(CommonBlackboardKeys.DESTINATION);
        if (destination == null)
            return;

        cooldowns.set(repathCooldownKey, repathIntervalTicks);

        if (cache == null) {
            cache = new PathNodeCache();
        }
        cache.clear();

        path = pathfinder.findPath(
            agent,
            agent.blockPosition(),
            destination,
            64,
            Math.max(arrivalRadius, 1),
            cache
        );
        waypointIndex = 0;
    }

    @Override
    public void stop(E agent, Blackboard blackboard, CooldownTracker cooldowns, ActionStatus reason) {
        CrawlController.setWallCrawling(agent, false);
        agent.setDeltaMovement(Vec3.ZERO);
        path = List.of();
        waypointIndex = 0;
    }

    @Override
    public boolean isInterruptible() {
        return true;
    }

    @Override
    public int priority() {
        return 10;
    }

    /**
     * Turns the agent to face {@code movement}'s horizontal direction.
     * <p>
     * This action drives the agent by setting {@link Mob#setDeltaMovement} directly rather than going through vanilla
     * {@code PathNavigation}/{@code MoveControl} (see the class docs above for why), so nothing else updates
     * {@link Mob#setYRot}/{@code yBodyRot}/{@code yHeadRot} while it runs — without this, the agent keeps whatever
     * facing it happened to have when this action started (often backwards or perpendicular to its actual travel
     * direction) for as long as it keeps chasing. Mirrors {@code SwimAction#faceMovementDirection}, the other action in
     * this package that bypasses vanilla movement control the same way.
     * <p>
     * Has no effect on a purely vertical step (climbing straight up or down a wall, horizontal movement ~0) — same
     * limitation as {@code SwimAction}, since there is no meaningful horizontal facing to turn toward in that case, so
     * the agent simply keeps whatever heading it last had.
     */
    private void faceMovementDirection(E agent, Vec3 movement) {
        if (movement.horizontalDistanceSqr() < 0.0001D)
            return;
        var yaw = (float) (Math.atan2(movement.z, movement.x) * (180.0D / Math.PI)) - 90.0F;
        agent.setYRot(yaw);
        agent.yBodyRot = yaw;
        agent.yHeadRot = yaw;
        agent.getLookControl()
            .setLookAt(
                agent.getX() + movement.x,
                agent.getEyeY(),
                agent.getZ() + movement.z
            );
    }
}
