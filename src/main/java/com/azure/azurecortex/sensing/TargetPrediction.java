package com.azure.azurecortex.sensing;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.function.Predicate;
import javax.annotation.Nullable;

import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.util.McCompat;

/**
 * Extrapolates a believable search point from a stale "last seen" sighting, instead of an investigate-style action only
 * ever walking to the exact block a target occupied at the moment visibility was lost.
 * <p>
 * Pairs with {@link TargetSensor}'s visibility-gated {@link CommonBlackboardKeys#LAST_SEEN_POS}/
 * {@link CommonBlackboardKeys#LAST_SEEN_VELOCITY}/{@link CommonBlackboardKeys#LAST_SEEN_TICK}: given those three plus
 * the current tick, {@link #predictInterceptPosition} projects the sighting forward along the target's last-known
 * heading, clamped to a plausible travel distance and rejected outright once the sighting is too stale, the target was
 * essentially stationary, or the projected point isn't somewhere worth searching.
 */
@SuppressWarnings("unused")
public final class TargetPrediction {

    private TargetPrediction() {}

    /**
     * Projects {@code lastSeenPos} forward along {@code lastSeenVelocity} by however far the target could plausibly
     * have traveled since {@code lastSeenTick}, or falls back to {@code lastSeenPos} unchanged whenever extrapolating
     * isn't warranted: no recorded velocity/tick, the sighting is older than {@code maxStalenessTicks}, the target was
     * moving slower than {@code minSpeed} when last seen, or the projected point fails {@code standable}.
     *
     * @param lastSeenPos       last position with genuine visibility, e.g. {@link CommonBlackboardKeys#LAST_SEEN_POS}
     * @param lastSeenVelocity  the target's horizontal velocity at that moment, or {@code null} if unknown
     * @param lastSeenTick      game tick of that sighting, or {@code null} if unknown
     * @param currentTick       the current game tick
     * @param maxStalenessTicks give up extrapolating once this many ticks have elapsed since the sighting — beyond this
     *                          the target could plausibly be almost anywhere, so a raw last-seen walk is the more
     *                          honest signal
     * @param minSpeed          horizontal speed (blocks/tick) below which the target is treated as having been
     *                          essentially stationary; extrapolating a barely-moving target produces a worse guess than
     *                          the raw last-seen position
     * @param minDistance       floor on the extrapolated travel distance, in blocks
     * @param maxDistance       ceiling on the extrapolated travel distance, in blocks
     * @param standable         tests whether a candidate predicted position is a plausible place to search; reject and
     *                          fall back to {@code lastSeenPos} when it isn't (e.g. embedded in a wall around the very
     *                          corner that broke line of sight) — see {@link #standableIn(World)} for the ordinary
     *                          ground-mob case
     * @return the predicted interception point, or {@code lastSeenPos} unchanged if extrapolation isn't warranted
     */
    public static BlockPos predictInterceptPosition(
        BlockPos lastSeenPos,
        @Nullable Vec3d lastSeenVelocity,
        @Nullable Integer lastSeenTick,
        int currentTick,
        int maxStalenessTicks,
        double minSpeed,
        double minDistance,
        double maxDistance,
        Predicate<BlockPos> standable
    ) {
        if (lastSeenVelocity == null || lastSeenTick == null)
            return lastSeenPos;

        int elapsed = currentTick - lastSeenTick;
        if (elapsed < 0 || elapsed > maxStalenessTicks)
            return lastSeenPos;

        double horizSpeed = Math.sqrt(McCompat.horizontalLengthSqr(lastSeenVelocity));
        if (horizSpeed < minSpeed)
            return lastSeenPos;

        double predictionDistance = MathHelper.clamp(horizSpeed * elapsed, minDistance, maxDistance);
        double dirX = lastSeenVelocity.x / horizSpeed;
        double dirZ = lastSeenVelocity.z / horizSpeed;

        BlockPos predicted = new BlockPos(
            MathHelper.floor(lastSeenPos.getX() + dirX * predictionDistance),
            lastSeenPos.getY(),
            MathHelper.floor(lastSeenPos.getZ() + dirZ * predictionDistance)
        );

        return standable.test(predicted) ? predicted : lastSeenPos;
    }

    /**
     * A ground-mob {@code standable} predicate: {@code true} for open space with solid footing beneath it. Mods with
     * different footprint/movement rules (flyers, swimmers, ...) should supply their own predicate instead.
     *
     * @param world the world to query
     */
    public static Predicate<BlockPos> standableIn(World world) {
        return pos -> McCompat.hasNoCollision(world, pos) && !McCompat.hasNoCollision(world, pos.down());
    }
}
