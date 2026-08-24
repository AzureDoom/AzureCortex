package com.azure.azurecortex.api.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Optional capability a {@code Mob} can implement to customize how the navigation package classifies terrain and
 * entities for that specific mob.
 * <p>
 * AzureCortex's traversal/collision queries have no built-in concept of "this block tag is dangerous" or "this block is
 * passable despite having a collision shape" — those are always mod-specific (Ovomorphosis's resin blocks, danger
 * fluids, and danger-entity tags, for instance). Rather than hard-coding any particular mod's tags, every query that
 * used to consult a fixed tag set now checks {@code mob instanceof MovementCapability} and defers to it, exactly the
 * same pattern the framework already uses for wall-crawling via
 * {@code com.azure.azurecortex.navigation.crawl.CrawlCapability}. A mob that does not implement this interface gets the
 * permissive {@link #DEFAULT} behavior: nothing is treated as hazardous and no blocks are special-cased as
 * passable-despite-solid.
 * <p>
 * Implementations are typically mixed into a mod's own entity base class, mirroring how {@code CrawlCapability} is
 * expected to be applied.
 */
@SuppressWarnings("unused")
public interface MovementCapability {

    /**
     * Returns {@code true} if {@code state} should be treated as hazardous terrain (e.g. an acid pool, a mod-specific
     * "danger block" tag) that pathfinding and steering should route around.
     *
     * @param level the world
     * @param pos   the position of {@code state}
     * @param state the block state to classify
     * @return {@code true} if this block is hazardous for this mob
     */
    default boolean isHazardBlock(Level level, BlockPos pos, BlockState state) {
        return false;
    }

    /**
     * Returns {@code true} if {@code fluid} should be treated as hazardous (e.g. lava, a mod-specific "danger fluid"
     * tag) rather than merely "in fluid".
     *
     * @param level the world
     * @param pos   the position of {@code fluid}
     * @param fluid the fluid state to classify
     * @return {@code true} if this fluid is hazardous for this mob
     */
    default boolean isHazardFluid(Level level, BlockPos pos, FluidState fluid) {
        return false;
    }

    /**
     * Returns {@code true} if {@code state} should be treated as passable for this mob even though it has a non-empty
     * collision shape — the generalization of Ovomorphosis's resin blocks, which xenomorphs pass through freely but
     * which are solid to everything else.
     *
     * @param level the world
     * @param pos   the position of {@code state}
     * @param state the block state to classify
     * @return {@code true} if this block should not block movement for this mob despite its collision shape
     */
    default boolean isPassableSolid(Level level, BlockPos pos, BlockState state) {
        return false;
    }

    /**
     * Returns {@code true} if entities of {@code type} should be treated as a movement hazard this mob should steer
     * away from (see {@code com.azure.azurecortex.navigation.movement.MovementController#dangerEntityRepulsion}).
     *
     * @param type the entity type to classify
     * @return {@code true} if entities of this type should trigger repulsion steering
     */
    default boolean isHazardEntityType(EntityType<?> type) {
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
        return mob instanceof MovementCapability capability ? capability : DEFAULT;
    }
}
