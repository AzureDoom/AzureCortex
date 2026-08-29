package com.azure.azurecortex.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import com.azure.azurecortex.config.CortexConfig;
import com.azure.azurecortex.navigation.astar.AStarPathfinder;
import com.azure.azurecortex.navigation.traversal.CollisionQueries;

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
     * climb node, walk node, or neither/both.
     *
     * @param mob  the mob this segment belongs to (used to resolve capability for the node classification)
     * @param from the segment start, in world space
     * @param to   the segment end, in world space
     */
    public static void sendParticlePath(Mob mob, Vec3 from, Vec3 to) {
        if (!CortexConfig.get().enablePathfindingDebug)
            return;
        if (!(mob.level instanceof ServerLevel serverLevel))
            return;

        sendNodeMarker(serverLevel, mob, from);
        sendNodeMarker(serverLevel, mob, to);

        for (var i = 0; i <= SEGMENT_STEPS; i++) {
            var t = i / (double) SEGMENT_STEPS;
            serverLevel.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
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

    private static void sendNodeMarker(ServerLevel serverLevel, Mob mob, Vec3 pos) {
        var blockPos = new BlockPos(pos);
        var level = mob.level;

        var isClimb = CollisionQueries.isSafeClimbNode(level, blockPos, mob);
        var isWalk = AStarPathfinder.canStandAt(level, mob, blockPos);

        var marker = isClimb && !isWalk
            ? ParticleTypes.DRIPPING_WATER
            : isWalk && !isClimb
                ? ParticleTypes.SMALL_FLAME
                : ParticleTypes.END_ROD;

        serverLevel.sendParticles(marker, pos.x, pos.y + 0.35D, pos.z, 3, 0.0D, 0.0D, 0.0D, 0.0D);
    }
}
