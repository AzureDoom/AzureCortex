package com.azure.azurecortex.navigation.traversal;

import net.minecraft.entity.EntityLiving;
import net.minecraft.world.World;

import java.util.Objects;
import javax.annotation.Nullable;

import com.azure.azurecortex.api.navigation.MovementCapability;
import com.azure.azurecortex.navigation.astar.PathNodeCache;

/**
 * Bundles the per-search state a {@link TraversalEvaluator} needs: the world, the mob being pathed, its resolved
 * {@link MovementCapability}, and an optional {@link PathNodeCache} for memorizing repeated classification queries.
 */
@SuppressWarnings("unused")
public final class TraversalContext {

    private final World world;

    private final EntityLiving mob;

    private final MovementCapability capability;

    @Nullable
    private final PathNodeCache cache;

    /**
     * @param world      the world being searched
     * @param mob        the mob being pathed
     * @param capability the mob's resolved {@link MovementCapability} (never {@code null} — defaults to
     *                   {@link MovementCapability#DEFAULT})
     * @param cache      a per-search node cache, or {@code null} if the evaluator doesn't use one
     */
    public TraversalContext(
        World world,
        EntityLiving mob,
        MovementCapability capability,
        @Nullable PathNodeCache cache
    ) {
        this.world = world;
        this.mob = mob;
        this.capability = capability;
        this.cache = cache;
    }

    public static TraversalContext of(World world, EntityLiving mob) {
        return new TraversalContext(world, mob, MovementCapability.of(mob), null);
    }

    public static TraversalContext of(World world, EntityLiving mob, PathNodeCache cache) {
        return new TraversalContext(world, mob, MovementCapability.of(mob), cache);
    }

    public World world() {
        return world;
    }

    public EntityLiving mob() {
        return mob;
    }

    public MovementCapability capability() {
        return capability;
    }

    @Nullable
    public PathNodeCache cache() {
        return cache;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TraversalContext))
            return false;
        TraversalContext other = (TraversalContext) o;
        return Objects.equals(world, other.world) && Objects.equals(mob, other.mob)
            && Objects.equals(capability, other.capability) && Objects.equals(cache, other.cache);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, mob, capability, cache);
    }

    @Override
    public String toString() {
        return "TraversalContext[world=" + world + ", mob=" + mob + ", capability=" + capability + ", cache=" + cache
            + "]";
    }
}
