package com.azure.azurecortex.action.combat;

import net.minecraft.world.entity.Mob;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.action.ActionOutcome;
import com.azure.azurecortex.api.action.ActionStatus;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.goap.PlanFailureReason;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * A generic reference melee attack action: waits {@code windupTicks}, then attempts a single
 * {@link MeleeHitResolver#tryStrike} against whatever is stored under {@link CommonBlackboardKeys#TARGET}, then sets
 * {@code cooldownKey} for {@code cooldownTicks}.
 * <p>
 * Faces the target every tick throughout the windup (not just at the hit instant) via
 * {@code Mob#getLookControl().setLookAt}, the same thing {@link MeleeHitResolver#tryStrike} additionally does right
 * before a hit lands — without this, a mob with a windup longer than a tick or two visibly stands still without turning
 * to face what it's about to hit, which becomes noticeable the longer {@code windupTicks} is.
 * <p>
 * Reusable across differently-named attacks by constructing several instances with different windup/reach/cooldown
 * values and a distinct {@link #debugName()} label — see {@link AttackProfile}, which is the intended way to offer
 * several of these to a behavior tree at once.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public class TimedAttackAction<E extends Mob, G> implements Action<E, G> {

    private final String debugName;

    private final int windupTicks;

    private final double reach;

    private final String cooldownKey;

    private final int cooldownTicks;

    private int ticksElapsed;

    public TimedAttackAction(String debugName, int windupTicks, double reach, String cooldownKey, int cooldownTicks) {
        this.debugName = debugName;
        this.windupTicks = windupTicks;
        this.reach = reach;
        this.cooldownKey = cooldownKey;
        this.cooldownTicks = cooldownTicks;
    }

    @Override
    public void start(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        ticksElapsed = 0;
    }

    @Override
    public ActionOutcome<G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        var target = blackboard.get(CommonBlackboardKeys.TARGET);
        if (target == null || !target.isAlive()) {
            return ActionOutcome.failed(PlanFailureReason.FAILED_TARGET_LOST);
        }

        agent.getLookControl().setLookAt(target, 30.0F, 30.0F);

        ticksElapsed++;
        if (ticksElapsed < windupTicks) {
            return ActionOutcome.running();
        }

        var landed = MeleeHitResolver.tryStrike(agent, target, reach);
        cooldowns.set(cooldownKey, cooldownTicks);

        return landed ? ActionOutcome.success() : ActionOutcome.failed(PlanFailureReason.FAILED_BLOCKED);
    }

    @Override
    public void stop(E agent, Blackboard blackboard, CooldownTracker cooldowns, ActionStatus reason) {}

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public String debugName() {
        return debugName;
    }
}
