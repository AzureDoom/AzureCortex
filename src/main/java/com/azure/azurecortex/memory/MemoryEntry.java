package com.azure.azurecortex.memory;

import java.util.Objects;

/**
 * A single remembered value, timestamped with the game tick it was recorded on so consumers can decide whether it is
 * still fresh enough to act on.
 *
 * @param <T> the type of value remembered
 */
@SuppressWarnings("unused")
public final class MemoryEntry<T> {

    private final T value;

    private final long recordedAtTick;

    /**
     * @param value          the remembered value
     * @param recordedAtTick the game tick this value was recorded
     */
    public MemoryEntry(T value, long recordedAtTick) {
        this.value = value;
        this.recordedAtTick = recordedAtTick;
    }

    public T value() {
        return value;
    }

    public long recordedAtTick() {
        return recordedAtTick;
    }

    /**
     * @param currentTick the tick to compare against
     * @param maxAgeTicks how old (in ticks) this entry is allowed to be and still count as fresh
     * @return {@code true} if this entry is no older than {@code maxAgeTicks}
     */
    public boolean isFresh(long currentTick, long maxAgeTicks) {
        return (currentTick - recordedAtTick) <= maxAgeTicks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof MemoryEntry))
            return false;
        MemoryEntry<?> other = (MemoryEntry<?>) o;
        return recordedAtTick == other.recordedAtTick && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, recordedAtTick);
    }

    @Override
    public String toString() {
        return "MemoryEntry[value=" + value + ", recordedAtTick=" + recordedAtTick + "]";
    }
}
