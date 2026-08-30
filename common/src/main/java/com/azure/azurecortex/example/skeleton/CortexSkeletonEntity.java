package com.azure.azurecortex.example.skeleton;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.example.VanillaTargetPredicates;
import com.azure.azurecortex.goap.EmergencyDetector;
import com.azure.azurecortex.goap.GoalExecutor;
import com.azure.azurecortex.runtime.CortexRuntime;
import com.azure.azurecortex.sensing.TargetSensor;

/**
 * An example {@code Skeleton} subclass wired up to AzureCortex, showing the same integration shape as
 * {@link com.azure.azurecortex.example.zombie.CortexZombieEntity} applied to a ranged attacker: GOAP goal selection,
 * the sensing layer, emergency pre-planning, and a periodic hook to keep the chase destination current. Vanilla's own
 * {@code Skeleton} bookkeeping (sunlight burning, drowning, and so on) keeps working unmodified — only
 * targeting/movement/attack decisions are replaced.
 * <p>
 * See {@link CortexSkeletonGoal} for the goal types, {@link CortexSkeletonGoalPlanner} for how they're scored, and
 * {@link CortexSkeletonTree} for the corresponding behavior tree — in particular the bow/melee split inside
 * {@code HUNT_TARGET}.
 * <p>
 * This class only wires the AI layer — registering the {@link EntityType}, spawn eggs, loot tables, and so on is left
 * to the consuming mod, exactly as it would be for any other custom entity.
 */
public class CortexSkeletonEntity extends Skeleton {

    private final CortexRuntime<CortexSkeletonEntity, CortexSkeletonGoal> runtime;

    private final CortexSkeletonGoalPlanner goalPlanner = new CortexSkeletonGoalPlanner();

    public CortexSkeletonEntity(EntityType<? extends Skeleton> entityType, Level level) {
        super(entityType, level);

        var validity = VanillaTargetPredicates.players()
            .or(VanillaTargetPredicates.ironGolems())
            .or(VanillaTargetPredicates.babyTurtlesOnLand());

        var targetSensor = new TargetSensor<CortexSkeletonEntity>(
            TargetSensor.nearestMatching(24.0D, validity),
            10,
            TargetSensor.lineOfSight()
        );

        this.runtime = new CortexRuntime<>(this, targetSensor, CortexSkeletonTree.create());

        this.runtime.addPeriodicHook("skeleton_sync_chase_destination", 5, (agent, blackboard) -> {
            var goalType = blackboard.get(CommonBlackboardKeys.ACTIVE_GOAL_TYPE);
            var target = blackboard.get(CommonBlackboardKeys.TARGET);
            if (goalType == CortexSkeletonGoal.HUNT_TARGET && target != null) {
                blackboard.set(CommonBlackboardKeys.DESTINATION, target.blockPosition());
            }
        });
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Skeleton.createAttributes();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    public void reassessWeaponGoal() {
        // no-op — prevents AbstractSkeleton's ctor/setItemSlot from re-adding
        // vanilla's RangedBowAttackGoal/MeleeAttackGoal, which fight your
        // UseItemAction for control of startUsingItem/stopUsingItem.
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level.isClientSide() && this.isAlive() && !this.isNoAi()) {
            tickGoalPlanner();
            runtime.tick();
        }
    }

    private void tickGoalPlanner() {
        var blackboard = runtime.getBlackboard();
        var cooldowns = runtime.getCooldowns();
        var currentTick = (int) this.level.getGameTime();

        var activeGoalType = blackboard.get(CommonBlackboardKeys.ACTIVE_GOAL_TYPE);
        var isPassive = activeGoalType == null
            || activeGoalType == CortexSkeletonGoal.NONE
            || activeGoalType == CortexSkeletonGoal.WANDER;

        var reactiveReplan = isPassive && blackboard.has(CommonBlackboardKeys.TARGET);

        var preplanUrgency = EmergencyDetector.detectPreplanUrgency(
            this,
            EmergencyDetector.<CortexSkeletonEntity>defaultProbes()
        );

        if (!reactiveReplan && preplanUrgency == null && cooldowns.isOnCooldown(CommonBlackboardKeys.GOAL_REPLAN))
            return;

        if (!reactiveReplan && !GoalExecutor.shouldReplan(blackboard, currentTick, preplanUrgency, this))
            return;

        cooldowns.set(CommonBlackboardKeys.GOAL_REPLAN, 20);

        var newGoal = goalPlanner.chooseGoal(this, blackboard, cooldowns);
        GoalExecutor.apply(this, blackboard, newGoal);
    }
}
