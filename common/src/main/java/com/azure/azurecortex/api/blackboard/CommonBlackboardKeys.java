package com.azure.azurecortex.api.blackboard;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

import com.azure.azurecortex.goap.GoalFailureCooldowns;
import com.azure.azurecortex.goap.PlanFeedback;
import com.azure.azurecortex.goap.PlannedGoal;
import com.azure.azurecortex.goap.WorldStateSnapshot;

/**
 * Typed blackboard keys shared across every agent that uses the AzureCortex framework, independent of any particular
 * mod's entities or goal types.
 * <p>
 * Mods that add their own agent-specific state (e.g. a "current hive" reference, a role assignment, a food-ignore list)
 * should declare their own key class alongside their goal-type enum rather than editing this one — keep mod-specific
 * keys and mod-specific concepts together, and keep this class limited to concepts every consumer of the framework can
 * meaningfully use.
 * <p>
 * Several keys below store types that are generic over a mod-supplied goal-type parameter {@code G} (e.g.
 * {@link PlannedGoal}, {@link PlanFeedback}). Because {@link BlackboardKey} needs a concrete {@link Class} token, these
 * are declared with the raw type and read back with an unchecked cast at the call site — exactly like reading any other
 * generic type out of a heterogeneous container. This is safe in practice because a single agent's blackboard is only
 * ever read by code written for that agent's own goal-type enum.
 */
@SuppressWarnings("unused")
public final class CommonBlackboardKeys {

    private CommonBlackboardKeys() {}

    /** Type: {@link LivingEntity}. The agent's current combat/attention target. */
    public static final BlackboardKey<LivingEntity> TARGET = BlackboardKey.of("target", LivingEntity.class);

    /** Type: {@link LivingEntity}. The target associated with the currently committed goal, if any. */
    public static final BlackboardKey<LivingEntity> GOAL_TARGET = BlackboardKey.of("goal_target", LivingEntity.class);

    /** Type: {@link BlockPos}. Last confirmed world position where the active target was seen. */
    public static final BlackboardKey<BlockPos> LAST_SEEN_POS = BlackboardKey.of("last_seen_pos", BlockPos.class);

    /** Alias kept for legacy action references; prefer {@link #LAST_SEEN_POS} for new code. */
    public static final BlackboardKey<BlockPos> LAST_KNOWN_TARGET_POS = BlackboardKey.of(
        "last_known_target_pos",
        BlockPos.class
    );

    /**
     * Type: {@link BlockPos}. Navigation destination set by the planner for the current goal. Actions should read this
     * rather than computing their own destination from the target.
     */
    public static final BlackboardKey<BlockPos> GOAL_DESTINATION = BlackboardKey.of("goal_destination", BlockPos.class);

    /**
     * Type: {@link BlockPos}. Low-level destination consumed by generic movement actions. Set by actions that need to
     * drive the navigator directly (e.g. a retreat/hide action, a wander action).
     */
    public static final BlackboardKey<BlockPos> DESTINATION = BlackboardKey.of("destination", BlockPos.class);

    /** Type (raw): {@link PlannedGoal}. The full goal record currently committed by the planner. */
    @SuppressWarnings("rawtypes")
    public static final BlackboardKey<PlannedGoal> ACTIVE_GOAL = BlackboardKey.of("active_goal", PlannedGoal.class);

    /** Type: mod-defined goal-type enum. Convenience shorthand extracted from {@link #ACTIVE_GOAL}. */
    public static final BlackboardKey<Object> ACTIVE_GOAL_TYPE = BlackboardKey.of("active_goal_type", Object.class);

    /** Type: {@link String}. Human-readable string explaining why the planner chose the current goal. */
    public static final BlackboardKey<String> LAST_GOAL_REASON = BlackboardKey.of("last_goal_reason", String.class);

    /**
     * Type (raw): {@link PlanFeedback}. Written by the runtime when an action reports a non-{@code NONE} failure reason
     * via {@code ActionOutcome.Blocked}/{@code ActionOutcome.Failed}. The planner reads this on the next planning
     * cycle, uses it to bias goal scores, then clears it so stale feedback does not persist.
     */
    @SuppressWarnings("rawtypes")
    public static final BlackboardKey<PlanFeedback> LAST_PLAN_FEEDBACK = BlackboardKey.of(
        "last_plan_feedback",
        PlanFeedback.class
    );

    /**
     * Convenience shorthand — actions that only need to record a reason code (not a full {@link PlanFeedback}) can
     * write here. The planner wraps this into a {@link PlanFeedback} if {@link #LAST_PLAN_FEEDBACK} is not already set.
     * Type: {@code com.azure.azurecortex.goap.PlanFailureReason}.
     */
    public static final BlackboardKey<Object> LAST_FAILURE_REASON = BlackboardKey.of(
        "last_failure_reason",
        Object.class
    );

    /**
     * Running count of how many times the active goal has been abandoned with {@code FAILURE}. Reset when a new goal
     * type is committed. Used for planner fallback heuristics. Type: {@link Integer}.
     */
    public static final BlackboardKey<Integer> FAILED_GOAL_COUNT = BlackboardKey.of("failed_goal_count", Integer.class);

    /** Type (raw): {@link GoalFailureCooldowns}. Per-goal-type failure suppression state. */
    @SuppressWarnings("rawtypes")
    public static final BlackboardKey<GoalFailureCooldowns> GOAL_FAILURE_COOLDOWNS = BlackboardKey.of(
        "goal_failure_cooldowns",
        GoalFailureCooldowns.class
    );

    /**
     * Type: {@link WorldStateSnapshot}. The coarse world-state facts on record for whatever plan is currently in
     * {@link #ACTIVE_GOAL}, captured by {@code GoalExecutor#apply} and compared against live state every tick by
     * {@code PlanInvalidation} to force a replan the moment they diverge, independent of {@link #LAST_PLAN_FEEDBACK}.
     */
    public static final BlackboardKey<WorldStateSnapshot> PLAN_WORLD_STATE = BlackboardKey.of(
        "plan_world_state",
        WorldStateSnapshot.class
    );

    /** Cooldown key: time between dodge attempts. Prevents spamming a dodge action. */
    public static final String DODGE_COOLDOWN = "dodge_cooldown";

    /** Cooldown key: time between lunge/gap-closing attempts. */
    public static final String LUNGE_COOLDOWN = "lunge_cooldown";

    /**
     * Cooldown key. Prevents the passive (non-threat) branch of the behavior tree from ticking every frame. Set to a
     * high value on passive-action start; cleared to 1 on interruption.
     */
    public static final String PASSIVE_DECISION = "passive_decision";

    /**
     * Cooldown key. Rate-limits how often the GOAP planner is allowed to re-evaluate goals. Typically set to 20 ticks
     * after each replan.
     */
    public static final String GOAL_REPLAN = "goal_replan";

    /**
     * Type: {@link Float}. Persistent fire-tolerance counter for a flee-fire style action. Accumulates while fire is
     * nearby; decays while fleeing. Survives action restarts.
     */
    public static final BlackboardKey<Float> FIRE_TOLERANCE = BlackboardKey.of("fire_tolerance", Float.class);

    /**
     * Type: {@link Integer}. Game tick timestamp after which flee-fire can trigger again. Set by a flee-fire action on
     * success.
     */
    public static final BlackboardKey<Integer> FIRE_FLEE_COOLDOWN = BlackboardKey.of(
        "fire_flee_cooldown",
        Integer.class
    );

    /** Type: {@link BlockPos}. Last known position of an environmental fire source. */
    public static final BlackboardKey<BlockPos> LAST_FIRE_POS = BlackboardKey.of("last_fire_pos", BlockPos.class);

    /**
     * Type: {@link LivingEntity}. The entity that most recently caused fire damage to or near this agent (a
     * flint-and-steel user, fire-arrow shooter, lava-bucket placer detected via fire proximity).
     */
    public static final BlackboardKey<LivingEntity> LAST_FIRE_ATTACKER = BlackboardKey.of(
        "last_fire_attacker",
        LivingEntity.class
    );

    /**
     * Type: {@link Boolean}. {@code true} when the current {@link #TARGET} is the same entity recorded in
     * {@link #LAST_FIRE_ATTACKER}. Cached here so the behavior tree can gate reactions without re-querying the attacker
     * every tick.
     */
    public static final BlackboardKey<Boolean> TARGET_IS_FIRE_USER = BlackboardKey.of(
        "target_is_fire_user",
        Boolean.class
    );

    /**
     * Type: {@link Integer}. Game tick timestamp until which fire is considered a serious danger from a specific
     * attacker. The planner degrades fire-user penalties once this expires.
     */
    public static final BlackboardKey<Integer> FIRE_DANGER_UNTIL_TICK = BlackboardKey.of(
        "fire_danger_until_tick",
        Integer.class
    );

    /** Type: {@link Boolean}. {@code true} when target is holding a ranged weapon or has fired recently. */
    public static final BlackboardKey<Boolean> TARGET_IS_RANGED = BlackboardKey.of("target_is_ranged", Boolean.class);

    /** Type: {@link Boolean}. {@code true} when no other hostile entity is near the target. */
    public static final BlackboardKey<Boolean> TARGET_IS_ISOLATED = BlackboardKey.of(
        "target_is_isolated",
        Boolean.class
    );

    /** Type: {@link Boolean}. {@code true} when target has significant armor equipped. */
    public static final BlackboardKey<Boolean> TARGET_IS_ARMORED = BlackboardKey.of("target_is_armored", Boolean.class);

    /**
     * Type: {@link Integer}. Game tick at which an agent was first noticed swimming with no live target and no
     * blackboard destination. A swim action can use this to give the agent a brief grace period before committing to
     * beelining for the nearest shore.
     */
    public static final BlackboardKey<Integer> SWIM_STRANDED_SINCE_TICK = BlackboardKey.of(
        "swim_stranded_since_tick",
        Integer.class
    );
}
