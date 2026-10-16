package com.azure.azurecortex.action.movement;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.action.ActionOutcome;
import com.azure.azurecortex.api.action.ActionStatus;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.navigation.traversal.TraversalQueries;
import com.azure.azurecortex.runtime.CooldownTracker;
import com.azure.azurecortex.util.McCompat;

/**
 * A generic reference action for mobs caught in water or lava: swims toward whatever is currently stored under
 * {@link CommonBlackboardKeys#TARGET} (if {@link #pursueTarget()} is set) or {@link CommonBlackboardKeys#DESTINATION},
 * and — if neither is present and the mob has been idly bobbing for a while — beelines for the nearest shore via
 * {@link TraversalQueries#findNearbyGroundPos}.
 * <p>
 * Succeeds immediately (nothing left to do) once the mob is no longer in water or lava, or if it has died. Reports
 * {@link ActionOutcome#running()} every other tick — this is meant to sit at a low priority in a behavior tree,
 * underneath whatever normal target-pursuit/combat actions the tree already offers, so it only actually drives movement
 * while nothing else has taken over.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public final class SwimAction<E extends EntityLiving, G> implements Action<E, G> {

    /**
     * Ticks a mob must be continuously idle (in water, no target, no destination) before {@link #tick} starts actively
     * seeking the nearest shore instead of just leaving it to bob in place like normal idle behavior.
     */
    private static final int STRANDED_GRACE_TICKS = 60;

    private final int priority;

    private final boolean pursueTarget;

    /**
     * @param priority     this action's priority in the behavior tree
     * @param pursueTarget if {@code true}, swim toward {@link CommonBlackboardKeys#TARGET} when one is present (the
     *                     right choice for a predator that should keep closing on prey even while both are in water);
     *                     if {@code false}, ignore the target entirely and only ever swim toward
     *                     {@link CommonBlackboardKeys#DESTINATION} or the stranded-shore fallback
     */
    public SwimAction(int priority, boolean pursueTarget) {
        this.priority = priority;
        this.pursueTarget = pursueTarget;
    }

    /**
     * Convenience constructor defaulting {@link #pursueTarget()} to {@code true} — the common case for predator mobs
     * whose target is something to chase even while swimming.
     *
     * @param priority this action's priority in the behavior tree
     */
    public SwimAction(int priority) {
        this(priority, true);
    }

    public boolean pursueTarget() {
        return pursueTarget;
    }

    @Override
    public void start(E mob, Blackboard blackboard, CooldownTracker cooldowns) {}

    @Override
    public ActionOutcome<G> tick(E mob, Blackboard blackboard, CooldownTracker cooldowns) {
        if (mob.getHealth() <= 0.0F || mob.isDead)
            return ActionOutcome.success();
        if (!mob.isInWater() && !mob.isInLava())
            return ActionOutcome.success();

        EntityLivingBase target = pursueTarget ? blackboard.get(CommonBlackboardKeys.TARGET) : null;
        BlockPos destinationPos = blackboard.get(CommonBlackboardKeys.DESTINATION);

        Vec3d destPos;
        if (target != null && target.isEntityAlive()) {
            destPos = new Vec3d(target.posX, target.posY + target.height * 0.5D, target.posZ);
        } else if (destinationPos != null) {
            destPos = McCompat.atBottomCenterOf(destinationPos);
        } else {
            destPos = null;
        }

        if (destPos == null) {
            int now = (int) mob.world.getTotalWorldTime();
            Integer strandedSince = blackboard.get(CommonBlackboardKeys.SWIM_STRANDED_SINCE_TICK);

            if (strandedSince == null) {
                blackboard.set(CommonBlackboardKeys.SWIM_STRANDED_SINCE_TICK, now);
            } else if (now - strandedSince >= STRANDED_GRACE_TICKS) {
                BlockPos shore = TraversalQueries.findNearbyGroundPos(mob);
                if (shore != null) {
                    destPos = McCompat.atBottomCenterOf(shore);
                }
            }
        } else {
            blackboard.remove(CommonBlackboardKeys.SWIM_STRANDED_SINCE_TICK);
        }

        if (destPos != null) {
            Vec3d toTarget = destPos.subtract(McCompat.position(mob));
            double dist = McCompat.length(toTarget);

            if (dist > 0.5D) {
                Vec3d movement = toTarget.normalize().scale(0.22D);

                boolean climbingLedge = false;
                if (mob.collidedHorizontally) {
                    AxisAlignedBB liftedBox = mob.getEntityBoundingBox().offset(0.0D, 0.6D, 0.0D);
                    if (mob.world.getCollisionBoxes(mob, liftedBox).isEmpty()) {
                        movement = new Vec3d(movement.x, 0.5D, movement.z);
                        climbingLedge = true;
                    }
                }

                McCompat.setMotion(mob, movement);
                mob.isAirBorne = true;
                faceMovementDirection(mob, movement);

                if (climbingLedge) {
                    McCompat.setMotion(mob, mob.motionX * 0.8D, movement.y, mob.motionZ * 0.8D);
                    return ActionOutcome.running();
                }
            } else {
                McCompat.setMotion(mob, Vec3d.ZERO);
            }
        } else {
            McCompat.setMotion(mob, mob.motionX * 0.5D, 0.03D, mob.motionZ * 0.5D);
        }

        McCompat.setMotion(mob, mob.motionX * 0.8D, mob.motionY * 0.8D, mob.motionZ * 0.8D);
        return ActionOutcome.running();
    }

    @Override
    public void stop(E mob, Blackboard blackboard, CooldownTracker cooldowns, ActionStatus reason) {}

    @Override
    public boolean isInterruptible() {
        return true;
    }

    @Override
    public int priority() {
        return priority;
    }

    private void faceMovementDirection(E mob, Vec3d movement) {
        if (McCompat.horizontalLengthSqr(movement) < 0.0001D)
            return;
        float yaw = (float) (Math.atan2(movement.z, movement.x) * (180.0D / Math.PI)) - 90.0F;
        mob.rotationYaw = yaw;
        mob.renderYawOffset = yaw;
        mob.rotationYawHead = yaw;
        // 10 / 40 match the newer versions' default head rotation speed / max head pitch for a plain mob.
        mob.getLookHelper()
            .setLookPosition(
                mob.posX + movement.x,
                mob.posY + mob.getEyeHeight(),
                mob.posZ + movement.z,
                10.0F,
                40.0F
            );
    }
}
