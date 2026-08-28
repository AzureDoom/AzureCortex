package com.azure.azurecortex.example;

import net.minecraft.world.entity.Mob;

import java.util.List;

import com.azure.azurecortex.action.combat.AttackProfile;
import com.azure.azurecortex.action.combat.AttackSelector;
import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.behavior.BehaviorNode;
import com.azure.azurecortex.api.behavior.BehaviorResult;
import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * A small custom {@link BehaviorNode}, shared by the zombie and skeleton examples, that demonstrates going beyond the
 * built-in composite/decorator/leaf nodes: while the agent's active goal type equals {@code huntGoalType}, this node
 * decides <em>every tick</em> between closing the distance to {@link CommonBlackboardKeys#TARGET} (via
 * {@code chaseAction}) and firing off whichever {@link AttackProfile} is currently in range and off cooldown (via
 * {@link AttackSelector}). That choice has to be made against a moving target's live distance each tick — not something
 * a static tree shape built from {@code PrioritySelector}/{@code Condition}/{@code ActionNode} alone can express, since
 * it depends on data ({@link AttackSelector#select}'s result), not just a fixed precondition.
 * <p>
 * <b>On priority:</b> this node passes each candidate's own {@link Action#priority()} straight through to
 * {@link BehaviorResult#run}, rather than inventing a separate number here. That matters because {@code CortexRuntime}
 * compares a <em>running</em> action's eligibility for continued preemption using that same {@link Action#priority()}
 * method, not whatever priority it was originally selected with — so for consistent preemption behavior across ticks, a
 * candidate's selection priority and its priority while running should always be the same value. See
 * {@code UseItemAction}'s docs for a case where that value is a constructor parameter specifically so two very
 * different usages (a bow charge vs. an emergency heal item) can carry different weights.
 *
 * @param <E> the agent type
 * @param <G> the mod-defined goal-type enum
 */
public final class HuntTargetNode<E extends Mob, G> implements BehaviorNode<E, G> {

    private final G huntGoalType;

    private final Action<E, G> chaseAction;

    private final List<AttackProfile<E, G>> attackProfiles;

    /**
     * @param huntGoalType   the goal-type value this node is active for; any other active goal type makes this node
     *                       return {@link BehaviorResult#none()} without consulting anything else
     * @param chaseAction    the action to run when no {@link AttackProfile} is currently legal (out of range or every
     *                       candidate is on cooldown) — typically a movement action driven by
     *                       {@link CommonBlackboardKeys#DESTINATION}
     * @param attackProfiles the attacks to choose between once in range; see {@link AttackSelector}
     */
    public HuntTargetNode(G huntGoalType, Action<E, G> chaseAction, List<AttackProfile<E, G>> attackProfiles) {
        this.huntGoalType = huntGoalType;
        this.chaseAction = chaseAction;
        this.attackProfiles = attackProfiles;
    }

    @Override
    public BehaviorResult<E, G> tick(E agent, Blackboard blackboard, CooldownTracker cooldowns) {
        var activeGoalType = blackboard.get(CommonBlackboardKeys.ACTIVE_GOAL_TYPE);
        if (!huntGoalType.equals(activeGoalType))
            return BehaviorResult.none();

        var target = blackboard.get(CommonBlackboardKeys.TARGET);
        if (target == null || !target.isAlive())
            return BehaviorResult.none();

        var attack = AttackSelector.select(agent, target, cooldowns, false, attackProfiles);
        if (attack != null) {
            return BehaviorResult.run(attack.action(), attack.action().priority());
        }

        return BehaviorResult.run(chaseAction, chaseAction.priority());
    }
}
