package com.azure.azurecortex.goap;

import net.minecraft.world.entity.Mob;

import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;

/**
 * A cheap, goal-agnostic snapshot of "the facts that justified committing to the current plan", taken once when a plan
 * is committed ({@code GoalExecutor#apply}) and re-derived from live state every tick by {@link PlanInvalidation}.
 * <h3>Why this exists</h3> {@code ActionOutcome.Blocked}/{@code ActionOutcome.Failed} feedback is <em>reactive</em>: it
 * only exists when the currently running action itself notices, on its own tick, that something has gone wrong. Nothing
 * upstream of the action ever asks "does the world still look like it did when this plan was chosen?" — so a plan whose
 * founding assumption silently stopped being true just keeps running, unquestioned, until the action itself trips over
 * it or the commit timer expires. {@link WorldStateSnapshot} plus {@link PlanInvalidation} sit <em>above</em> that
 * action-feedback loop: they compare a handful of coarse, universally-applicable facts against what was true at commit
 * time and force an early replan the moment they diverge, independent of whether any action noticed anything.
 * <p>
 * Deliberately coarse and deliberately goal-agnostic — this is a cheap tripwire, not a substitute for planner-specific
 * scoring. Buckets are wide on purpose so ordinary noise (a target stepping half a block, a torch flickering) never
 * fires it; it should only trip on changes big enough that "does the plan still make sense" is a legitimate question.
 *
 * @param targetId       the entity id of {@link CommonBlackboardKeys#TARGET} at commit time, or {@code -1} if there was
 *                       none
 * @param distanceBucket which {@link DistanceBucket} the target fell into at commit time (irrelevant if
 *                       {@code targetId == -1})
 * @param healthBucket   which {@link HealthBucket} the agent's health fraction fell into at commit time
 * @param inDarkness     whether the agent's ambient light was at/below the "dark" threshold at commit time
 */
public record WorldStateSnapshot(
    int targetId,
    DistanceBucket distanceBucket,
    HealthBucket healthBucket,
    boolean inDarkness
) {

    /** Ambient light at/below this is considered "dark". */
    public static final int DARKNESS_LIGHT_THRESHOLD = 4;

    public enum DistanceBucket {

        NONE,
        CLOSE,
        MEDIUM,
        FAR;

        static DistanceBucket of(double distSq) {
            if (distSq <= 6.0 * 6.0)
                return CLOSE;
            if (distSq <= 16.0 * 16.0)
                return MEDIUM;
            return FAR;
        }
    }

    public enum HealthBucket {

        CRITICAL,
        WOUNDED,
        HEALTHY;

        static HealthBucket of(float fraction) {
            if (fraction <= 0.30f)
                return CRITICAL;
            if (fraction <= 0.60f)
                return WOUNDED;
            return HEALTHY;
        }
    }

    /**
     * Captures the current world-state facts for {@code mob}, for storage on the blackboard the moment a plan is
     * committed.
     */
    public static WorldStateSnapshot capture(Mob mob, Blackboard blackboard) {
        var target = blackboard.get(CommonBlackboardKeys.TARGET);
        var hasTarget = target != null && target.isAlive();

        var targetId = hasTarget ? target.getId() : -1;
        var distanceBucket = hasTarget
            ? DistanceBucket.of(mob.distanceToSqr(target))
            : DistanceBucket.NONE;

        var maxHealth = mob.getMaxHealth();
        var healthFraction = maxHealth > 0f ? mob.getHealth() / maxHealth : 1f;
        var healthBucket = HealthBucket.of(healthFraction);

        var inDarkness = mob.level.getMaxLocalRawBrightness(mob.blockPosition()) <= DARKNESS_LIGHT_THRESHOLD;

        return new WorldStateSnapshot(targetId, distanceBucket, healthBucket, inDarkness);
    }
}
