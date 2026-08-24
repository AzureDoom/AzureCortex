package com.azure.azurecortex.goap;

import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;

import com.azure.azurecortex.api.goal.GoalUrgency;

/**
 * A cheap, pre-planner check for genuine emergencies (on fire, imminent explosion, critical health, or any mod-specific
 * equivalent), used to supply {@link GoalExecutor#shouldReplan} with a {@code candidateUrgency} <em>before</em> the
 * full {@code GoalPlanner.chooseGoal} has run.
 * <h3>Why this exists</h3> {@link GoalExecutor#shouldReplan} advertises an emergency override that bypasses the
 * min-commit lock, but that override only fires when the caller passes {@link GoalUrgency#EMERGENCY} as
 * {@code candidateUrgency}. Scoring candidates requires running the planner — which {@code shouldReplan} exists to gate
 * in the first place. Without a cheap upstream probe, the emergency override is advertised but effectively unreachable:
 * nothing ever supplies a non-null, non-default urgency until after the planner (which might not even be invoked) has
 * already run.
 * <p>
 * This class breaks that chicken-and-egg problem with a handful of cheap, pluggable {@link EmergencyProbe} checks. None
 * of them allocate a full candidate list or score goals; they are meant to be safe to call every tick. AzureCortex
 * ships one built-in probe ({@link #criticalHealth}); everything else (fire, nearby explosives, ...) is inherently
 * mod-specific and should be supplied by the consuming mod via {@link #detectPreplanUrgency(Mob, List)}.
 */
@SuppressWarnings("unused")
public final class EmergencyDetector {

    private EmergencyDetector() {}

    /** Health fraction at or below which the agent is considered critically wounded regardless of active goal. */
    private static final float CRITICAL_HEALTH_FRACTION = 0.15f;

    /**
     * A single cheap emergency check. Implementations should be O(1) or a small bounded scan — this is called every
     * tick for every agent that wants pre-planner emergency detection.
     *
     * @param <E> the agent type
     */
    @FunctionalInterface
    public interface EmergencyProbe<E extends Mob> {

        /**
         * @param agent the agent to probe
         * @return {@code true} if this probe detects an emergency condition for {@code agent}
         */
        boolean isEmergency(E agent);
    }

    /**
     * Built-in probe: {@code true} when the agent's health fraction is at or below {@value #CRITICAL_HEALTH_FRACTION}.
     */
    public static <E extends Mob> EmergencyProbe<E> criticalHealth() {
        return agent -> agent.getMaxHealth() > 0f && agent.getHealth() <= agent.getMaxHealth()
            * CRITICAL_HEALTH_FRACTION;
    }

    /** Built-in probe: {@code true} when the agent is currently on fire. */
    public static <E extends Mob> EmergencyProbe<E> onFire() {
        return Mob::isOnFire;
    }

    /**
     * Convenience: bundles {@link #criticalHealth()} and {@link #onFire()}, the two conditions that apply to virtually
     * every mob regardless of mod. Mods should extend this list with their own probes (nearby explosives, a specific
     * predator type, etc.) via {@link #detectPreplanUrgency(Mob, List)}.
     */
    public static <E extends Mob> List<EmergencyProbe<E>> defaultProbes() {
        var probes = new ArrayList<EmergencyProbe<E>>();
        probes.add(onFire());
        probes.add(criticalHealth());
        return probes;
    }

    /**
     * Returns {@link GoalUrgency#EMERGENCY} if any of {@code probes} detects an emergency condition for {@code agent};
     * otherwise returns {@code null} (meaning "unknown/not emergency", so the caller should fall back to its normal
     * replan cadence rather than treating this as a low-urgency result).
     *
     * @param agent  the agent to probe
     * @param probes the emergency checks to run, in order; stops at the first match
     * @param <E>    the agent type
     * @return {@link GoalUrgency#EMERGENCY}, or {@code null} if no emergency condition was detected
     */
    public static <E extends Mob> GoalUrgency detectPreplanUrgency(E agent, List<EmergencyProbe<E>> probes) {
        if (!agent.isAlive())
            return null;

        for (var probe : probes) {
            if (probe.isEmergency(agent))
                return GoalUrgency.EMERGENCY;
        }

        return null;
    }
}
