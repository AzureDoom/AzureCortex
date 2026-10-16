package com.azure.azurecortex.example.skeleton;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Predicate;

import com.azure.azurecortex.api.blackboard.Blackboard;
import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.api.goal.GoalUrgency;
import com.azure.azurecortex.example.VanillaTargetPredicates;
import com.azure.azurecortex.goap.EmergencyDetector;
import com.azure.azurecortex.goap.GoalExecutor;
import com.azure.azurecortex.goap.PlannedGoal;
import com.azure.azurecortex.runtime.CooldownTracker;
import com.azure.azurecortex.runtime.CortexRuntime;
import com.azure.azurecortex.sensing.TargetSensor;

/**
 * An example {@code EntitySkeleton} subclass wired up to AzureCortex, showing the same integration shape as
 * {@link com.azure.azurecortex.example.zombie.CortexZombieEntity} applied to a ranged attacker: GOAP goal selection,
 * the sensing layer, emergency pre-planning, and a periodic hook to keep the chase destination current. Vanilla's own
 * skeleton bookkeeping (sunlight burning, equipment on spawn, and so on) keeps working unmodified — only
 * targeting/movement/attack decisions are replaced.
 * <p>
 * See {@link CortexSkeletonGoal} for the goal types, {@link CortexSkeletonGoalPlanner} for how they're scored, and
 * {@link CortexSkeletonTree} for the corresponding behavior tree — in particular the bow/melee split inside
 * {@code HUNT_TARGET}.
 * <p>
 * This class only wires the AI layer; registration lives in {@code ExampleRegistry}.
 */
public class CortexSkeletonEntity extends EntitySkeleton {

    private final CortexRuntime<CortexSkeletonEntity, CortexSkeletonGoal> runtime;

    private final CortexSkeletonGoalPlanner goalPlanner = new CortexSkeletonGoalPlanner();

    private final List<EmergencyDetector.EmergencyProbe<CortexSkeletonEntity>> emergencyProbes = EmergencyDetector
        .defaultProbes();

    public CortexSkeletonEntity(World world) {
        super(world);

        Predicate<EntityLivingBase> validity = VanillaTargetPredicates.players()
            .or(VanillaTargetPredicates.villagers())
            .or(VanillaTargetPredicates.ironGolems());

        TargetSensor<CortexSkeletonEntity> targetSensor = new TargetSensor<>(
            TargetSensor.nearestMatching(24.0D, validity),
            10,
            TargetSensor.lineOfSight()
        );

        this.runtime = new CortexRuntime<>(this, targetSensor, CortexSkeletonTree.create());

        this.runtime.addPeriodicHook("skeleton_sync_chase_destination", 5, (agent, blackboard) -> {
            Object goalType = blackboard.get(CommonBlackboardKeys.ACTIVE_GOAL_TYPE);
            EntityLivingBase target = blackboard.get(CommonBlackboardKeys.TARGET);
            if (goalType == CortexSkeletonGoal.HUNT_TARGET && target != null) {
                blackboard.set(CommonBlackboardKeys.DESTINATION, new BlockPos(target));
            }
        });
    }

    /**
     * Replaces vanilla's skeleton AI (including its target tasks). Called from {@code EntityLiving}'s constructor,
     * before this class's own fields are initialized, so it must only touch {@code tasks}/{@code targetTasks}.
     */
    @Override
    protected void initEntityAI() {
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        this.tasks.addTask(2, new EntityAILookIdle(this));
    }

    @Override
    public void setCombatTask() {
        // no-op — prevents AbstractSkeleton's constructor/setItemStackToSlot/readEntityFromNBT from re-adding
        // vanilla's EntityAIAttackRangedBow/EntityAIAttackMelee, which would fight UseItemAction for control of
        // setActiveHand/resetActiveHand.
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!this.world.isRemote && this.isEntityAlive() && !this.isAIDisabled()) {
            tickGoalPlanner();
            runtime.tick();
        }
    }

    private void tickGoalPlanner() {
        Blackboard blackboard = runtime.getBlackboard();
        CooldownTracker cooldowns = runtime.getCooldowns();
        int currentTick = (int) this.world.getTotalWorldTime();

        Object activeGoalType = blackboard.get(CommonBlackboardKeys.ACTIVE_GOAL_TYPE);
        boolean isPassive = activeGoalType == null
            || activeGoalType == CortexSkeletonGoal.NONE
            || activeGoalType == CortexSkeletonGoal.WANDER;

        boolean reactiveReplan = isPassive && blackboard.has(CommonBlackboardKeys.TARGET);

        GoalUrgency preplanUrgency = EmergencyDetector.detectPreplanUrgency(this, emergencyProbes);

        if (!reactiveReplan && preplanUrgency == null && cooldowns.isOnCooldown(CommonBlackboardKeys.GOAL_REPLAN))
            return;

        if (
            !reactiveReplan && !GoalExecutor.<CortexSkeletonEntity, CortexSkeletonGoal>shouldReplan(
                blackboard,
                currentTick,
                preplanUrgency,
                this
            )
        )
            return;

        cooldowns.set(CommonBlackboardKeys.GOAL_REPLAN, 20);

        PlannedGoal<CortexSkeletonEntity, CortexSkeletonGoal> newGoal = goalPlanner.chooseGoal(
            this,
            blackboard,
            cooldowns
        );
        GoalExecutor.apply(this, blackboard, newGoal);
    }
}
