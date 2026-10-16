package com.azure.azurecortex.action.utility;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.Vec3d;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.action.ActionOutcome;
import com.azure.azurecortex.api.action.ActionStatus;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.runtime.CooldownTracker;
import com.azure.azurecortex.util.McCompat;

/**
 * A generic reference action that moves the agent directly away from whatever is currently stored under
 * {@link CommonBlackboardKeys#TARGET}.
 * <p>
 * Recomputes the flee direction every {@code repathIntervalTicks} ticks rather than every tick, since the exact
 * away-from-target point doesn't need to be pixel-perfect and re-pathing constantly is wasteful.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public class FleeAction<E extends EntityLiving, G> implements Action<E, G> {

    private final double speed;

    private final double fleeDistance;

    private final int repathIntervalTicks;

    private final String repathCooldownKey;

    public FleeAction(double speed, double fleeDistance, int repathIntervalTicks) {
        this.speed = speed;
        this.fleeDistance = fleeDistance;
        this.repathIntervalTicks = repathIntervalTicks;
        this.repathCooldownKey = "azurecortex:flee:" + System.identityHashCode(this);
    }

    @Override
    public void start(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        flee(agent, blackboard, cooldowns);
    }

    @Override
    public ActionOutcome<G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        EntityLivingBase threat = blackboard.get(CommonBlackboardKeys.TARGET);
        if (threat == null || !threat.isEntityAlive()) {
            return ActionOutcome.success();
        }

        if (cooldowns.ready(repathCooldownKey)) {
            flee(agent, blackboard, cooldowns);
        }

        return ActionOutcome.running();
    }

    private void flee(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        cooldowns.set(repathCooldownKey, repathIntervalTicks);

        EntityLivingBase threat = blackboard.get(CommonBlackboardKeys.TARGET);
        if (threat == null)
            return;

        Vec3d agentPos = McCompat.position(agent);
        Vec3d away = agentPos.subtract(McCompat.position(threat));
        if (McCompat.lengthSqr(away) < 0.0001D) {
            away = new Vec3d(1.0D, 0.0D, 0.0D);
        }

        Vec3d destination = agentPos.add(away.normalize().scale(fleeDistance));
        agent.getNavigator().tryMoveToXYZ(destination.x, destination.y, destination.z, speed);
    }

    @Override
    public void stop(E agent, Blackboard blackboard, CooldownTracker cooldowns, ActionStatus reason) {
        agent.getNavigator().clearPath();
    }

    @Override
    public boolean isInterruptible() {
        return true;
    }

    @Override
    public int priority() {
        return 15;
    }
}
