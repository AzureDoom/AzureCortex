package com.azure.azurecortex.example;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;

import java.util.function.Predicate;

/**
 * Small, reusable {@link Predicate}{@code <EntityLivingBase>} building blocks matching vanilla's own hostile-mob
 * targeting rules, for use with {@code TargetSensor.nearestMatching}. Combine with {@link Predicate#or} — see
 * {@code CortexZombieEntity}/{@code CortexSkeletonEntity} for how each entity picks a different combination (e.g. a
 * zombie targets villagers, a skeleton doesn't).
 * <p>
 * These are deliberately simple, single-purpose checks mirroring vanilla's {@code EntityAINearestAttackableTarget}
 * target-class pairs (e.g. {@code EntityZombie#applyEntityAI}) — not a general-purpose targeting framework. (The newer
 * versions' baby-turtle rule has no 1.12.2 equivalent; turtles don't exist yet.)
 */
@SuppressWarnings("unused")
public final class VanillaTargetPredicates {

    private VanillaTargetPredicates() {}

    /** Matches vanilla's own player-targeting rule: alive, not creative, not spectator. */
    public static Predicate<EntityLivingBase> players() {
        return candidate -> {
            if (!(candidate instanceof EntityPlayer))
                return false;
            EntityPlayer player = (EntityPlayer) candidate;
            return player.isEntityAlive() && !player.isCreative() && !player.isSpectator();
        };
    }

    /** Matches any living {@link EntityVillager}. */
    public static Predicate<EntityLivingBase> villagers() {
        return candidate -> candidate instanceof EntityVillager && candidate.isEntityAlive();
    }

    /** Matches any living {@link EntityIronGolem}. */
    public static Predicate<EntityLivingBase> ironGolems() {
        return candidate -> candidate instanceof EntityIronGolem && candidate.isEntityAlive();
    }

    public static Predicate<EntityLivingBase> onlyPlayersAtNight() {
        return candidate -> {
            if (!(candidate instanceof EntityPlayer))
                return false;
            EntityPlayer player = (EntityPlayer) candidate;
            return player.isEntityAlive() && !player.isCreative() && !player.isSpectator()
                && player.getBrightness() < 0.5F;
        };
    }

    public static Predicate<EntityLivingBase> onlyGolemsAtNight() {
        return candidate -> candidate instanceof EntityIronGolem && candidate.getBrightness() < 0.5F;
    }
}
