package com.azure.azurecortex.example.spider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.function.Predicate;

import com.azure.azurecortex.sensing.TargetPrediction;

/**
 * Small helper predicates specific to a wall/ceiling-crawling agent, following the pattern
 * {@link TargetPrediction#standableIn} documents: "mods with different footprint/movement rules (wall-crawlers, flyers)
 * should supply their own predicate instead." Used by {@link CortexSpiderTree}'s {@code INVESTIGATE} branch, passed to
 * {@code InvestigateLastSeenTargetAction}'s {@code standableFactory} constructor parameter.
 */
public final class SpiderTraversalPredicates {

    private SpiderTraversalPredicates() {}

    /**
     * Like {@link TargetPrediction#standableIn}, but accepts any open position adjacent to a solid surface in
     * <em>any</em> axis-aligned direction — floor, wall, or ceiling — rather than only a position with solid footing
     * directly below. Use this in place of {@code standableIn} wherever a wall-crawler validates an extrapolated search
     * or wander point, so a candidate stuck to a wall or ceiling isn't wrongly rejected.
     *
     * @param level the world to query
     */
    public static Predicate<BlockPos> standableForCrawler(Level level) {
        return pos -> {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty())
                return false;

            for (var direction : Direction.values()) {
                var neighbor = pos.relative(direction);
                if (!level.getBlockState(neighbor).getCollisionShape(level, neighbor).isEmpty())
                    return true;
            }
            return false;
        };
    }
}
