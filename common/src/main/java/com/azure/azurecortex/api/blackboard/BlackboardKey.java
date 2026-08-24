package com.azure.azurecortex.api.blackboard;

import org.jetbrains.annotations.NotNull;

/**
 * A typed blackboard key: a stable string identifier paired with the {@link Class} of value it stores.
 * <p>
 * Declaring keys this way (rather than a bare {@code String} constant) lets {@link Blackboard#get(BlackboardKey)} infer
 * the correct type without the caller repeating it, and gives mods a single place to document what a key means and what
 * it holds. Two keys are considered the same slot if they share an {@link #id()}; the {@link #type()} is only used for
 * the safe cast on read.
 *
 * @param <T>  the type of value this key stores
 * @param id   a stable, human-readable identifier (also shown in diagnostic output)
 * @param type the class of value stored under this key, used for the safe cast in {@link Blackboard#get(BlackboardKey)}
 */
public record BlackboardKey<T>(
    String id,
    Class<T> type
) {

    /**
     * Creates a new typed key.
     *
     * @param id   a stable, human-readable identifier
     * @param type the class of value stored under this key
     * @param <T>  the type of value this key stores
     * @return a new {@link BlackboardKey}
     */
    public static <T> BlackboardKey<T> of(String id, Class<T> type) {
        return new BlackboardKey<>(id, type);
    }

    @Override
    public @NotNull String toString() {
        return id;
    }
}
