package com.azure.azurecortex.goap;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;
import java.util.Optional;

import com.azure.azurecortex.api.goal.Goal;
import com.azure.azurecortex.api.goal.GoalUrgency;

/**
 * A single committed GOAP goal, produced by a {@link GoalPlanner} and applied to the blackboard by
 * {@link GoalExecutor#apply}.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
@SuppressWarnings("unused")
public final class PlannedGoal<E extends EntityLiving, G> {

    private final G type;

    private final float score;

    private final int startedAtTick;

    private final int minCommitTicks;

    private final int maxCommitTicks;

    private final Optional<EntityLivingBase> target;

    private final Optional<BlockPos> destination;

    private final GoalUrgency urgency;

    private final boolean interruptible;

    private final String reason;

    /**
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
    public PlannedGoal(
        G type,
        float score,
        int startedAtTick,
        int minCommitTicks,
        int maxCommitTicks,
        Optional<EntityLivingBase> target,
        Optional<BlockPos> destination,
        GoalUrgency urgency,
        boolean interruptible,
        String reason
    ) {
        this.type = type;
        this.score = score;
        this.startedAtTick = startedAtTick;
        this.minCommitTicks = minCommitTicks;
        this.maxCommitTicks = maxCommitTicks;
        this.target = target;
        this.destination = destination;
        this.urgency = urgency;
        this.interruptible = interruptible;
        this.reason = reason;
    }

    public G type() {
        return type;
    }

    public float score() {
        return score;
    }

    public int startedAtTick() {
        return startedAtTick;
    }

    public int minCommitTicks() {
        return minCommitTicks;
    }

    public int maxCommitTicks() {
        return maxCommitTicks;
    }

    public Optional<EntityLivingBase> target() {
        return target;
    }

    public Optional<BlockPos> destination() {
        return destination;
    }

    public GoalUrgency urgency() {
        return urgency;
    }

    public boolean interruptible() {
        return interruptible;
    }

    public String reason() {
        return reason;
    }

    public boolean canReplan(int currentTick) {
        return currentTick - startedAtTick >= minCommitTicks;
    }

    public boolean isExpired(int currentTick) {
        return currentTick - startedAtTick >= maxCommitTicks;
    }

    public boolean hasValidTarget() {
        return target.isPresent() && target.get().isEntityAlive();
    }

    public boolean hasDestination() {
        return destination.isPresent();
    }

    /**
     * Returns {@code true} if {@link #type} implements {@link Goal} and reports itself as the "no goal" sentinel via
     * {@link Goal#isNone()}. Goal-type enums that don't implement {@link Goal} always report {@code false} here.
     */
    public boolean isNone() {
        return type instanceof Goal && ((Goal) type).isNone();
    }

    public static <E extends EntityLiving, G> PlannedGoal<E, G> of(
        G type,
        float score,
        int currentTick,
        int minCommitTicks,
        int maxCommitTicks,
        EntityLivingBase target,
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
    public static <E extends EntityLiving, G> PlannedGoal<E, G> none(G type, int currentTick) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof PlannedGoal))
            return false;
        PlannedGoal<?, ?> other = (PlannedGoal<?, ?>) o;
        return Float.compare(score, other.score) == 0 && startedAtTick == other.startedAtTick
            && minCommitTicks == other.minCommitTicks && maxCommitTicks == other.maxCommitTicks
            && interruptible == other.interruptible && Objects.equals(type, other.type)
            && Objects.equals(target, other.target) && Objects.equals(destination, other.destination)
            && urgency == other.urgency && Objects.equals(reason, other.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            type,
            score,
            startedAtTick,
            minCommitTicks,
            maxCommitTicks,
            target,
            destination,
            urgency,
            interruptible,
            reason
        );
    }

    @Override
    public String toString() {
        return "PlannedGoal[type=" + type + ", score=" + score + ", startedAtTick=" + startedAtTick + ", urgency="
            + urgency + ", reason=" + reason + "]";
    }
}
