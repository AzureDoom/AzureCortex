package com.azure.azurecortex.action.combat;

import net.minecraft.entity.EntityLiving;
import net.minecraft.util.EnumHand;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.action.ActionOutcome;
import com.azure.azurecortex.api.action.ActionStatus;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.goap.PlanFailureReason;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * A generic reference action that drives the vanilla "hold an item, then release it" lifecycle
 * ({@link EntityLiving#setActiveHand}/{@link EntityLiving#resetActiveHand}) — the same mechanism vanilla's own
 * ranged-attack and consumable AI goals use for bows, crossbows, tridents, potions, and food.
 * <h3>Two release modes</h3>
 * <ul>
 * <li><b>Charge-and-release</b> (bows, crossbows, tridents): construct with {@code minChargeTicks > 0}. The action
 * starts using the item, waits at least {@code minChargeTicks}, then calls {@link EntityLiving#resetActiveHand()}
 * followed by {@link #onRelease} (if supplied) once {@code readyToRelease} agrees — or once {@code maxChargeTicks} is
 * hit regardless.</li>
 * <li><b>Auto-completing</b> (eating, drinking): construct via {@link #autoComplete}. The action starts using the item
 * and simply waits for vanilla's own {@link EntityLiving#isHandActive()} to go false on its own once the item's use
 * duration elapses — this mode never calls {@code resetActiveHand()}, {@link #onChargeTick}, or {@link #onRelease}
 * itself.</li>
 * </ul>
 * <h3>Priority is per instance, not per class</h3> Unlike most actions in this package, {@link #priority()} is a
 * constructor parameter rather than a fixed literal. A charge-and-release bow attack and an auto-completing emergency
 * heal-item both reasonably use this same action class, but want very different resistance to preemption once running —
 * a fixed class-level priority couldn't serve both.
 * <h3>Start/stop lifecycle hooks</h3> {@link #onStart} and {@link #onStop} exist for state that should be true for the
 * whole duration of the action and nowhere else — the canonical example is
 * {@code AbstractSkeleton#setSwingingArms(true)} for a bow, mirroring what vanilla's own
 * {@code EntityAIAttackRangedBow} does in its {@code startExecuting()}/ {@code resetTask()}. {@link #onStop} is
 * guaranteed to run exactly once for every {@link #onStart} — {@link #stop} is called unconditionally whenever the
 * action ends, whether that's success (release completed), failure (the {@link ItemCheck} or {@code isHandActive} check
 * failed), or external interruption (behavior tree preemption) — so state set in {@link #onStart} can never get stuck
 * on. Both hooks apply to auto-completing mode too, since "holding the item" is still well-defined there even without a
 * charge/release cycle.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public class UseItemAction<E extends EntityLiving, G> implements Action<E, G> {

    /** Tests whether {@code agent} is still holding an acceptable item to use, checked on every tick. */
    @FunctionalInterface
    public interface ItemCheck<E> {

        boolean test(E agent);
    }

    /**
     * Tests whether the charge-and-release mode should release now, checked every tick once {@code minChargeTicks} has
     * elapsed. Typically, checks that the intended target is still alive and in range/line of sight.
     */
    @FunctionalInterface
    public interface ReleaseCondition<E> {

        boolean test(E agent, Blackboard blackboard, int ticksCharged);
    }

    /**
     * Runs every tick while charge-and-release mode is actively holding the item (both before and after
     * {@code minChargeTicks} has elapsed) — the place for continuous per-tick effects like aiming, e.g.
     * {@code (agent, bb, t) -> agent.getLookHelper().setLookPositionWithEntity(target, 30F, 30F)}.
     */
    @FunctionalInterface
    public interface ChargeTick<E> {

        void tick(E agent, Blackboard blackboard, int ticksCharged);
    }

    /**
     * Runs once, immediately after charge-and-release mode calls {@link EntityLiving#resetActiveHand()} — the place for
     * whatever actually constitutes "the attack" for items whose vanilla release logic doesn't fire for non-player
     * entities, e.g. a bow calling {@code IRangedAttackMob#attackEntityWithRangedAttack}. See the class docs'
     * explanation of why this is necessary for bows specifically.
     */
    @FunctionalInterface
    public interface ReleaseCallback<E> {

        void onRelease(E agent, Blackboard blackboard, int ticksCharged);
    }

    /**
     * Runs once, in {@link #start}, immediately after {@link EntityLiving#setActiveHand} — the place for state that
     * should hold for the entire duration of the use, e.g. {@code skeleton.setSwingingArms(true)}. Always paired with
     * exactly one {@link StopCallback} invocation — see class docs.
     */
    @FunctionalInterface
    public interface StartCallback<E> {

        void onStart(E agent, Blackboard blackboard);
    }

    /**
     * Runs once, in {@link #stop}, regardless of whether the action ended via success, failure, or external
     * interruption — the place to undo whatever {@link StartCallback} set, so it can never get stuck on. {@code reason}
     * is the same {@link ActionStatus} the framework passed to {@link #stop}, in case the undo logic needs to
     * distinguish e.g. a clean release from a preempted one.
     */
    @FunctionalInterface
    public interface StopCallback<E> {

        void onStop(E agent, Blackboard blackboard, ActionStatus reason);
    }

    private final EnumHand hand;

    private final ItemCheck<E> itemCheck;

    private final int minChargeTicks;

    private final int maxChargeTicks;

    private final ReleaseCondition<E> readyToRelease;

    private final ChargeTick<E> onChargeTick;

    private final ReleaseCallback<E> onRelease;

    private final String cooldownKey;

    private final int cooldownTicks;

    private final int priority;

    private final StartCallback<E> onStart;

    private final StopCallback<E> onStop;

    private int ticksElapsed;

    /**
     * @param hand           which hand's item to use
     * @param itemCheck      checked every tick — the action fails with {@link PlanFailureReason#FAILED_PRECONDITION}
     *                       the moment this stops holding
     * @param minChargeTicks minimum ticks to hold the item before releasing; {@code 0} selects auto-completing mode
     *                       (eating/drinking) and disables every charge-and-release-only parameter — prefer
     *                       {@link #autoComplete} for that case instead of passing {@code 0} directly
     * @param maxChargeTicks hard cap on ticks to hold before releasing regardless of {@code readyToRelease}; ignored in
     *                       auto-completing mode
     * @param readyToRelease extra gate checked once {@code minChargeTicks} has elapsed, e.g. "target still in range";
     *                       ignored in auto-completing mode; pass {@code (agent, bb, t) -> true} to release as soon as
     *                       {@code minChargeTicks} is reached
     * @param onChargeTick   optional (nullable) per-tick effect while charging, e.g. aiming — see class docs; ignored
     *                       in auto-completing mode
     * @param onRelease      optional (nullable) callback run immediately after {@link EntityLiving#resetActiveHand()} —
     *                       see class docs for why bows need this; ignored in auto-completing mode
     * @param cooldownKey    cooldown set on release/completion
     * @param cooldownTicks  how long that cooldown lasts
     * @param priority       this instance's priority — see class docs for why this is per-instance rather than fixed
     * @param onStart        optional (nullable) callback run once in {@link #start} — see class docs
     * @param onStop         optional (nullable) callback run once in {@link #stop}, paired 1:1 with {@code onStart} —
     *                       see class docs
     */
    public UseItemAction(
        EnumHand hand,
        ItemCheck<E> itemCheck,
        int minChargeTicks,
        int maxChargeTicks,
        ReleaseCondition<E> readyToRelease,
        ChargeTick<E> onChargeTick,
        ReleaseCallback<E> onRelease,
        String cooldownKey,
        int cooldownTicks,
        int priority,
        StartCallback<E> onStart,
        StopCallback<E> onStop
    ) {
        this.hand = hand;
        this.itemCheck = itemCheck;
        this.minChargeTicks = minChargeTicks;
        this.maxChargeTicks = maxChargeTicks;
        this.readyToRelease = readyToRelease;
        this.onChargeTick = onChargeTick;
        this.onRelease = onRelease;
        this.cooldownKey = cooldownKey;
        this.cooldownTicks = cooldownTicks;
        this.priority = priority;
        this.onStart = onStart;
        this.onStop = onStop;
    }

    /**
     * Convenience factory for auto-completing items (eating, drinking) — see class docs. There is no charge window and
     * nothing to release; the action simply waits for vanilla to finish using the item on its own.
     *
     * @param hand          which hand's item to use
     * @param itemCheck     checked every tick — the action fails with {@link PlanFailureReason#FAILED_PRECONDITION} the
     *                      moment this stops holding
     * @param cooldownKey   cooldown set on completion
     * @param cooldownTicks how long that cooldown lasts
     * @param priority      this instance's priority
     */
    public static <E extends EntityLiving, G> UseItemAction<E, G> autoComplete(
        EnumHand hand,
        ItemCheck<E> itemCheck,
        String cooldownKey,
        int cooldownTicks,
        int priority
    ) {
        return new UseItemAction<>(
            hand,
            itemCheck,
            0,
            0,
            null,
            null,
            null,
            cooldownKey,
            cooldownTicks,
            priority,
            null,
            null
        );
    }

    @Override
    public void start(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        ticksElapsed = 0;
        agent.setActiveHand(hand);
        if (onStart != null) {
            onStart.onStart(agent, blackboard);
        }
    }

    @Override
    public ActionOutcome<G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        if (!itemCheck.test(agent)) {
            if (agent.isHandActive())
                agent.resetActiveHand();
            return ActionOutcome.failed(PlanFailureReason.FAILED_PRECONDITION);
        }

        ticksElapsed++;

        if (minChargeTicks <= 0) {
            return agent.isHandActive() ? ActionOutcome.running() : finish(cooldowns);
        }

        if (!agent.isHandActive()) {
            return ActionOutcome.failed(PlanFailureReason.FAILED_PRECONDITION);
        }

        if (onChargeTick != null) {
            onChargeTick.tick(agent, blackboard, ticksElapsed);
        }

        if (ticksElapsed >= maxChargeTicks) {
            return release(agent, blackboard, cooldowns);
        }

        if (ticksElapsed >= minChargeTicks && readyToRelease.test(agent, blackboard, ticksElapsed)) {
            return release(agent, blackboard, cooldowns);
        }

        return ActionOutcome.running();
    }

    private ActionOutcome<G> release(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        int ticksCharged = ticksElapsed;
        agent.resetActiveHand();
        if (onRelease != null) {
            onRelease.onRelease(agent, blackboard, ticksCharged);
        }
        return finish(cooldowns);
    }

    private ActionOutcome<G> finish(CooldownTracker cooldowns) {
        cooldowns.set(cooldownKey, cooldownTicks);
        return ActionOutcome.success();
    }

    @Override
    public void stop(E agent, Blackboard blackboard, CooldownTracker cooldowns, ActionStatus reason) {
        if (agent.isHandActive()) {
            agent.resetActiveHand();
        }
        if (onStop != null) {
            onStop.onStop(agent, blackboard, reason);
        }
    }

    @Override
    public boolean isInterruptible() {
        return true;
    }

    @Override
    public int priority() {
        return priority;
    }
}
