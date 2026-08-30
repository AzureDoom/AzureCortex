package com.azure.azurecortex.example;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

import java.util.function.Predicate;

/**
 * Small, reusable {@link Predicate}{@code <LivingEntity>} building blocks matching vanilla's own hostile-mob targeting
 * rules, for use with {@code TargetSensor.nearestMatching}. Combine with {@link Predicate#or} — see
 * {@code CortexZombieEntity}/{@code CortexSkeletonEntity} for how each entity picks a different combination (e.g. a
 * zombie targets villagers, a skeleton doesn't).
 * <p>
 * These are deliberately simple, single-purpose checks mirroring vanilla's {@code NearestAttackableTargetGoal}
 * target-class/selector pairs (e.g. {@code Zombie.addBehaviourGoals}) — not a general-purpose targeting framework.
 */
public final class VanillaTargetPredicates {

    private VanillaTargetPredicates() {}

    /** Matches vanilla's own player-targeting rule: alive, not creative, not spectator. */
    public static Predicate<LivingEntity> players() {
        return candidate -> candidate instanceof Player player
            && player.isAlive()
            && !player.isCreative()
            && !player.isSpectator();
    }

    /** Matches any living {@link AbstractVillager} (regular villagers and wandering traders). */
    public static Predicate<LivingEntity> abstractVillagers() {
        return candidate -> candidate instanceof AbstractVillager && candidate.isAlive();
    }

    /** Matches any living {@link IronGolem}. */
    public static Predicate<LivingEntity> ironGolems() {
        return candidate -> candidate instanceof IronGolem && candidate.isAlive();
    }

    /**
     * Matches a living {@link Turtle}, but only a baby that's currently on land — the same restriction vanilla applies
     * (adult turtles and any turtle still in water are left alone).
     */
    public static Predicate<LivingEntity> babyTurtlesOnLand() {
        return candidate -> candidate instanceof Turtle turtle
            && turtle.isAlive()
            && turtle.isBaby()
            && !turtle.isInWater();
    }

    @SuppressWarnings("deprecation")
    public static Predicate<LivingEntity> onlyPlayersAtNight() {
        return candidate -> candidate instanceof Player player && player.getLightLevelDependentMagicValue() < 0.5;
    }

    @SuppressWarnings("deprecation")
    public static Predicate<LivingEntity> onlyGolemsAtNight() {
        return candidate -> candidate instanceof IronGolem ironGolem && ironGolem
            .getLightLevelDependentMagicValue() < 0.5;
    }
}
