package com.azure.azurecortex.example.zombie;

import net.minecraft.util.EnumHand;

import java.util.Collections;

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
 * Builds the behavior tree for {@link CortexZombieEntity}: one branch per {@link CortexZombieGoal}, arbitrated by a
 * {@link PrioritySelector}.
 * <p>
 * The interesting branches are {@link #EAT_TO_HEAL_PRIORITY} — an ordinary {@link ActionNode} wired to the
 * auto-completing mode of {@link UseItemAction} (see its class docs) — and {@code HUNT_TARGET}, which uses
 * {@link HuntTargetNode} rather than a plain {@link ActionNode} since choosing between chasing and attacking has to
 * happen dynamically against the target's live distance.
 */
public final class CortexZombieTree {

    /**
     * Priority for the eat-to-heal branch. Chosen well above every other action here (including melee's fixed 20) so
     * that once committed via {@link com.azure.azurecortex.api.goal.GoalUrgency#EMERGENCY}, eating actually holds — see
     * {@link UseItemAction}'s docs on why its priority is a constructor parameter rather than a fixed literal.
     */
    private static final int EAT_TO_HEAL_PRIORITY = 90;

    private CortexZombieTree() {}

    public static BehaviorNode<CortexZombieEntity, CortexZombieGoal> create() {
        IdleAction<CortexZombieEntity, CortexZombieGoal> idle = new IdleAction<>();

        WanderAction<CortexZombieEntity, CortexZombieGoal> wander = new WanderAction<>(1.0D, 10.0D, 100);

        InvestigateLastSeenTargetAction<CortexZombieEntity, CortexZombieGoal> investigate =
            new InvestigateLastSeenTargetAction<>(
                AStarPathfinder.INSTANCE,
                1.1D,
                2,
                60,
                100,
                60,
                0.02D,
                2.0D,
                8.0D
            );

        UseItemAction<CortexZombieEntity, CortexZombieGoal> eatGoldenApple = UseItemAction.autoComplete(
            EnumHand.OFF_HAND,
            CortexZombieEntity::isHoldingGoldenAppleOffhand,
            "zombie_eat_cooldown",
            600,
            EAT_TO_HEAL_PRIORITY
        );

        HuntTargetNode<CortexZombieEntity, CortexZombieGoal> hunt = getHunt();

        return PrioritySelector.of(
            new ActionNode<>(idle, 0),
            new Condition<>(
                (agent, blackboard, cooldowns) -> blackboard.get(
                    CommonBlackboardKeys.ACTIVE_GOAL_TYPE
                ) == CortexZombieGoal.WANDER,
                new ActionNode<>(wander, 5)
            ),
            new Condition<>(
                (agent, blackboard, cooldowns) -> blackboard.get(
                    CommonBlackboardKeys.ACTIVE_GOAL_TYPE
                ) == CortexZombieGoal.INVESTIGATE,
                new ActionNode<>(investigate, 8)
            ),
            new Condition<>(
                (agent, blackboard, cooldowns) -> blackboard.get(
                    CommonBlackboardKeys.ACTIVE_GOAL_TYPE
                ) == CortexZombieGoal.EAT_TO_HEAL,
                new ActionNode<>(eatGoldenApple, EAT_TO_HEAL_PRIORITY)
            ),
            hunt
        );
    }

    private static HuntTargetNode<CortexZombieEntity, CortexZombieGoal> getHunt() {
        AttackProfile<CortexZombieEntity, CortexZombieGoal> melee = new AttackProfile<>(
            "melee",
            new TimedAttackAction<>(
                "zombie_melee",
                6,
                2.0D,
                "zombie_melee_cooldown",
                20
            ),
            "zombie_melee_cooldown",
            0.0D,
            2.0D,
            20
        );

        MoveToDestinationAction<CortexZombieEntity, CortexZombieGoal> chase = new MoveToDestinationAction<>(
            AStarPathfinder.INSTANCE,
            1.15D,
            1,
            10,
            60
        );

        return new HuntTargetNode<>(CortexZombieGoal.HUNT_TARGET, chase, Collections.singletonList(melee));
    }
}
