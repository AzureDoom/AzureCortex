package com.azure.azurecortex.sensing;

import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.math.BlockPos;

/**
 * Stateless queries about an agent's ambient environment: light level, submersion, and similar conditions that many
 * planners and actions across different mods end up needing (the "is it dark here" check backing
 * {@code com.azure.azurecortex.goap.WorldStateSnapshot}, or the "is this mob currently in water" check a swim action
 * needs, are the same kind of question every time).
 * <p>
 * Unlike {@link TargetSensor}/{@link HazardSensor}, this is not a periodic {@link Sensor} — these are cheap enough to
 * call directly wherever needed rather than caching onto the blackboard every tick.
 */
@SuppressWarnings("unused")
public final class EnvironmentSensor {

    private EnvironmentSensor() {}

    /**
     * Returns {@code true} if the ambient light level at the agent's current position is at or below {@code threshold}.
     *
     * @param agent     the agent to check
     * @param threshold the maximum light level still considered "dark"
     */
    public static boolean isInDarkness(EntityLiving agent, int threshold) {
        return agent.world.getLightFromNeighbors(new BlockPos(agent)) <= threshold;
    }

    /**
     * Returns {@code true} if the agent is currently submerged in water.
     */
    public static boolean isInWater(EntityLiving agent) {
        return agent.isInsideOfMaterial(Material.WATER) || agent.isInWater();
    }

    /**
     * Returns the agent's current health as a fraction of its max health, in {@code [0, 1]} (or {@code 1} if the agent
     * reports zero or negative max health).
     */
    public static float healthFraction(EntityLiving agent) {
        float maxHealth = agent.getMaxHealth();
        return maxHealth > 0f ? agent.getHealth() / maxHealth : 1f;
    }
}
