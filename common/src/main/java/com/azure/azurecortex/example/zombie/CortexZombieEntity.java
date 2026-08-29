package com.azure.azurecortex.example.zombie;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.example.VanillaTargetPredicates;
import com.azure.azurecortex.goap.EmergencyDetector;
import com.azure.azurecortex.goap.GoalExecutor;
import com.azure.azurecortex.runtime.CortexRuntime;
import com.azure.azurecortex.sensing.TargetSensor;

/**
 * An example {@code Zombie} subclass wired up to AzureCortex, demonstrating GOAP goal selection, the sensing layer,
 * emergency pre-planning, and a periodic hook, alongside vanilla's own {@code Zombie} (sunlight burning, drowning, baby
 * scaling, and so on all keep working unmodified — only targeting/movement/attack decisions are replaced).
 * <p>
 * See {@link CortexZombieGoal} for the goal types, {@link CortexZombieGoalPlanner} for how they're scored, and
 * {@link CortexZombieTree} for the corresponding behavior tree. Spawn one holding a golden apple in its offhand (e.g.
 * via {@code finalizeSpawn} or a loot-modified spawn egg) to see {@link CortexZombieGoal#EAT_TO_HEAL} in action.
 * <p>
 * This class only wires the AI layer — registering the {@link EntityType}, spawn eggs, loot tables, and so on is left
 * to the consuming mod, exactly as it would be for any other custom entity.
 */
public class CortexZombieEntity extends Zombie {

    private final CortexRuntime<CortexZombieEntity, CortexZombieGoal> runtime;

    private final CortexZombieGoalPlanner goalPlanner = new CortexZombieGoalPlanner();

    /**
     * {@link EmergencyDetector#defaultProbes()} (fire, critical health) plus an extra probe for this entity: wounded
     * below {@link CortexZombieGoalPlanner#EAT_HEALTH_FRACTION} while carrying a golden apple. Without this, a zombie
     * wouldn't consider eating until its next ordinary replan cooldown expired — this lets it react immediately.
     */
    private final List<EmergencyDetector.EmergencyProbe<CortexZombieEntity>> emergencyProbes = buildEmergencyProbes();

    public CortexZombieEntity(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);

        var validity = VanillaTargetPredicates.players()
            .or(VanillaTargetPredicates.abstractVillagers())
            .or(VanillaTargetPredicates.ironGolems())
            .or(VanillaTargetPredicates.babyTurtlesOnLand());

        var targetSensor = new TargetSensor<CortexZombieEntity>(
            TargetSensor.nearestMatching(24.0D, validity),
            10,
            TargetSensor.lineOfSight()
        );

        this.runtime = new CortexRuntime<>(this, targetSensor, CortexZombieTree.create());

        this.runtime.addPeriodicHook("zombie_sync_chase_destination", 5, (agent, blackboard) -> {
            var goalType = blackboard.get(CommonBlackboardKeys.ACTIVE_GOAL_TYPE);
            var target = blackboard.get(CommonBlackboardKeys.TARGET);
            if (goalType == CortexZombieGoal.HUNT_TARGET && target != null) {
                blackboard.set(CommonBlackboardKeys.DESTINATION, target.blockPosition());
            }
        });
    }

    private static List<EmergencyDetector.EmergencyProbe<CortexZombieEntity>> buildEmergencyProbes() {
        var probes = new ArrayList<>(EmergencyDetector.<CortexZombieEntity>defaultProbes());
        probes.add(agent -> {
            var fraction = agent.getMaxHealth() > 0f ? agent.getHealth() / agent.getMaxHealth() : 1f;
            return fraction <= CortexZombieGoalPlanner.EAT_HEALTH_FRACTION
                && agent.getOffhandItem().is(Items.GOLDEN_APPLE);
        });
        return probes;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
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
            || activeGoalType == CortexZombieGoal.NONE
            || activeGoalType == CortexZombieGoal.WANDER;

        var reactiveReplan = isPassive && blackboard.has(CommonBlackboardKeys.TARGET);

        var preplanUrgency = EmergencyDetector.detectPreplanUrgency(this, emergencyProbes);

        if (!reactiveReplan && preplanUrgency == null && cooldowns.isOnCooldown(CommonBlackboardKeys.GOAL_REPLAN))
            return;

        if (!reactiveReplan && !GoalExecutor.shouldReplan(blackboard, currentTick, preplanUrgency, this))
            return;

        cooldowns.set(CommonBlackboardKeys.GOAL_REPLAN, 20);

        var newGoal = goalPlanner.chooseGoal(this, blackboard, cooldowns);
        GoalExecutor.apply(this, blackboard, newGoal);
    }
}
