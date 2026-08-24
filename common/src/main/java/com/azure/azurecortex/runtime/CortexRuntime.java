package com.azure.azurecortex.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.action.ActionOutcome;
import com.azure.azurecortex.api.action.ActionStatus;
import com.azure.azurecortex.api.behavior.BehaviorNode;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.config.CortexConfig;
import com.azure.azurecortex.goap.PlanFailureReason;
import com.azure.azurecortex.goap.PlanFeedback;
import com.azure.azurecortex.sensing.Sensor;

/**
 * The central AI driver for a single agent.
 * <p>
 * Each tick, the runtime advances cooldowns, runs the optional {@link Sensor} (e.g. a target sensor), any registered
 * {@link PeriodicHook}s, ticks the currently active {@link Action}, and then evaluates the {@link BehaviorNode} tree to
 * potentially start a new (higher-priority) action. Non-interruptible actions block tree-driven switching (but not
 * evaluation — see below) until they complete.
 * <h3>Feedback is centralized here, not in individual actions</h3> Actions do not write {@link PlanFeedback} to the
 * blackboard themselves. Instead, {@link Action#tick} returns an {@link ActionOutcome}, and this runtime is the single
 * place that translates an {@link ActionOutcome.Blocked} or {@link ActionOutcome.Failed} outcome into
 * {@link CommonBlackboardKeys#LAST_PLAN_FEEDBACK}. This means GOAP's feedback loop is a property of the runtime
 * contract, not a convention every action has to remember to follow.
 * <h3>Periodic hooks</h3> Some agents need small, infrequent bookkeeping that isn't really an "action" at all — the
 * historical example is a xenomorph periodically re-syncing its cached hive-memory position. Rather than hard-coding
 * any particular mod's bookkeeping into the runtime, register it via {@link #addPeriodicHook}: a cooldown-gated task
 * that runs once its interval has elapsed, independent of whatever action is currently active.
 *
 * @param <E> the agent type this runtime controls
 * @param <G> the mod-defined goal-type enum used for GOAP feedback attribution
 */
@SuppressWarnings("unused")
public final class CortexRuntime<E extends Mob, G> {

    /**
     * A small, infrequent piece of bookkeeping run on a cooldown, independent of the currently active action. See
     * {@link CortexRuntime#addPeriodicHook}.
     *
     * @param <E> the agent type
     */
    @FunctionalInterface
    public interface PeriodicHook<E> {

        void run(E agent, Blackboard blackboard);
    }

    private record RegisteredHook<E>(
        String cooldownKey,
        int intervalTicks,
        PeriodicHook<E> hook
    ) {}

    private final E agent;

    private final Blackboard blackboard = new Blackboard();

    private final CooldownTracker cooldowns = new CooldownTracker();

    @Nullable
    private final Sensor<E> sensor;

    private final BehaviorNode<E, G> root;

    private final List<RegisteredHook<E>> periodicHooks = new ArrayList<>();

    private Action<E, G> currentAction;

    /**
     * Creates a new runtime for {@code agent}.
     *
     * @param agent  the agent this runtime controls
     * @param sensor the sensor responsible for refreshing target/perception state each tick, or {@code null} if this
     *               agent doesn't use one
     * @param root   the root behavior node evaluated each tick
     */
    public CortexRuntime(E agent, @Nullable Sensor<E> sensor, BehaviorNode<E, G> root) {
        this.agent = agent;
        this.sensor = sensor;
        this.root = root;
    }

    /**
     * Registers a cooldown-gated periodic hook, run once {@code intervalTicks} have elapsed since the last run (or
     * immediately on the first eligible tick). Hooks run every tick regardless of what action is currently active or
     * locked.
     *
     * @param cooldownKey   a cooldown key private to this hook — must not collide with any other cooldown key used by
     *                      this agent's actions
     * @param intervalTicks how often (in ticks) this hook should run
     * @param hook          the task to run
     * @return {@code this}, for chaining
     */
    public CortexRuntime<E, G> addPeriodicHook(String cooldownKey, int intervalTicks, PeriodicHook<E> hook) {
        periodicHooks.add(new RegisteredHook<>(cooldownKey, intervalTicks, hook));
        return this;
    }

    /**
     * Advances the runtime by one game tick.
     * <p>
     * Order of operations:
     * <ol>
     * <li>Decrement all cooldowns.</li>
     * <li>Run the sensor (if any and if the current action isn't locked) to refresh perception state.</li>
     * <li>Tick the current action, translating its {@link ActionOutcome} into blackboard feedback and, for
     * {@link ActionOutcome.Success}/{@link ActionOutcome.Failed}, stopping it.</li>
     * <li>Run any due periodic hooks.</li>
     * <li>Evaluate the behavior tree; start a new action if one outranks the current.</li>
     * </ol>
     * <h3>Interrupt categories</h3> A running action's {@link InterruptCategory} governs how resistant it is to
     * preemption (see {@link InterruptController}). Critically, even a {@link InterruptCategory#LOCKED} action does
     * <em>not</em> skip behavior-tree evaluation entirely — the tree is still consulted every tick so that an
     * {@link InterruptCategory#EMERGENCY} candidate (fire, imminent explosion, critical health, ...) can break through.
     * Only the actual <em>switch</em> is gated by {@link InterruptController#canPreempt}; evaluating the tree itself is
     * cheap, since branches that aren't selected are never ticked.
     */
    public void tick() {
        if (agent.isNoAi())
            return;
        cooldowns.tick();

        var actionIsLocked = currentAction != null
            && currentAction.interruptCategory() != InterruptCategory.NORMAL;
        if (sensor != null && !actionIsLocked)
            sensor.tick(agent, blackboard);

        if (currentAction != null) {
            var stillActive = applyOutcome(currentAction.tick(agent, blackboard, cooldowns));

            if (stillActive) {
                if (currentAction.interruptCategory() == InterruptCategory.NORMAL) {
                    // Ordinary running/blocked action: fall through to the shared tree evaluation below so the
                    // usual priority-based preemption logic applies.
                } else {
                    // Resistant (LOCKED or EMERGENCY) action: still consult the tree so a genuine emergency can
                    // break through, but nothing else is allowed to touch it.
                    var candidate = root.tick(agent, blackboard, cooldowns);
                    if (InterruptController.canPreempt(currentAction, candidate)) {
                        currentAction.stop(agent, blackboard, cooldowns, ActionStatus.INTERRUPTED);
                        currentAction = candidate.action();
                        currentAction.start(agent, blackboard, cooldowns);
                    }
                    runPeriodicHooks();
                    return;
                }
            }
            // If the action terminated (Success/Failed), applyOutcome already called stop() and cleared
            // currentAction, so we fall straight through to the shared tree evaluation below.
        }

        runPeriodicHooks();

        var result = root.tick(agent, blackboard, cooldowns);

        if (result.action() != null) {
            var shouldSwitch = currentAction == null || InterruptController.canPreempt(currentAction, result);

            if (shouldSwitch) {
                if (currentAction != null) {
                    currentAction.stop(agent, blackboard, cooldowns, ActionStatus.INTERRUPTED);
                }
                currentAction = result.action();
                currentAction.start(agent, blackboard, cooldowns);
            }
        }

        logDiagnosticsIfEnabled();
    }

    private void runPeriodicHooks() {
        for (var registered : periodicHooks) {
            if (cooldowns.ready(registered.cooldownKey())) {
                registered.hook().run(agent, blackboard);
                cooldowns.set(registered.cooldownKey(), registered.intervalTicks());
            }
        }
    }

    /**
     * Periodically logs the one-line {@code CortexDiagnostics} summary for this agent when
     * {@link CortexConfig#enableAiDiagnostics} is on. Rate-limited to once every 40 ticks per agent (rather than every
     * tick) so enabling it doesn't flood the log for a world full of agents.
     */
    private void logDiagnosticsIfEnabled() {
        if (!CortexConfig.get().enableAiDiagnostics)
            return;
        if (agent.level().getGameTime() % 40 != 0)
            return;

        CortexDiagnostics.log(agent, blackboard, currentAction);
    }

    /**
     * Translates a single {@link ActionOutcome} into blackboard feedback and, for terminal outcomes, stops
     * {@link #currentAction} and clears it.
     *
     * @param outcome the outcome {@link #currentAction}'s {@link Action#tick} just returned
     * @return {@code true} if the action is still active ({@link ActionOutcome.Running} or
     *         {@link ActionOutcome.Blocked}), {@code false} if it just terminated, in which case {@link #currentAction}
     *         is now {@code null}
     */
    private boolean applyOutcome(ActionOutcome<G> outcome) {
        if (outcome instanceof ActionOutcome.Running<G>) {
            return true;
        }

        if (outcome instanceof ActionOutcome.Blocked<G> blocked) {
            writePlanFeedback(
                blocked.reason(),
                blocked.at(),
                blocked.goalType(),
                blocked.blockingPositions()
            );
            return true;
        }

        if (outcome instanceof ActionOutcome.Success<G>) {
            currentAction.stop(agent, blackboard, cooldowns, ActionStatus.SUCCESS);
            currentAction = null;
            return false;
        }

        if (outcome instanceof ActionOutcome.Failed<G> failed) {
            writePlanFeedback(
                failed.reason(),
                failed.at(),
                failed.goalType(),
                failed.blockingPositions()
            );
            currentAction.stop(agent, blackboard, cooldowns, ActionStatus.FAILURE);
            currentAction = null;
            return false;
        }

        throw new IllegalStateException("Unknown ActionOutcome: " + outcome);
    }

    @SuppressWarnings("unchecked")
    private void writePlanFeedback(
        @Nullable PlanFailureReason reason,
        @Nullable BlockPos at,
        @Nullable G goalTypeOverride,
        List<BlockPos> blockingPositions
    ) {
        if (reason == null || reason == PlanFailureReason.NONE)
            return;

        var goalType = goalTypeOverride != null
            ? goalTypeOverride
            : (G) blackboard.get(CommonBlackboardKeys.ACTIVE_GOAL_TYPE);

        blackboard.set(
            CommonBlackboardKeys.LAST_PLAN_FEEDBACK,
            PlanFeedback.of(
                reason,
                (int) agent.level().getGameTime(),
                at != null ? at : agent.blockPosition(),
                goalType,
                blockingPositions
            )
        );
    }

    /**
     * Returns the {@link Blackboard} owned by this runtime.
     */
    public Blackboard getBlackboard() {
        return blackboard;
    }

    /**
     * Returns the {@link CooldownTracker} owned by this runtime.
     */
    public CooldownTracker getCooldowns() {
        return cooldowns;
    }

    /**
     * Returns the {@link Action} that is currently executing, or {@code null} if no action is active.
     */
    public Action<E, G> getCurrentAction() {
        return currentAction;
    }

    /**
     * Returns a {@link CortexContext} snapshot of this runtime's current state.
     */
    public CortexContext context() {
        return CortexContext.of(agent, blackboard, cooldowns);
    }
}
