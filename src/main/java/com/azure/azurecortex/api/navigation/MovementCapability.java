package com.azure.azurecortex.api.navigation;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Optional capability an {@code EntityLiving} can implement to customize how the navigation package classifies terrain
 * and entities for that specific mob.
 * <p>
 * AzureCortex's traversal/collision queries have no built-in concept of "this block is dangerous" or "this block is
 * passable despite having a collision box" — those are always mod-specific (Ovomorphosis's resin blocks, danger fluids,
 * and danger entities, for instance). Rather than hard-coding any particular mod's rules, every query that would
 * otherwise consult a fixed block/entity set checks {@code mob instanceof MovementCapability} and defers to it. A mob
 * that does not implement this interface gets the permissive {@link #DEFAULT} behavior: nothing is treated as hazardous
 * and no blocks are special-cased as passable-despite-solid.
 * <p>
 * Implementations are typically added to a mod's own entity base class.
 * <h3>1.12.2 notes</h3> 1.12.2 has no separate fluid state or entity-type registry objects, so {@link #isHazardFluid}
 * receives the liquid's {@link IBlockState} itself and {@link #isHazardEntityType} receives the entity's class.
 */
@SuppressWarnings("unused")
public interface MovementCapability {

    /**
     * Returns {@code true} if {@code state} should be treated as hazardous terrain (e.g. an acid pool, a mod-specific
     * "danger block") that pathfinding and steering should route around.
     *
     * @param world the world
     * @param pos   the position of {@code state}
     * @param state the block state to classify
     * @return {@code true} if this block is hazardous for this mob
     */
    default boolean isHazardBlock(World world, BlockPos pos, IBlockState state) {
        return false;
    }

    /**
     * Returns {@code true} if the liquid {@code fluid} should be treated as hazardous (e.g. lava, a mod-specific
     * "danger fluid") rather than merely "in fluid". Only called for states whose material is a liquid.
     *
     * @param world the world
     * @param pos   the position of {@code fluid}
     * @param fluid the liquid block state to classify
     * @return {@code true} if this fluid is hazardous for this mob
     */
    default boolean isHazardFluid(World world, BlockPos pos, IBlockState fluid) {
        return false;
    }

    /**
     * Returns {@code true} if {@code state} should be treated as passable for this mob even though it has a non-null
     * collision box — the generalization of Ovomorphosis's resin blocks, which xenomorphs pass through freely but which
     * are solid to everything else.
     *
     * @param world the world
     * @param pos   the position of {@code state}
     * @param state the block state to classify
     * @return {@code true} if this block should not block movement for this mob despite its collision box
     */
    default boolean isPassableSolid(World world, BlockPos pos, IBlockState state) {
        return false;
    }

    /**
     * Returns {@code true} if entities of class {@code type} should be treated as a movement hazard this mob should
     * steer away from (see {@code com.azure.azurecortex.navigation.movement.MovementController#dangerEntityRepulsion}).
     *
     * @param type the entity class to classify
     * @return {@code true} if entities of this class should trigger repulsion steering
     */
    default boolean isHazardEntityType(Class<? extends Entity> type) {
        return false;
    }

    /** The permissive default used for mobs that do not implement {@link MovementCapability} at all. */
    MovementCapability DEFAULT = new MovementCapability() {};

    /**
     * Resolves the effective {@link MovementCapability} for {@code mob}: itself if it implements the interface,
     * otherwise {@link #DEFAULT}.
     *
     * @param mob the mob to resolve capability for
     * @return the mob's own capability implementation, or {@link #DEFAULT}
     */
    static MovementCapability of(Object mob) {
        return mob instanceof MovementCapability ? (MovementCapability) mob : DEFAULT;
    }
}
