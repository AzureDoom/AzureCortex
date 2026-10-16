package com.azure.azurecortex.sensing;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;

/**
 * Periodically evaluates a {@link Selector} and writes the result to the blackboard so that actions can read it via
 * {@link CommonBlackboardKeys#TARGET}.
 * <p>
 * The selector is called every {@code retargetInterval} ticks, or immediately when the current target is no longer
 * alive. The last known position of a living target is always kept up to date under
 * {@link CommonBlackboardKeys#LAST_KNOWN_TARGET_POS}.
 * <h3>Visibility-gated last-seen tracking</h3> When constructed with a {@link #visibilityPredicate}, this sensor also
 * maintains {@link CommonBlackboardKeys#LAST_SEEN_POS}/{@link CommonBlackboardKeys#LAST_SEEN_VELOCITY}/
 * {@link CommonBlackboardKeys#LAST_SEEN_TICK} — updated only on ticks where the predicate accepts the current target,
 * as opposed to {@link CommonBlackboardKeys#LAST_KNOWN_TARGET_POS}, which updates unconditionally. This is what lets
 * {@code com.azure.azurecortex.sensing.TargetPrediction} (and actions built on it, e.g.
 * {@code InvestigateLastSeenTargetAction}) extrapolate a believable search point instead of only ever walking to
 * wherever the target happens to be right now. The predicate is intentionally pluggable rather than a fixed
 * line-of-sight check, since not every mod's vision rules are ordinary line-of-sight — see {@link #lineOfSight()} for
 * the common case. Agents constructed without a predicate never populate these three keys.
 * <h3>Velocity on 1.12.2</h3> The server-side {@code motionX/Z} of a player is not reliable on 1.12.2 (player movement
 * is client-authoritative and arrives as position packets), so instead of reading the target's motion this sensor
 * samples the target's horizontal position each time it ticks and derives blocks-per-tick from the difference. The
 * stored {@link CommonBlackboardKeys#LAST_SEEN_VELOCITY} always has {@code y == 0}.
 *
 * @param <E> the agent type this sensor serves
 */
@SuppressWarnings("unused")
public final class TargetSensor<E> implements Sensor<E> {

    /** Samples further apart than this are too coarse to derive a meaningful velocity from; the sample restarts. */
    private static final int MAX_VELOCITY_SAMPLE_GAP_TICKS = 10;

    /**
     * Strategy interface for selecting an agent's current target. Implementations are supplied to {@link TargetSensor}
     * and called periodically to refresh the target stored under {@link CommonBlackboardKeys#TARGET}.
     *
     * @param <E> the agent type performing the search
     */
    @FunctionalInterface
    public interface Selector<E> {

        /**
         * @param agent      the agent searching for a target
         * @param blackboard the agent's shared AI state store
         * @return the selected target, or {@code null} if no valid target can be found
         */
        EntityLivingBase findTarget(E agent, Blackboard blackboard);
    }

    private final Selector<E> selector;

    private final int retargetInterval;

    @Nullable
    private final BiPredicate<E, EntityLivingBase> visibilityPredicate;

    private int age;

    private int sampledEntityId = -1;

    private long sampledTick;

    private double sampledX;

    private double sampledZ;

    private Vec3d sampledVelocity = Vec3d.ZERO;

    /**
     * Creates a new target sensor with no visibility gating — {@link CommonBlackboardKeys#LAST_SEEN_POS} and its paired
     * velocity/tick keys are never written.
     *
     * @param selector         the strategy used to find a target
     * @param retargetInterval number of ticks between forced re-evaluations of the target
     */
    public TargetSensor(Selector<E> selector, int retargetInterval) {
        this(selector, retargetInterval, null);
    }

    /**
     * Creates a new target sensor.
     *
     * @param selector            the strategy used to find a target
     * @param retargetInterval    number of ticks between forced re-evaluations of the target
     * @param visibilityPredicate tested against the current agent/target every tick; while it accepts, this sensor also
     *                            updates {@link CommonBlackboardKeys#LAST_SEEN_POS}/
     *                            {@link CommonBlackboardKeys#LAST_SEEN_VELOCITY}/
     *                            {@link CommonBlackboardKeys#LAST_SEEN_TICK}. Pass {@code null} to skip last-seen
     *                            tracking entirely (equivalent to the other constructor). See {@link #lineOfSight()}
     *                            for an ordinary line-of-sight predicate.
     */
    public TargetSensor(
        Selector<E> selector,
        int retargetInterval,
        @Nullable BiPredicate<E, EntityLivingBase> visibilityPredicate
    ) {
        this.selector = selector;
        this.retargetInterval = retargetInterval;
        this.visibilityPredicate = visibilityPredicate;
    }

    @Override
    public void tick(E agent, Blackboard blackboard) {
        age++;

        EntityLivingBase current = blackboard.get(CommonBlackboardKeys.TARGET);

        if (current != null && current.isEntityAlive()) {
            BlockPos currentPos = new BlockPos(current);
            blackboard.set(CommonBlackboardKeys.LAST_KNOWN_TARGET_POS, currentPos);

            if (visibilityPredicate != null) {
                long now = current.world.getTotalWorldTime();
                Vec3d velocity = sampleVelocity(current, now);

                if (visibilityPredicate.test(agent, current)) {
                    blackboard.set(CommonBlackboardKeys.LAST_SEEN_POS, currentPos);
                    blackboard.set(CommonBlackboardKeys.LAST_SEEN_VELOCITY, velocity);
                    blackboard.set(CommonBlackboardKeys.LAST_SEEN_TICK, (int) now);
                }
            }
        }

        if (age % retargetInterval != 0 && current != null && current.isEntityAlive()) {
            return;
        }

        EntityLivingBase target = selector.findTarget(agent, blackboard);

        if (target != null) {
            blackboard.set(CommonBlackboardKeys.TARGET, target);
        } else {
            blackboard.remove(CommonBlackboardKeys.TARGET);
        }
    }

    /**
     * Updates the position sample for {@code target} and returns its horizontal velocity in blocks per tick, or the
     * previous estimate if no time has passed since the last sample.
     */
    private Vec3d sampleVelocity(EntityLivingBase target, long now) {
        long elapsed = now - sampledTick;

        if (target.getEntityId() != sampledEntityId || elapsed < 0 || elapsed > MAX_VELOCITY_SAMPLE_GAP_TICKS) {
            sampledVelocity = Vec3d.ZERO;
        } else if (elapsed > 0) {
            sampledVelocity = new Vec3d(
                (target.posX - sampledX) / elapsed,
                0.0D,
                (target.posZ - sampledZ) / elapsed
            );
        }

        if (elapsed != 0 || target.getEntityId() != sampledEntityId) {
            sampledEntityId = target.getEntityId();
            sampledTick = now;
            sampledX = target.posX;
            sampledZ = target.posZ;
        }

        return sampledVelocity;
    }

    /**
     * Builds a {@link Selector} that retains the current target if {@code validity} still accepts it, or scans for the
     * nearest entity within {@code range} blocks that {@code validity} accepts.
     * <p>
     * This is the generalization of the historical {@code NearestHostileTargetSelector}: rather than a fixed
     * mod-specific validity predicate, the caller supplies its own (e.g. "alive, not a spectator, not this mob's own
     * type, not already infected", or whatever a given mod's targeting rules are).
     *
     * @param range    maximum distance in blocks to search for a target
     * @param validity predicate deciding whether a candidate entity is an acceptable target
     * @param <E>      the agent type performing the search
     */
    public static <E extends EntityLiving> Selector<E> nearestMatching(
        double range,
        Predicate<EntityLivingBase> validity
    ) {
        return (agent, blackboard) -> {
            EntityLivingBase current = blackboard.get(CommonBlackboardKeys.TARGET);

            if (current != null && validity.test(current)) {
                return current;
            }

            List<EntityLivingBase> candidates = agent.world.getEntitiesWithinAABB(
                EntityLivingBase.class,
                agent.getEntityBoundingBox().grow(range),
                candidate -> candidate != null && validity.test(candidate)
            );

            EntityLivingBase nearest = null;
            double nearestDistSq = Double.MAX_VALUE;
            for (EntityLivingBase candidate : candidates) {
                double distSq = agent.getDistanceSq(candidate);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = candidate;
                }
            }
            return nearest;
        };
    }

    /**
     * The common-case {@code visibilityPredicate}: ordinary, unobstructed line of sight via
     * {@link EntityLivingBase#canEntityBeSeen}. Mods with unusual vision rules (echolocation, heat vision through thin
     * walls, blindness effects, ...) should supply their own predicate instead.
     *
     * @param <E> the agent type performing the search
     */
    public static <E extends EntityLiving> BiPredicate<E, EntityLivingBase> lineOfSight() {
        return EntityLivingBase::canEntityBeSeen;
    }
}
