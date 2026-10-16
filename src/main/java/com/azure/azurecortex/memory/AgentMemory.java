package com.azure.azurecortex.memory;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A small, timestamped key-value store for state that should outlive a single behavior-tree tick but is still
 * conceptually "what this agent (or group of agents) remembers" rather than core simulation state.
 * <p>
 * Unlike {@link com.azure.azurecortex.api.blackboard.Blackboard}, entries here carry a {@link MemoryEntry} timestamp so
 * callers can decide for themselves how stale a remembered value is allowed to be before it's worth refreshing.
 */
@SuppressWarnings("unused")
public final class AgentMemory {

    private final Map<String, MemoryEntry<?>> entries = new HashMap<>();

    /**
     * Remembers {@code value} under {@code key}, timestamped at {@code currentTick}.
     */
    public <T> void remember(MemoryKey<T> key, T value, long currentTick) {
        entries.put(key.id(), new MemoryEntry<>(value, currentTick));
    }

    /**
     * Returns the remembered entry for {@code key}, or {@code null} if nothing has been remembered under it (or the
     * stored value isn't an instance of the key's type).
     */
    @SuppressWarnings("unchecked")
    public <T> MemoryEntry<T> recall(MemoryKey<T> key) {
        MemoryEntry<?> entry = entries.get(key.id());
        if (entry == null || !key.type().isInstance(entry.value()))
            return null;
        return (MemoryEntry<T>) entry;
    }

    /**
     * Convenience overload of {@link #recall(MemoryKey)} that returns just the value, or {@code null} if nothing is
     * remembered or the entry is not fresh enough.
     *
     * @param key         the key to look up
     * @param currentTick the current game tick
     * @param maxAgeTicks how old the entry is allowed to be
     */
    public <T> T recallIfFresh(MemoryKey<T> key, long currentTick, long maxAgeTicks) {
        MemoryEntry<T> entry = recall(key);
        if (entry == null || !entry.isFresh(currentTick, maxAgeTicks))
            return null;
        return entry.value();
    }

    /** Forgets whatever is remembered under {@code key}. */
    public void forget(MemoryKey<?> key) {
        entries.remove(key.id());
    }

    /** Returns {@code true} if something is currently remembered under {@code key}. */
    public boolean has(MemoryKey<?> key) {
        return entries.containsKey(key.id());
    }

    /**
     * Convenience helper generalizing the "find nearest position matching a condition, within range, and remember it"
     * pattern: scans a cube of positions around {@code origin} out to {@code radius} blocks, returns the nearest match,
     * and — if found — remembers it under {@code key} so a caller can skip re-scanning next tick and instead use
     * {@link #recallIfFresh} until the memory goes stale.
     * <p>
     * This is a plain brute-force scan; for large radii or hot-path use, a mod should maintain its own spatial index
     * (as {@code ResinWebRegistry} does in Ovomorphosis) and remember the result here rather than calling this helper
     * every time.
     * <p>
     * {@code matches} is handed a mutable position that is reused between candidates; call
     * {@link BlockPos#toImmutable()} on it if the predicate needs to keep a reference.
     *
     * @param world       the world to scan
     * @param origin      the position to scan outward from
     * @param radius      the scan radius in blocks
     * @param matches     predicate deciding whether a candidate position is acceptable
     * @param key         where to remember the result, if found
     * @param currentTick the current game tick, used for the remembered timestamp
     * @return the nearest matching position, if any
     */
    public Optional<BlockPos> findAndRememberNearest(
        World world,
        BlockPos origin,
        double radius,
        Predicate<BlockPos> matches,
        MemoryKey<BlockPos> key,
        long currentTick
    ) {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        int intRadius = (int) Math.ceil(radius);
        double radiusSq = radius * radius;

        for (
            BlockPos pos : BlockPos.getAllInBoxMutable(
                origin.add(-intRadius, -intRadius, -intRadius),
                origin.add(intRadius, intRadius, intRadius)
            )
        ) {
            double distSq = pos.distanceSq(origin);
            if (distSq > radiusSq || distSq >= bestDistSq)
                continue;
            if (!matches.test(pos))
                continue;
            best = pos.toImmutable();
            bestDistSq = distSq;
        }

        if (best != null) {
            remember(key, best, currentTick);
        }

        return Optional.ofNullable(best);
    }
}
