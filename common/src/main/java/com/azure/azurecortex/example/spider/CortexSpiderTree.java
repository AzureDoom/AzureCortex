package com.azure.azurecortex.example.spider;

import java.util.List;

import com.azure.azurecortex.action.combat.AttackProfile;
import com.azure.azurecortex.action.combat.TimedAttackAction;
import com.azure.azurecortex.action.movement.CrawlToDestinationAction;
import com.azure.azurecortex.action.movement.IdleAction;
import com.azure.azurecortex.action.movement.WanderAction;
import com.azure.azurecortex.action.utility.InvestigateLastSeenTargetAction;
import com.azure.azurecortex.api.behavior.BehaviorNode;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.behavior.composite.PrioritySelector;
import com.azure.azurecortex.behavior.decorator.Condition;
import com.azure.azurecortex.behavior.leaf.ActionNode;
import com.azure.azurecortex.example.HuntTargetNode;
import com.azure.azurecortex.navigation.astar.AStarPathfinder;
import com.azure.azurecortex.navigation.crawl.CrawlTraversalEvaluator;

/**
 * Builds the behavior tree for {@link CortexSpiderEntity}: one branch per {@link CortexSpiderGoal}, arbitrated by a
 * {@link PrioritySelector} — structurally identical to
 * {@code com.azure.azurecortex.example.skeleton.CortexSkeletonTree} apart from a single-attack {@code HUNT_TARGET} and
 * one deliberate difference in the pathfinder each branch is given:
 * <ul>
 * <li>{@code WANDER}/{@code INVESTIGATE} use the ordinary ground-walking pathfinder ({@link AStarPathfinder}) — a
 * spider that's merely wandering or checking a stale sighting doesn't need to commit to a full wall/ceiling climb.
 * {@code INVESTIGATE} does, however, validate its extrapolated search point with
 * {@code SpiderTraversalPredicates#standableForCrawler} rather than the ground-only default, so a sighting last seen on
 * a wall or ceiling isn't wrongly rejected as a candidate — see {@link InvestigateLastSeenTargetAction}'s class docs
 * for that constructor parameter.</li>
 * <li>{@code HUNT_TARGET}'s chase action uses {@link CrawlToDestinationAction} with
 * {@link CrawlTraversalEvaluator#INSTANCE} — this is the branch that actually climbs over walls and across ceilings to
 * close the distance on a live target. See {@link CrawlToDestinationAction}'s class docs for why a dedicated action is
 * needed for that rather than the ordinary {@code MoveToDestinationAction}.</li>
 * </ul>
 */
public final class CortexSpiderTree {

    private CortexSpiderTree() {}

    public static BehaviorNode<CortexSpiderEntity, CortexSpiderGoal> create() {
        var idle = new IdleAction<CortexSpiderEntity, CortexSpiderGoal>();

        var wander = new WanderAction<CortexSpiderEntity, CortexSpiderGoal>(1.0D, 10.0D, 100);

        var investigate = new InvestigateLastSeenTargetAction<CortexSpiderEntity, CortexSpiderGoal>(
            AStarPathfinder.INSTANCE,
            1.0D,
            2,
            60,
            100,
            60,
            0.02D,
            2.0D,
            8.0D,
            SpiderTraversalPredicates::standableForCrawler
        );

        var hunt = getHunt();

        return PrioritySelector.of(
            new ActionNode<>(idle, 0),
            new Condition<>(
                (agent, blackboard, cooldowns) -> blackboard.get(
                    CommonBlackboardKeys.ACTIVE_GOAL_TYPE
                ) == CortexSpiderGoal.WANDER,
                new ActionNode<>(wander, 5)
            ),
            new Condition<>(
                (agent, blackboard, cooldowns) -> blackboard.get(
                    CommonBlackboardKeys.ACTIVE_GOAL_TYPE
                ) == CortexSpiderGoal.INVESTIGATE,
                new ActionNode<>(investigate, 8)
            ),
            hunt
        );
    }

    private static HuntTargetNode<CortexSpiderEntity, CortexSpiderGoal> getHunt() {
        var melee = new AttackProfile<CortexSpiderEntity, CortexSpiderGoal>(
            "melee",
            new TimedAttackAction<>("spider_melee", 4, 2.0D, "spider_melee_cooldown", 15),
            "spider_melee_cooldown",
            0.0D,
            2.0D,
            20
        );

        var chase = new CrawlToDestinationAction<CortexSpiderEntity, CortexSpiderGoal>(
            CrawlTraversalEvaluator.INSTANCE,
            1.1D,
            1,
            10,
            60
        );

        return new HuntTargetNode<>(CortexSpiderGoal.HUNT_TARGET, chase, List.of(melee));
    }
}
