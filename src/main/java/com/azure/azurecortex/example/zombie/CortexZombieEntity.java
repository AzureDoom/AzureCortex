package com.azure.azurecortex.example.zombie;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.azure.azurecortex.action.combat.TimedAttackAction;
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
 * An example {@code EntityZombie} subclass wired up to AzureCortex, demonstrating GOAP goal selection, the sensing
 * layer, emergency pre-planning, and a periodic hook, alongside vanilla's own zombie behavior. (Sunlight burning, baby
 * scaling, reinforcements, and so on all keep working unmodified, only targeting/movement/attack decisions are
 * replaced.)
 * <p>
 * See {@link CortexZombieGoal} for the goal types, {@link CortexZombieGoalPlanner} for how they're scored, and
 * {@link CortexZombieTree} for the corresponding behavior tree. Give one a golden apple in its offhand (e.g. with
 * {@code /replaceitem entity @e[type=azurecortex:cortex_zombie] slot.weapon.offhand golden_apple}) to see
 * {@link CortexZombieGoal#EAT_TO_HEAL} in action.
 * <p>
 * This class only wires the AI layer; registration lives in {@code ExampleRegistry}.
 */
public class CortexZombieEntity extends EntityZombie {

    private final CortexRuntime<CortexZombieEntity, CortexZombieGoal> runtime;

    private final CortexZombieGoalPlanner goalPlanner = new CortexZombieGoalPlanner();

    /**
     * {@link EmergencyDetector#defaultProbes()} (fire, critical health) plus an extra probe for this entity: wounded
     * below {@link CortexZombieGoalPlanner#EAT_HEALTH_FRACTION} while carrying a golden apple. Without this, a zombie
     * wouldn't consider eating until its next ordinary replan cooldown expired — this lets it react immediately.
     */
    private final List<EmergencyDetector.EmergencyProbe<CortexZombieEntity>> emergencyProbes = buildEmergencyProbes();

    public CortexZombieEntity(World world) {
        super(world);

        Predicate<EntityLivingBase> validity = VanillaTargetPredicates.players()
            .or(VanillaTargetPredicates.villagers())
            .or(VanillaTargetPredicates.ironGolems());

        TargetSensor<CortexZombieEntity> targetSensor = new TargetSensor<>(
            TargetSensor.nearestMatching(24.0D, validity),
            10,
            TargetSensor.lineOfSight()
        );

        this.runtime = new CortexRuntime<>(this, targetSensor, CortexZombieTree.create());

        this.runtime.addPeriodicHook("zombie_sync_chase_destination", 5, (agent, blackboard) -> {
            Object goalType = blackboard.get(CommonBlackboardKeys.ACTIVE_GOAL_TYPE);
            EntityLivingBase target = blackboard.get(CommonBlackboardKeys.TARGET);
            if (goalType == CortexZombieGoal.HUNT_TARGET && target != null) {
                blackboard.set(CommonBlackboardKeys.DESTINATION, new BlockPos(target));
            }
        });
    }

    private static List<EmergencyDetector.EmergencyProbe<CortexZombieEntity>> buildEmergencyProbes() {
        List<EmergencyDetector.EmergencyProbe<CortexZombieEntity>> probes = new ArrayList<>(
            EmergencyDetector.defaultProbes()
        );
        probes.add(agent -> {
            float fraction = agent.getMaxHealth() > 0f ? agent.getHealth() / agent.getMaxHealth() : 1f;
            return fraction <= CortexZombieGoalPlanner.EAT_HEALTH_FRACTION && agent.isHoldingGoldenAppleOffhand();
        });
        return probes;
    }

    boolean isHoldingGoldenAppleOffhand() {
        return getHeldItemOffhand().getItem() == Items.GOLDEN_APPLE;
    }

    /**
     * Replaces vanilla's zombie AI. Vanilla's {@code initEntityAI} would also call {@code applyEntityAI}, adding the
     * attack/target tasks that would fight the Cortex runtime for control of the navigator; neither runs here. Note
     * this is called from {@code EntityLiving}'s constructor, before this class's own fields are initialized, so it
     * must only touch {@code tasks}/{@code targetTasks}.
     */
    @Override
    protected void initEntityAI() {
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        this.tasks.addTask(2, new EntityAILookIdle(this));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!this.world.isRemote && this.isEntityAlive() && !this.isAIDisabled()) {
            tickGoalPlanner();
            runtime.tick();
            updateArmsRaised();
        }
    }

    /**
     * On 1.12.2 the zombie's raised-arms attack pose is driven by {@code setArmsRaised}, which vanilla's
     * {@code EntityAIZombieAttack} toggles and which the Cortex AI replaces. Raise the arms for as long as a melee
     * action is running (its windup and strike), and lower them otherwise. The flag is synced to clients by
     * {@code EntityZombie}'s own data parameter.
     */
    private void updateArmsRaised() {
        boolean attacking = runtime.getCurrentAction() instanceof TimedAttackAction;
        if (attacking != isArmsRaised()) {
            setArmsRaised(attacking);
        }
    }

    /**
     * On 1.12.2, {@code ItemFood} only applies its effects to players — a mob finishing a golden apple just consumes
     * it. Re-apply vanilla's golden apple effects here so {@link CortexZombieGoal#EAT_TO_HEAL} actually heals.
     */
    @Override
    protected void onItemUseFinish() {
        ItemStack using = getActiveItemStack();
        boolean wasGoldenApple = isHandActive() && using.getItem() == Items.GOLDEN_APPLE;
        int metadata = wasGoldenApple ? using.getMetadata() : 0;

        super.onItemUseFinish();

        if (wasGoldenApple && !this.world.isRemote) {
            if (metadata > 0) {
                addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 400, 1));
                addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 6000, 0));
                addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, 6000, 0));
                addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 2400, 3));
            } else {
                addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 100, 1));
                addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 2400, 0));
            }
        }
    }

    private void tickGoalPlanner() {
        Blackboard blackboard = runtime.getBlackboard();
        CooldownTracker cooldowns = runtime.getCooldowns();
        int currentTick = (int) this.world.getTotalWorldTime();

        Object activeGoalType = blackboard.get(CommonBlackboardKeys.ACTIVE_GOAL_TYPE);
        boolean isPassive = activeGoalType == null
            || activeGoalType == CortexZombieGoal.NONE
            || activeGoalType == CortexZombieGoal.WANDER;

        boolean reactiveReplan = isPassive && blackboard.has(CommonBlackboardKeys.TARGET);

        GoalUrgency preplanUrgency = EmergencyDetector.detectPreplanUrgency(
            this,
            emergencyProbes
        );

        if (!reactiveReplan && preplanUrgency == null && cooldowns.isOnCooldown(CommonBlackboardKeys.GOAL_REPLAN))
            return;

        if (
            !reactiveReplan && !GoalExecutor.<CortexZombieEntity, CortexZombieGoal>shouldReplan(
                blackboard,
                currentTick,
                preplanUrgency,
                this
            )
        )
            return;

        cooldowns.set(CommonBlackboardKeys.GOAL_REPLAN, 20);

        PlannedGoal<CortexZombieEntity, CortexZombieGoal> newGoal = goalPlanner.chooseGoal(this, blackboard, cooldowns);
        GoalExecutor.apply(this, blackboard, newGoal);
    }
}
