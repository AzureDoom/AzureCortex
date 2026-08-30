package com.azure.azurecortex.example.skeleton;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import com.azure.azurecortex.action.combat.AttackProfile;
import com.azure.azurecortex.action.combat.TimedAttackAction;
import com.azure.azurecortex.action.combat.UseItemAction;
import com.azure.azurecortex.action.movement.IdleAction;
import com.azure.azurecortex.action.movement.MoveToDestinationAction;
import com.azure.azurecortex.action.movement.WanderAction;
import com.azure.azurecortex.action.utility.InvestigateLastSeenTargetAction;
import com.azure.azurecortex.api.behavior.BehaviorNode;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.behavior.composite.PrioritySelector;
import com.azure.azurecortex.behavior.decorator.Condition;
import com.azure.azurecortex.behavior.leaf.ActionNode;
import com.azure.azurecortex.example.HuntTargetNode;
import com.azure.azurecortex.navigation.astar.AStarPathfinder;

/**
 * Builds the behavior tree for {@link CortexSkeletonEntity}: one branch per {@link CortexSkeletonGoal}, arbitrated by a
 * {@link PrioritySelector}.
 * <p>
 * {@code HUNT_TARGET} is the interesting branch — it uses {@link HuntTargetNode} with two {@link AttackProfile}s: a bow
 * (mid-to-long range, built on {@link UseItemAction}'s charge-and-release mode) and a melee fallback for when the
 * target closes to point-blank range. {@link HuntTargetNode} re-evaluates which of the two is legal every tick via
 * {@code AttackSelector}, so a skeleton smoothly switches from shooting to swinging without any explicit state machine
 * for it.
 * <p>
 * The bow specifically supplies {@link UseItemAction}'s {@code onChargeTick} (to aim at the target while drawing) and
 * {@code onRelease} (to actually fire, via {@code RangedAttackMob#performRangedAttack}) — see that class's docs for why
 * a bow needs both of those and can't rely on {@code stopUsingItem()} alone: {@code BowItem#releaseUsing} only fires an
 * arrow {@code if (entity instanceof Player)}, so for a mob it silently does nothing, exactly as vanilla's own
 * {@code RangedBowAttackGoal} anticipates by calling {@code performRangedAttack} itself.
 * <p>
 * It also supplies {@code onStart}/{@code onStop} to toggle {@code setAggressive}, mirroring what vanilla's own
 * {@code RangedBowAttackGoal} does in its own {@code start()}/{@code stop()} — this keeps the aggressive-pose state
 * scoped exactly to "actively drawing/holding the bow" without {@link CortexSkeletonEntity} needing to poll for it
 * every tick.
 */
public final class CortexSkeletonTree {

    private CortexSkeletonTree() {}

    public static BehaviorNode<CortexSkeletonEntity, CortexSkeletonGoal> create() {
        var idle = new IdleAction<CortexSkeletonEntity, CortexSkeletonGoal>();

        var wander = new WanderAction<CortexSkeletonEntity, CortexSkeletonGoal>(1.0D, 10.0D, 100);

        var investigate = new InvestigateLastSeenTargetAction<CortexSkeletonEntity, CortexSkeletonGoal>(
            AStarPathfinder.INSTANCE,
            1.0D,
            2,
            60,
            100,
            60,
            0.02D,
            2.0D,
            8.0D
        );

        var hunt = getHunt();

        return PrioritySelector.of(
            new ActionNode<>(idle, 0),
            new Condition<>(
                (agent, blackboard, cooldowns) -> blackboard.get(
                    CommonBlackboardKeys.ACTIVE_GOAL_TYPE
                ) == CortexSkeletonGoal.WANDER,
                new ActionNode<>(wander, 5)
            ),
            new Condition<>(
                (agent, blackboard, cooldowns) -> blackboard.get(
                    CommonBlackboardKeys.ACTIVE_GOAL_TYPE
                ) == CortexSkeletonGoal.INVESTIGATE,
                new ActionNode<>(investigate, 8)
            ),
            hunt
        );
    }

    private static @NotNull HuntTargetNode<CortexSkeletonEntity, CortexSkeletonGoal> getHunt() {
        var bowAttack = new UseItemAction<CortexSkeletonEntity, CortexSkeletonGoal>(
            InteractionHand.MAIN_HAND,
            agent -> agent.getMainHandItem().is(Items.BOW),
            20,
            40,
            (agent, blackboard, ticksCharged) -> {
                var target = blackboard.get(CommonBlackboardKeys.TARGET);
                return target != null && target.isAlive() && agent.hasLineOfSight(target);
            },
            (agent, blackboard, ticksCharged) -> {
                var target = blackboard.get(CommonBlackboardKeys.TARGET);
                if (target != null) {
                    agent.getLookControl().setLookAt(target, 30.0F, 30.0F);
                }
            },
            (agent, blackboard, ticksCharged) -> {
                var target = blackboard.get(CommonBlackboardKeys.TARGET);
                if (target != null && target.isAlive()) {
                    agent.performRangedAttack(target, BowItem.getPowerForTime(ticksCharged));
                }
            },
            "skeleton_bow_cooldown",
            20,
            18,
            (agent, blackboard) -> agent.setAggressive(true),
            (agent, blackboard, reason) -> agent.setAggressive(false)
        );

        var melee = new AttackProfile<CortexSkeletonEntity, CortexSkeletonGoal>(
            "melee",
            new TimedAttackAction<>("skeleton_melee", 6, 2.0D, "skeleton_melee_cooldown", 20),
            "skeleton_melee_cooldown",
            0.0D,
            2.0D,
            25
        );

        var bow = new AttackProfile<>(
            "bow",
            bowAttack,
            "skeleton_bow_cooldown",
            4.0D,
            15.0D,
            20
        );

        var chase = new MoveToDestinationAction<CortexSkeletonEntity, CortexSkeletonGoal>(
            AStarPathfinder.INSTANCE,
            1.0D,
            1,
            10,
            60
        );

        return new HuntTargetNode<>(CortexSkeletonGoal.HUNT_TARGET, chase, List.of(melee, bow));
    }
}
