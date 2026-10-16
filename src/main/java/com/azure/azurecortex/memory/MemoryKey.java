package com.azure.azurecortex.memory;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * A typed key into an {@link AgentMemory} store: a stable string identifier paired with the {@link Class} of value it
 * holds. Mirrors {@code com.azure.azurecortex.api.blackboard.BlackboardKey}, but for longer-lived, cross-tick memory
 * rather than per-tick AI state.
 *
 * @param <T> the type of value this key stores
 */
public final class MemoryKey<T> {

    private final String id;

    private final Class<T> type;

    /**
     * @param id   a stable, human-readable identifier
     * @param type the class of value stored under this key
     */
    public MemoryKey(String id, Class<T> type) {
        this.id = id;
        this.type = type;
    }

    public static <T> MemoryKey<T> of(String id, Class<T> type) {
        return new MemoryKey<>(id, type);
    }

    public String id() {
        return id;
    }

    public Class<T> type() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof MemoryKey))
            return false;
        MemoryKey<?> other = (MemoryKey<?>) o;
        return id.equals(other.id) && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }

    @Override
    @Nonnull
    public String toString() {
        return id;
    }
}
