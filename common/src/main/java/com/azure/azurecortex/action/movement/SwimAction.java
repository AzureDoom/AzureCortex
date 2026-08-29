package com.azure.azurecortex.action.movement;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.action.ActionOutcome;
import com.azure.azurecortex.api.action.ActionStatus;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.navigation.traversal.TraversalQueries;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * A generic reference action for mobs caught in water or lava: swims toward whatever is currently stored under
 * {@link CommonBlackboardKeys#TARGET} (if {@link #pursueTarget} is set) or {@link CommonBlackboardKeys#DESTINATION},
 * and — if neither is present and the mob has been idly bobbing for a while — beelines for the nearest shore via
 * {@link TraversalQueries#findNearbyGroundPos}.
 * <p>
 * Succeeds immediately (nothing left to do) once the mob is no longer in water or lava, or if it has died. Reports
 * {@link ActionOutcome#running()} every other tick — this is meant to sit at a low priority in a behavior tree,
 * underneath whatever normal target-pursuit/combat actions the tree already offers, so it only actually drives movement
 * while nothing else has taken over.
 *
 * @param <E>          the agent type
 * @param <G>          the mod-defined goal-type enum
 * @param priority     this action's priority in the behavior tree
 * @param pursueTarget if {@code true}, swim toward {@link CommonBlackboardKeys#TARGET} when one is present (the right
 *                     choice for a predator that should keep closing on prey even while both are in water); if
 *                     {@code false}, ignore the target entirely and only ever swim toward
 *                     {@link CommonBlackboardKeys#DESTINATION} or the stranded-shore fallback
 */
@SuppressWarnings("unused")
public record SwimAction<E extends Mob, G>(
    int priority,
    boolean pursueTarget
) implements Action<E, G> {

    /**
     * Convenience constructor defaulting {@link #pursueTarget} to {@code true} — the common case for predator mobs
     * whose target is something to chase even while swimming.
     *
     * @param priority this action's priority in the behavior tree
     */
    public SwimAction(int priority) {
        this(priority, true);
    }

    /**
     * Ticks a mob must be continuously idle (in water, no target, no destination) before {@link #tick} starts actively
     * seeking the nearest shore instead of just leaving it to bob in place like normal idle behavior.
     */
    private static final int STRANDED_GRACE_TICKS = 60;

    @Override
    public void start(E mob, Blackboard blackboard, CooldownTracker cooldowns) {}

    @Override
    public ActionOutcome<G> tick(E mob, Blackboard blackboard, CooldownTracker cooldowns) {
        if (mob.isDeadOrDying())
            return ActionOutcome.success();
        if (!mob.isInWater() && !mob.isInLava())
            return ActionOutcome.success();

        var target = pursueTarget ? blackboard.get(CommonBlackboardKeys.TARGET) : null;
        var destinationPos = blackboard.get(CommonBlackboardKeys.DESTINATION);

        var destPos = target != null && target.isAlive()
            ? target.position().add(0, target.getBbHeight() * 0.5, 0)
            : destinationPos != null
                ? Vec3.atBottomCenterOf(destinationPos)
                : null;

        if (destPos == null) {
            var now = (int) mob.level.getGameTime();
            var strandedSince = blackboard.get(CommonBlackboardKeys.SWIM_STRANDED_SINCE_TICK);

            if (strandedSince == null) {
                blackboard.set(CommonBlackboardKeys.SWIM_STRANDED_SINCE_TICK, now);
            } else if (now - strandedSince >= STRANDED_GRACE_TICKS) {
                var shore = TraversalQueries.findNearbyGroundPos(mob);
                if (shore != null) {
                    destPos = Vec3.atBottomCenterOf(shore);
                }
            }
        } else {
            blackboard.remove(CommonBlackboardKeys.SWIM_STRANDED_SINCE_TICK);
        }

        if (destPos != null) {
            var toTarget = destPos.subtract(mob.position());
            var dist = toTarget.length();

            if (dist > 0.5D) {
                var movement = toTarget.normalize().scale(0.22D);

                var climbingLedge = false;
                if (mob.horizontalCollision) {
                    var liftedBox = mob.getBoundingBox().move(0.0D, 0.6D, 0.0D);
                    if (mob.level.noCollision(mob, liftedBox)) {
                        movement = new Vec3(movement.x, 0.5D, movement.z);
                        climbingLedge = true;
                    }
                }

                mob.setDeltaMovement(movement);
                mob.hasImpulse = true;
                faceMovementDirection(mob, movement);

                if (climbingLedge) {
                    mob.setDeltaMovement(mob.getDeltaMovement().x * 0.8, movement.y, mob.getDeltaMovement().z * 0.8);
                    return ActionOutcome.running();
                }
            } else {
                mob.setDeltaMovement(Vec3.ZERO);
            }
        } else {
            var current = mob.getDeltaMovement();
            mob.setDeltaMovement(current.x * 0.5, 0.03, current.z * 0.5);
        }

        mob.setDeltaMovement(mob.getDeltaMovement().multiply(0.8, 0.8, 0.8));
        return ActionOutcome.running();
    }

    @Override
    public void stop(E mob, Blackboard blackboard, CooldownTracker cooldowns, ActionStatus reason) {}

    @Override
    public boolean isInterruptible() {
        return true;
    }

    private void faceMovementDirection(E mob, Vec3 movement) {
        if (movement.horizontalDistanceSqr() < 0.0001D)
            return;
        var yaw = (float) (Math.atan2(movement.z, movement.x) * (180.0D / Math.PI)) - 90.0F;
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.yHeadRot = yaw;
        mob.getLookControl()
            .setLookAt(
                mob.getX() + movement.x,
                mob.getEyeY(),
                mob.getZ() + movement.z
            );
    }
}
