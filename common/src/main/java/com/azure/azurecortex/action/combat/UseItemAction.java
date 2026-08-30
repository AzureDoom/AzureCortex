package com.azure.azurecortex.action.combat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.action.ActionOutcome;
import com.azure.azurecortex.api.action.ActionStatus;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.goap.PlanFailureReason;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * A generic reference action that drives the vanilla "hold an item, then release it" lifecycle
 * ({@link Mob#startUsingItem}/{@link Mob#stopUsingItem}) — the same mechanism vanilla's own ranged-attack and
 * consumable AI goals use for bows, crossbows, tridents, potions, and food.
 * <h3>What release actually does — and doesn't do — on its own</h3> For auto-completing items (food, potions),
 * vanilla's own {@code Item#finishUsingItem} runs generically on any {@link net.minecraft.world.entity.LivingEntity}
 * and needs nothing further from this action. Bows are different: {@code BowItem#releaseUsing} only fires an arrow
 * {@code if (entity instanceof Player)} — for a {@link Mob}, calling {@link Mob#stopUsingItem()} alone ends the drawing
 * animation and fires nothing. Vanilla's own {@code RangedBowAttackGoal} accounts for this by calling
 * {@code stopUsingItem()} and then separately, explicitly, calling
 * {@code RangedAttackMob#performRangedAttack(target, power)} — the actual arrow only exists because of that second
 * call. This action does not special-case bows itself (that would defeat the point of being generic); instead,
 * {@link #onRelease} is the hook a bow (or crossbow, or trident) wires up to actually perform its attack the same way
 * vanilla does. Similarly, vanilla's ranged goals call {@code getLookControl().setLookAt(target, ...)} every tick while
 * charging so the mob visibly aims; this action doesn't do that on its own either, so {@link #onChargeTick} is where
 * that (or any other continuous per-tick effect) belongs.
 * <h3>Two release modes</h3>
 * <ul>
 * <li><b>Charge-and-release</b> (bows, crossbows, tridents): construct with {@code minChargeTicks > 0}. The action
 * starts using the item, waits at least {@code minChargeTicks}, then calls {@link Mob#stopUsingItem()} followed by
 * {@link #onRelease} (if supplied) once {@code readyToRelease} agrees — or once {@code maxChargeTicks} is hit
 * regardless.</li>
 * <li><b>Auto-completing</b> (eating, drinking): construct via {@link #autoComplete}. The action starts using the item
 * and simply waits for vanilla's own {@link Mob#isUsingItem()} to go false on its own once the item's use duration
 * elapses — this mode never calls {@code stopUsingItem()}, {@link #onChargeTick}, or {@link #onRelease} itself.</li>
 * </ul>
 * <h3>Priority is per instance, not per class</h3> Unlike most actions in this package, {@link #priority()} is a
 * constructor parameter rather than a fixed literal. A charge-and-release bow attack and an auto-completing emergency
 * heal-item both reasonably use this same action class, but want very different resistance to preemption once running —
 * a fixed class-level priority couldn't serve both.
 * <h3>Start/stop lifecycle hooks</h3> {@link #onStart} and {@link #onStop} exist for state that should be true for the
 * whole duration of the action and nowhere else — the canonical example is {@code Mob#setAggressive(true)} for a bow,
 * mirroring what vanilla's own {@code RangedBowAttackGoal} does in its {@code start()}/{@code stop()}. {@link #onStop}
 * is guaranteed to run exactly once for every {@link #onStart} — {@link #stop} is called unconditionally whenever the
 * action ends, whether that's success (release completed), failure (the {@link ItemCheck} or {@code isUsingItem} check
 * failed), or external interruption (behavior tree preemption) — so state set in {@link #onStart} can never get stuck
 * on. Both hooks apply to auto-completing mode too, since "holding the item" is still well-defined there even without a
 * charge/release cycle.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public class UseItemAction<E extends Mob, G> implements Action<E, G> {

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
     * {@code (agent, bb, t) -> agent.getLookControl().setLookAt(target, 30F, 30F)}.
     */
    @FunctionalInterface
    public interface ChargeTick<E> {

        void tick(E agent, Blackboard blackboard, int ticksCharged);
    }

    /**
     * Runs once, immediately after charge-and-release mode calls {@link Mob#stopUsingItem()} — the place for whatever
     * actually constitutes "the attack" for items whose vanilla release logic doesn't fire for non-{@code Player}
     * entities, e.g. a bow calling {@code RangedAttackMob#performRangedAttack}. See the class docs' explanation of why
     * this is necessary for bows specifically.
     */
    @FunctionalInterface
    public interface ReleaseCallback<E> {

        void onRelease(E agent, Blackboard blackboard, int ticksCharged);
    }

    /**
     * Runs once, in {@link #start}, immediately after {@link Mob#startUsingItem} — the place for state that should hold
     * for the entire duration of the use, e.g. {@code agent.setAggressive(true)}. Always paired with exactly one
     * {@link StopCallback} invocation — see class docs.
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

    private final InteractionHand hand;

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
     * @param onRelease      optional (nullable) callback run immediately after {@link Mob#stopUsingItem()} — see class
     *                       docs for why bows need this; ignored in auto-completing mode
     * @param cooldownKey    cooldown set on release/completion
     * @param cooldownTicks  how long that cooldown lasts
     * @param priority       this instance's priority — see class docs for why this is per-instance rather than fixed
     * @param onStart        optional (nullable) callback run once in {@link #start} — see class docs
     * @param onStop         optional (nullable) callback run once in {@link #stop}, paired 1:1 with {@code onStart} —
     *                       see class docs
     */
    public UseItemAction(
        InteractionHand hand,
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
    public static <E extends Mob, G> UseItemAction<E, G> autoComplete(
        InteractionHand hand,
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
        agent.startUsingItem(hand);
        if (onStart != null) {
            onStart.onStart(agent, blackboard);
        }
    }

    @Override
    public ActionOutcome<G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        if (!itemCheck.test(agent)) {
            if (agent.isUsingItem())
                agent.stopUsingItem();
            return ActionOutcome.failed(PlanFailureReason.FAILED_PRECONDITION);
        }

        ticksElapsed++;

        if (minChargeTicks <= 0) {
            return agent.isUsingItem() ? ActionOutcome.running() : finish(cooldowns);
        }

        if (!agent.isUsingItem()) {
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
        var ticksCharged = ticksElapsed;
        agent.stopUsingItem();
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
        if (agent.isUsingItem()) {
            agent.stopUsingItem();
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
