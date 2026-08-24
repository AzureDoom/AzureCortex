package com.azure.azurecortex.memory;

/**
 * A single remembered value, timestamped with the game tick it was recorded on so consumers can decide whether it is
 * still fresh enough to act on.
 *
 * @param <T>            the type of value remembered
 * @param value          the remembered value
 * @param recordedAtTick the game tick this value was recorded
 */
public record MemoryEntry<T>(
    T value,
    long recordedAtTick
) {

    /**
     * @param currentTick the tick to compare against
     * @param maxAgeTicks how old (in ticks) this entry is allowed to be and still count as fresh
     * @return {@code true} if this entry is no older than {@code maxAgeTicks}
     */
    public boolean isFresh(long currentTick, long maxAgeTicks) {
        return (currentTick - recordedAtTick) <= maxAgeTicks;
    }
}
