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
import com.azure.azurecortex.navigation.astar.AStarPathfinder;
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

        var level = agent.level;
        var waypoint = path.get(waypointIndex);
        var waypointCenter = Vec3.atBottomCenterOf(waypoint);

        var isClimbNode = CollisionQueries.isSafeClimbNode(level, waypoint, agent)
            || CrawlTraversalEvaluator.tunnelCanStandAt(level, agent, waypoint)
            || CrawlTraversalEvaluator.verticalShaftCanCrawlAt(level, agent, waypoint);

        var wallCrawling = isClimbNode && CrawlController.canWallCrawl(agent);
        CrawlController.setWallCrawling(agent, wallCrawling);

        var velocity = wallCrawling
            ? NavigationQueries.computeWallCrawlVelocity(agent, waypointCenter, speed)
            : computeGroundVelocity(agent, waypointCenter, speed);
        agent.setDeltaMovement(velocity);
        agent.hasImpulse = true;
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

        var radius = Math.max(arrivalRadius, 1);

        path = pathfinder.findPath(
            agent,
            agent.blockPosition(),
            destination,
            64,
            radius,
            cache
        );

        if (path.isEmpty() || !path.get(path.size() - 1).closerThan(destination, radius + 4)) {
            fallBackToGroundPathIfBetter(agent, destination, radius);
        }

        waypointIndex = 0;
    }

    /**
     * Retries with the plain ground-walking pathfinder when {@link CrawlTraversalEvaluator}'s search either failed
     * outright or only managed a partial path that doesn't get meaningfully close to {@code destination}.
     * <p>
     * The crawl-aware search's neighbor generation is tuned for genuine wall/ceiling climbs — every node near any
     * naturally uneven terrain (a hillside step, a tree root) still costs far more than a plain ground step, and a long
     * enough climb or elevation change can still exhaust its search budget even after the branching and partial-path
     * fixes elsewhere in this class's supporting code. {@code AStarPathfinder} has none of that overhead and reaches
     * ordinary terrain — including hills — reliably. Mirrors the same fallback the Ovomorphosis mod's own (much more
     * elaborate) crawl-aware movement action already relies on for exactly this reason: always try the crawl-aware
     * search first since only it knows how to route over walls and ceilings, but don't let its fragility on ordinary
     * terrain leave the agent stuck when a plain walk would have gotten there fine.
     */
    private void fallBackToGroundPathIfBetter(E agent, BlockPos destination, int radius) {
        var groundPath = AStarPathfinder.INSTANCE.findPath(agent, agent.blockPosition(), destination, 64, radius);

        if (groundPath.isEmpty()) {
            return;
        }

        if (path.isEmpty()) {
            path = groundPath;
            return;
        }

        var groundEndDistSqr = groundPath.get(groundPath.size() - 1).distSqr(destination);
        var crawlEndDistSqr = path.get(path.size() - 1).distSqr(destination);

        if (groundEndDistSqr < crawlEndDistSqr) {
            path = groundPath;
        }
    }

    /**
     * Computes velocity for an ordinary (non-climb) waypoint: horizontal steering, an explicit jump impulse over a
     * detected low step, and a floor on descent speed — leaving gravity to handle everything else.
     * <p>
     * {@link NavigationQueries#computeWallCrawlVelocity} aims a full 3D vector straight at the waypoint, which is
     * correct only while actually wall-crawling — gravity is suppressed then, and this class's {@code travel()}
     * override applies that velocity directly, bypassing vanilla physics. Off a wall, movement goes through vanilla's
     * ordinary gravity-driven {@code travel()} instead, and passive step-height assist turns out not to be reliable for
     * an agent whose deltaMovement is being set manually every tick rather than driven through vanilla's own
     * {@code MoveControl}/{@code PathNavigation} — Ovomorphosis's own (much more battle-tested) crawl-aware movement
     * action hits this same issue and works around it identically: explicitly detect a low step immediately ahead (feet
     * blocked, head clear, one block above that clear) and fire a real jump impulse over it — matching vanilla's own
     * default jump power (0.42) — rather than trusting the passive assist to engage. Descending gets the same treatment
     * in miniature: a floor on downward velocity so the agent doesn't hang waiting for gravity to slowly build up speed
     * from zero at every ledge.
     */
    private Vec3 computeGroundVelocity(E agent, Vec3 waypointCenter, double speed) {
        var current = agent.position();
        var horizontal = new Vec3(waypointCenter.x - current.x, 0.0D, waypointCenter.z - current.z);
        var dist = horizontal.length();

        var existingY = agent.getDeltaMovement().y;

        if (dist < 0.05D) {
            return new Vec3(0.0D, existingY, 0.0D);
        }

        var forward = horizontal.normalize();
        var steer = forward.scale(Math.min(speed, dist));

        if (agent.isOnGround() && isLowStepAhead(agent, forward)) {
            return new Vec3(steer.x, 0.42D, steer.z);
        }

        if (waypointCenter.y < current.y - 0.1D) {
            return new Vec3(steer.x, Math.min(existingY, -0.15D), steer.z);
        }

        return new Vec3(steer.x, existingY, steer.z);
    }

    /**
     * Returns {@code true} if there's a single-block-tall obstruction directly ahead that a jump would clear: solid at
     * feet height, clear at head height, and clear again one block above that (so jumping onto it doesn't just trade
     * one obstruction for another). Loosely ported from Ovomorphosis's {@code MoveToTargetAction#isStairBlockAhead} —
     * the name there reflects its main trigger case, but the check itself is general to any single-block ledge, not
     * just literal stair blocks.
     * <p>
     * Unlike the original, this scales its look-ahead distance to the agent's own half-width and samples across the
     * full width (mirroring {@code TraversalQueries#isSafeAhead}'s pattern) instead of a single fixed-offset point on
     * the center line. Ovomorphosis's xenomorph is narrow enough (~0.47 half-width) that a single point 0.6 blocks
     * ahead is a reasonable stand-in for its whole body; the spider's half-width (~0.72) is wider than that fixed
     * offset, so the original version could sample a point still under the spider's own belly rather than genuinely
     * ahead of it, and never checked anything off the center line at all — missing a step that only obstructed one side
     * of a body this wide. That's why the jump wasn't reliably firing: the stall you were seeing was actually the
     * unrelated stuck-timeout (60 ticks ≈ 3 seconds, set in {@code CortexSpiderTree}) eventually forcing a repath, not
     * this check succeeding.
     */
    private boolean isLowStepAhead(E agent, Vec3 forward) {
        var level = agent.level;
        var halfWidth = (agent.getBbWidth() / 2.0D) + 0.02D;
        var lookAhead = halfWidth + 0.1D;
        var side = new Vec3(-forward.z, 0.0D, forward.x);
        var feetY = agent.getBoundingBox().minY;
        var aheadPos = agent.position().add(forward.scale(lookAhead));

        for (var s = -halfWidth; s <= halfWidth; s += Math.max(halfWidth, 0.01D)) {
            var checkPos = aheadPos.add(side.scale(s));
            var feet = new BlockPos(checkPos.x, feetY, checkPos.z);
            var head = feet.above();

            var feetState = level.getBlockState(feet);
            var headState = level.getBlockState(head);

            if (feetState.getCollisionShape(level, feet).isEmpty())
                continue;
            if (!headState.getCollisionShape(level, head).isEmpty())
                continue;

            var landing = head.above();
            if (level.getBlockState(landing).getCollisionShape(level, landing).isEmpty()) {
                return true;
            }
        }

        return false;
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
