package com.azure.azurecortex.action.movement;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.action.ActionOutcome;
import com.azure.azurecortex.api.action.ActionStatus;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * A generic reference wander action: periodically picks a random nearby point and walks toward it, restarting when
 * reached or after a maximum duration.
 * <p>
 * This is a minimal, mod-agnostic implementation meant as a starting point — it has no notion of "prefer dark areas" or
 * any other mod-specific bias. A mod wanting that should subclass or wrap this with its own destination-picking logic
 * (e.g. bias {@link #pickDestination} candidates toward low-light positions), following the same shape.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public class WanderAction<E extends Mob, G> implements Action<E, G> {

    private final double speed;

    private final double radius;

    private final int maxDurationTicks;

    private int ticksRunning;

    private Vec3 destination;

    /**
     * @param speed            navigation speed modifier passed to the entity's move control
     * @param radius           how far (blocks) from the agent's current position to pick a destination
     * @param maxDurationTicks give up and re-pick a destination after this many ticks even if not yet arrived
     */
    public WanderAction(double speed, double radius, int maxDurationTicks) {
        this.speed = speed;
        this.radius = radius;
        this.maxDurationTicks = maxDurationTicks;
    }

    @Override
    public void start(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        ticksRunning = 0;
        destination = pickDestination(agent);
        if (destination != null) {
            agent.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
        }
    }

    @Override
    public ActionOutcome<G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        ticksRunning++;

        if (destination == null || agent.getNavigation().isDone()) {
            if (ticksRunning >= maxDurationTicks) {
                return ActionOutcome.success();
            }
            destination = pickDestination(agent);
            if (destination != null) {
                agent.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
            }
        }

        if (ticksRunning >= maxDurationTicks) {
            return ActionOutcome.success();
        }

        return ActionOutcome.running();
    }

    @Override
    public void stop(E agent, Blackboard blackboard, CooldownTracker cooldowns, ActionStatus reason) {
        agent.getNavigation().stop();
        destination = null;
    }

    /**
     * Picks a random destination within {@link #radius} blocks of the agent. Override to bias candidates toward
     * whatever conditions matter for a given mod (darkness, proximity to a hive, avoiding open water, ...).
     *
     * @param agent the agent to pick a destination for
     * @return a candidate destination, or {@code null} if none could be found this attempt
     */
    protected Vec3 pickDestination(E agent) {
        var random = agent.getRandom();
        var angle = random.nextFloat() * (float) (Math.PI * 2);
        var distance = radius * (0.3D + random.nextDouble() * 0.7D);

        var dx = Mth.cos(angle) * distance;
        var dz = Mth.sin(angle) * distance;

        return agent.position().add(dx, 0.0D, dz);
    }

    @Override
    public boolean isInterruptible() {
        return true;
    }

    @Override
    public int priority() {
        return 5;
    }
}
