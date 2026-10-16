package com.azure.azurecortex.util;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;

/**
 * Small, stateless helpers that stand in for newer-version conveniences the 1.12.2 API either lacks or names
 * differently (collision-shape emptiness, fluid states, {@code Vec3.atCenterOf}, Manhattan distance, and so on).
 * <p>
 * Keeping these in one place means the rest of the framework reads close to its newer-version counterparts, and any
 * 1.12.2-specific quirk only has to be handled once.
 */
@SuppressWarnings("unused")
public final class McCompat {

    private McCompat() {}

    /**
     * Returns {@code true} if the block at {@code pos} has no collision box — the 1.12.2 equivalent of
     * {@code getCollisionShape(level, pos).isEmpty()}.
     */
    public static boolean hasNoCollision(IBlockAccess world, BlockPos pos) {
        return hasNoCollision(world, pos, world.getBlockState(pos));
    }

    /** As {@link #hasNoCollision(IBlockAccess, BlockPos)}, for a state the caller already looked up. */
    public static boolean hasNoCollision(IBlockAccess world, BlockPos pos, IBlockState state) {
        return state.getCollisionBoundingBox(world, pos) == null;
    }

    /**
     * Returns {@code true} if {@code state} is a liquid. 1.12.2 has no separate fluid state: fluids are ordinary blocks
     * (vanilla {@code BlockLiquid}, Forge {@code BlockFluidBase}) with a liquid {@code Material}.
     */
    public static boolean isLiquid(IBlockState state) {
        return state.getMaterial().isLiquid();
    }

    /** The entity's position as a vector (feet, horizontally centered). */
    public static Vec3d position(Entity entity) {
        return new Vec3d(entity.posX, entity.posY, entity.posZ);
    }

    /** The entity's eye position. Avoids {@code Entity#getPositionEyes}, which is client-only on 1.12.2. */
    public static Vec3d eyePosition(Entity entity) {
        return new Vec3d(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
    }

    /** The entity's current motion vector. */
    public static Vec3d motion(Entity entity) {
        return new Vec3d(entity.motionX, entity.motionY, entity.motionZ);
    }

    /** Sets the entity's motion vector. */
    public static void setMotion(Entity entity, Vec3d motion) {
        setMotion(entity, motion.x, motion.y, motion.z);
    }

    /** Sets the entity's motion vector. */
    public static void setMotion(Entity entity, double x, double y, double z) {
        entity.motionX = x;
        entity.motionY = y;
        entity.motionZ = z;
    }

    public static double lengthSqr(Vec3d vec) {
        return vec.x * vec.x + vec.y * vec.y + vec.z * vec.z;
    }

    public static double length(Vec3d vec) {
        return Math.sqrt(lengthSqr(vec));
    }

    public static double horizontalLengthSqr(Vec3d vec) {
        return vec.x * vec.x + vec.z * vec.z;
    }

    /** The center of the block at {@code pos}. */
    public static Vec3d atCenterOf(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    /** The horizontal center of the bottom face of the block at {@code pos}. */
    public static Vec3d atBottomCenterOf(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    /** The Manhattan (taxicab) distance between two block positions. */
    public static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    /** Returns {@code true} if {@code a} is strictly closer than {@code distance} blocks to {@code b}. */
    public static boolean closerThan(BlockPos a, BlockPos b, double distance) {
        return a.distanceSq(b) < distance * distance;
    }
}
