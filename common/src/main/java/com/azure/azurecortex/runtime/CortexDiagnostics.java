package com.azure.azurecortex.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import com.azure.azurecortex.AzureCortex;
import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.goap.PlanFeedback;

/**
 * Builds (and, when enabled, logs) a single-line, human-scannable diagnostic string summarizing what an agent's brain
 * is currently doing, in the form:
 *
 * <pre>
 * TARGET=Villager, PLAN=CAPTURE_HOST, ACTION=CARRY_TO_WEB, PATH=BLOCKED(GLASS@120,64,-30)
 * </pre>
 *
 * <h3>Why this exists</h3> Once a plan/blackboard/pathing chain has several layers (GOAP planner → behavior tree →
 * action → pathfinder), a misbehaving agent can be failing at any one of them, and the failure often only shows up two
 * or three layers away from its actual cause. This puts all four pieces of state on one line so that chain can be read
 * at a glance instead of reconstructed from separate logs.
 * <p>
 * This is a read-only formatter — it does not gate or influence any AI decision, only describes it.
 */
public final class CortexDiagnostics {

    private CortexDiagnostics() {}

    /**
     * Builds and logs the one-line diagnostic for {@code agent} at {@code INFO} level.
     *
     * @param agent         the agent to describe
     * @param blackboard    the agent's blackboard
     * @param currentAction the action currently running on {@code agent}'s runtime, or {@code null} if none
     */
    public static void log(Mob agent, Blackboard blackboard, @Nullable Action<?, ?> currentAction) {
        AzureCortex.LOGGER.info("[{}] {}", agent.getStringUUID(), describe(agent, blackboard, currentAction));
    }

    /**
     * Builds the one-line diagnostic for {@code agent}.
     *
     * @param agent         the agent to describe
     * @param blackboard    the agent's blackboard
     * @param currentAction the action currently running on {@code agent}'s runtime, or {@code null} if none
     * @return the formatted diagnostic line
     */
    public static String describe(Mob agent, Blackboard blackboard, @Nullable Action<?, ?> currentAction) {
        return "TARGET=" + describeTarget(blackboard)
            + ", PLAN=" + describePlan(blackboard)
            + ", ACTION=" + describeAction(currentAction)
            + ", PATH=" + describePath(agent, blackboard);
    }

    private static String describeTarget(Blackboard blackboard) {
        var target = blackboard.get(CommonBlackboardKeys.TARGET);
        if (target == null || !target.isAlive())
            return "NONE";
        return target.getType().getDescription().getString();
    }

    private static String describePlan(Blackboard blackboard) {
        var goalType = blackboard.get(CommonBlackboardKeys.ACTIVE_GOAL_TYPE);
        return goalType != null ? goalType.toString() : "NONE";
    }

    private static String describeAction(@Nullable Action<?, ?> currentAction) {
        return currentAction != null ? currentAction.debugName() : "NONE";
    }

    private static String describePath(Mob agent, Blackboard blackboard) {
        PlanFeedback<?> feedback = blackboard.get(CommonBlackboardKeys.LAST_PLAN_FEEDBACK);
        if (feedback == null || feedback.isNone())
            return "OK";

        var blockingPositions = feedback.blockingPositions();
        if (blockingPositions.isEmpty())
            return feedback.reason().name();

        var pos = blockingPositions.getFirst();
        return feedback.reason().name() + "(" + describeBlock(agent, pos) + "@" + pos.getX() + "," + pos.getY() + ","
            + pos.getZ() + ")";
    }

    private static String describeBlock(Mob agent, BlockPos pos) {
        var block = agent.level().getBlockState(pos).getBlock();
        var key = BuiltInRegistries.BLOCK.getKey(block);
        return key.getPath().toUpperCase(Locale.ROOT);
    }
}
