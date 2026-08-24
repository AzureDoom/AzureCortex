package com.azure.azurecortex.navigation.traversal;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import com.azure.azurecortex.api.navigation.MovementCapability;
import com.azure.azurecortex.navigation.astar.PathNodeCache;

/**
 * Bundles the per-search state a {@link TraversalEvaluator} needs: the world, the mob being pathed, its resolved
 * {@link MovementCapability}, and an optional {@link PathNodeCache} for memoizing repeated classification queries.
 *
 * @param level      the world being searched
 * @param mob        the mob being pathed
 * @param capability the mob's resolved {@link MovementCapability} (never {@code null} — defaults to
 *                   {@link MovementCapability#DEFAULT})
 * @param cache      a per-search node cache, or {@code null} if the evaluator doesn't use one
 */
public record TraversalContext(
    Level level,
    Mob mob,
    MovementCapability capability,
    @Nullable PathNodeCache cache
) {

    public static TraversalContext of(Level level, Mob mob) {
        return new TraversalContext(level, mob, MovementCapability.of(mob), null);
    }

    public static TraversalContext of(Level level, Mob mob, PathNodeCache cache) {
        return new TraversalContext(level, mob, MovementCapability.of(mob), cache);
    }
}
