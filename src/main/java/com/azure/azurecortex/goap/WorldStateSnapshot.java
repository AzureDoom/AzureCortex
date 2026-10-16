package com.azure.azurecortex.goap;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

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
 */
public final class WorldStateSnapshot {

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

    private final int targetId;

    private final DistanceBucket distanceBucket;

    private final HealthBucket healthBucket;

    private final boolean inDarkness;

    /**
     * @param targetId       the entity id of {@link CommonBlackboardKeys#TARGET} at commit time, or {@code -1} if there
     *                       was none
     * @param distanceBucket which {@link DistanceBucket} the target fell into at commit time (irrelevant if
     *                       {@code targetId == -1})
     * @param healthBucket   which {@link HealthBucket} the agent's health fraction fell into at commit time
     * @param inDarkness     whether the agent's ambient light was at/below the "dark" threshold at commit time
     */
    public WorldStateSnapshot(
        int targetId,
        DistanceBucket distanceBucket,
        HealthBucket healthBucket,
        boolean inDarkness
    ) {
        this.targetId = targetId;
        this.distanceBucket = distanceBucket;
        this.healthBucket = healthBucket;
        this.inDarkness = inDarkness;
    }

    public int targetId() {
        return targetId;
    }

    public DistanceBucket distanceBucket() {
        return distanceBucket;
    }

    public HealthBucket healthBucket() {
        return healthBucket;
    }

    public boolean inDarkness() {
        return inDarkness;
    }

    /**
     * Captures the current world-state facts for {@code mob}, for storage on the blackboard the moment a plan is
     * committed.
     */
    public static WorldStateSnapshot capture(EntityLiving mob, Blackboard blackboard) {
        EntityLivingBase target = blackboard.get(CommonBlackboardKeys.TARGET);
        boolean hasTarget = target != null && target.isEntityAlive();

        int targetId = hasTarget ? target.getEntityId() : -1;
        DistanceBucket distanceBucket = hasTarget
            ? DistanceBucket.of(mob.getDistanceSq(target))
            : DistanceBucket.NONE;

        float maxHealth = mob.getMaxHealth();
        float healthFraction = maxHealth > 0f ? mob.getHealth() / maxHealth : 1f;
        HealthBucket healthBucket = HealthBucket.of(healthFraction);

        boolean inDarkness = mob.world.getLightFromNeighbors(new BlockPos(mob)) <= DARKNESS_LIGHT_THRESHOLD;

        return new WorldStateSnapshot(targetId, distanceBucket, healthBucket, inDarkness);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof WorldStateSnapshot))
            return false;
        WorldStateSnapshot other = (WorldStateSnapshot) o;
        return targetId == other.targetId && distanceBucket == other.distanceBucket
            && healthBucket == other.healthBucket && inDarkness == other.inDarkness;
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetId, distanceBucket, healthBucket, inDarkness);
    }

    @Override
    public String toString() {
        return "WorldStateSnapshot[targetId=" + targetId + ", distanceBucket=" + distanceBucket + ", healthBucket="
            + healthBucket + ", inDarkness=" + inDarkness + "]";
    }
}
