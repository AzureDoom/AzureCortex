package com.azure.azurecortex.goap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

import com.azure.azurecortex.api.goal.GoalUrgency;

/**
 * A single committed GOAP goal, produced by a {@link GoalPlanner} and applied to the blackboard by
 * {@link GoalExecutor#apply}.
 *
 * @param <E>            the agent type
 * @param <G>            the mod-defined goal-type enum
 * @param type           which goal this is
 * @param score          the score the planner assigned this goal when it was chosen
 * @param startedAtTick  the game tick this goal was committed
 * @param minCommitTicks minimum ticks before the planner is allowed to reconsider ({@link #canReplan})
 * @param maxCommitTicks maximum ticks before the planner is forced to reconsider regardless of anything else
 *                       ({@link #isExpired})
 * @param target         the entity this goal is about, if any
 * @param destination    the world position this goal is about, if any
 * @param urgency        how urgently this goal wants to preempt the agent's current commitment
 * @param interruptible  whether the resulting action should be treated as ordinarily interruptible
 * @param reason         a human-readable explanation of why the planner chose this goal, for diagnostics
 */
@SuppressWarnings("unused")
public record PlannedGoal<E extends Mob, G>(
    G type,
    float score,

    int startedAtTick,
    int minCommitTicks,
    int maxCommitTicks,

    Optional<LivingEntity> target,
    Optional<BlockPos> destination,

    GoalUrgency urgency,
    boolean interruptible,

    String reason
) {

    public boolean canReplan(int currentTick) {
        return currentTick - startedAtTick >= minCommitTicks;
    }

    public boolean isExpired(int currentTick) {
        return currentTick - startedAtTick >= maxCommitTicks;
    }

    public boolean hasValidTarget() {
        return target.isPresent() && target.get().isAlive();
    }

    public boolean hasDestination() {
        return destination.isPresent();
    }

    /**
     * Returns {@code true} if {@link #type} implements {@link com.azure.azurecortex.api.goal.Goal} and reports itself
     * as the "no goal" sentinel via {@link com.azure.azurecortex.api.goal.Goal#isNone()}. Goal-type enums that don't
     * implement {@link com.azure.azurecortex.api.goal.Goal} always report {@code false} here.
     */
    public boolean isNone() {
        return type instanceof com.azure.azurecortex.api.goal.Goal goal && goal.isNone();
    }

    public static <E extends Mob, G> PlannedGoal<E, G> of(
        G type,
        float score,
        int currentTick,
        int minCommitTicks,
        int maxCommitTicks,
        LivingEntity target,
        BlockPos destination,
        GoalUrgency urgency,
        boolean interruptible,
        String reason
    ) {
        return new PlannedGoal<>(
            type,
            score,
            currentTick,
            minCommitTicks,
            maxCommitTicks,
            Optional.ofNullable(target),
            Optional.ofNullable(destination),
            urgency,
            interruptible,
            reason
        );
    }

    /**
     * Builds the "no goal selected" sentinel for {@code type} (typically a mod's {@code NONE} constant).
     *
     * @param type        the sentinel goal-type value representing "nothing chosen"
     * @param currentTick the current game tick
     */
    public static <E extends Mob, G> PlannedGoal<E, G> none(G type, int currentTick) {
        return new PlannedGoal<>(
            type,
            0.0F,
            currentTick,
            20,
            40,
            Optional.empty(),
            Optional.empty(),
            GoalUrgency.LOW,
            true,
            "No goal selected"
        );
    }
}
