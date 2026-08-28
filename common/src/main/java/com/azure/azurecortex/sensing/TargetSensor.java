package com.azure.azurecortex.sensing;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

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
 * {@code Mob#hasLineOfSight} check, since not every mod's vision rules are ordinary line-of-sight — see
 * {@link #lineOfSight()} for the common case. Agents constructed without a predicate never populate these three keys.
 *
 * @param <E> the agent type this sensor serves
 */
@SuppressWarnings("unused")
public final class TargetSensor<E> implements Sensor<E> {

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
        LivingEntity findTarget(E agent, Blackboard blackboard);
    }

    private final Selector<E> selector;

    private final int retargetInterval;

    @Nullable
    private final BiPredicate<E, LivingEntity> visibilityPredicate;

    private int age;

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
        @Nullable BiPredicate<E, LivingEntity> visibilityPredicate
    ) {
        this.selector = selector;
        this.retargetInterval = retargetInterval;
        this.visibilityPredicate = visibilityPredicate;
    }

    @Override
    public void tick(E agent, Blackboard blackboard) {
        age++;

        var current = blackboard.get(CommonBlackboardKeys.TARGET);

        if (current != null && current.isAlive()) {
            blackboard.set(CommonBlackboardKeys.LAST_KNOWN_TARGET_POS, current.blockPosition());

            if (visibilityPredicate != null && visibilityPredicate.test(agent, current)) {
                blackboard.set(CommonBlackboardKeys.LAST_SEEN_POS, current.blockPosition());
                blackboard.set(CommonBlackboardKeys.LAST_SEEN_VELOCITY, current.getDeltaMovement());
                blackboard.set(CommonBlackboardKeys.LAST_SEEN_TICK, (int) current.level().getGameTime());
            }
        }

        if (age % retargetInterval != 0 && current != null && current.isAlive()) {
            return;
        }

        var target = selector.findTarget(agent, blackboard);

        if (target != null) {
            blackboard.set(CommonBlackboardKeys.TARGET, target);
        } else {
            blackboard.remove(CommonBlackboardKeys.TARGET);
        }
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
    public static <E extends Mob> Selector<E> nearestMatching(double range, Predicate<LivingEntity> validity) {
        return (agent, blackboard) -> {
            var current = blackboard.get(CommonBlackboardKeys.TARGET);

            if (current != null && validity.test(current)) {
                return current;
            }

            return agent.level()
                .getEntitiesOfClass(LivingEntity.class, agent.getBoundingBox().inflate(range), validity)
                .stream()
                .min(Comparator.comparingDouble(agent::distanceToSqr))
                .orElse(null);
        };
    }

    /**
     * The common-case {@code visibilityPredicate}: ordinary, unobstructed line of sight via {@link Mob#hasLineOfSight}.
     * Mods with unusual vision rules (echolocation, heat vision through thin walls, blindness effects, ...) should
     * supply their own predicate instead.
     *
     * @param <E> the agent type performing the search
     */
    public static <E extends Mob> BiPredicate<E, LivingEntity> lineOfSight() {
        return Mob::hasLineOfSight;
    }
}
