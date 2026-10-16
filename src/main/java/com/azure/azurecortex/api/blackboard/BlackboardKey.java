package com.azure.azurecortex.api.blackboard;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * A typed blackboard key: a stable string identifier paired with the {@link Class} of value it stores.
 * <p>
 * Declaring keys this way (rather than a bare {@code String} constant) lets {@link Blackboard#get(BlackboardKey)} infer
 * the correct type without the caller repeating it, and gives mods a single place to document what a key means and what
 * it holds. Two keys are considered the same slot if they share an {@link #id()}; the {@link #type()} is only used for
 * the safe cast on read.
 *
 * @param <T> the type of value this key stores
 */
public final class BlackboardKey<T> {

    private final String id;

    private final Class<T> type;

    /**
     * @param id   a stable, human-readable identifier (also shown in diagnostic output)
     * @param type the class of value stored under this key, used for the safe cast in
     *             {@link Blackboard#get(BlackboardKey)}
     */
    public BlackboardKey(String id, Class<T> type) {
        this.id = id;
        this.type = type;
    }

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
        if (!(o instanceof BlackboardKey))
            return false;
        BlackboardKey<?> other = (BlackboardKey<?>) o;
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
