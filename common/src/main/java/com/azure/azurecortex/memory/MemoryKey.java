package com.azure.azurecortex.memory;

import org.jetbrains.annotations.NotNull;

/**
 * A typed key into an {@link AgentMemory} store: a stable string identifier paired with the {@link Class} of value it
 * holds. Mirrors {@code com.azure.azurecortex.api.blackboard.BlackboardKey}, but for longer-lived, cross-tick memory
 * rather than per-tick AI state.
 *
 * @param <T>  the type of value this key stores
 * @param id   a stable, human-readable identifier
 * @param type the class of value stored under this key
 */
public record MemoryKey<T>(
    String id,
    Class<T> type
) {

    public static <T> MemoryKey<T> of(String id, Class<T> type) {
        return new MemoryKey<>(id, type);
    }

    @Override
    public @NotNull String toString() {
        return id;
    }
}
