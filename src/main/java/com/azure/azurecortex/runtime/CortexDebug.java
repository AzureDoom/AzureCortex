package com.azure.azurecortex.runtime;

import net.minecraft.entity.EntityLiving;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

import com.azure.azurecortex.config.CortexConfig;
import com.azure.azurecortex.navigation.astar.AStarPathfinder;

/**
 * Server-side particle visualization for pathfinding, gated by {@link CortexConfig#enablePathfindingDebug}.
 * <p>
 * Intended purely for development/debugging a mod built on AzureCortex; has no effect on AI decisions.
 */
public final class CortexDebug {

    private static final int SEGMENT_STEPS = 4;

    private CortexDebug() {}

    /**
     * Draws a short particle segment between two path waypoints, plus a small marker at each end classifying it as a
     * walkable node (flame) or not (end rod).
     *
     * @param mob  the mob this segment belongs to (used to resolve capability for the node classification)
     * @param from the segment start, in world space
     * @param to   the segment end, in world space
     */
    public static void sendParticlePath(EntityLiving mob, Vec3d from, Vec3d to) {
        if (!CortexConfig.get().enablePathfindingDebug)
            return;
        if (!(mob.world instanceof WorldServer))
            return;
        WorldServer serverWorld = (WorldServer) mob.world;

        sendNodeMarker(serverWorld, mob, from);
        sendNodeMarker(serverWorld, mob, to);

        for (int i = 0; i <= SEGMENT_STEPS; i++) {
            double t = i / (double) SEGMENT_STEPS;
            serverWorld.spawnParticle(
                EnumParticleTypes.VILLAGER_HAPPY,
                from.x + (to.x - from.x) * t,
                from.y + (to.y - from.y) * t,
                from.z + (to.z - from.z) * t,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D
            );
        }
    }

    private static void sendNodeMarker(WorldServer serverWorld, EntityLiving mob, Vec3d pos) {
        BlockPos blockPos = new BlockPos(pos.x, pos.y, pos.z);

        boolean isWalk = AStarPathfinder.canStandAt(mob.world, mob, blockPos);

        EnumParticleTypes marker = isWalk ? EnumParticleTypes.FLAME : EnumParticleTypes.END_ROD;

        serverWorld.spawnParticle(marker, pos.x, pos.y + 0.35D, pos.z, 3, 0.0D, 0.0D, 0.0D, 0.0D);
    }
}
