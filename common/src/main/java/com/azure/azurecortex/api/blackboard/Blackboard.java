package com.azure.azurecortex.api.blackboard;

import java.util.HashMap;
import java.util.Map;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.behavior.BehaviorNode;
import com.azure.azurecortex.runtime.CortexRuntime;

/**
 * A key-value store used to share AI state between {@link BehaviorNode}s and {@link Action}s within a single agent's
 * brain.
 * <p>
 * Each {@link CortexRuntime} owns exactly one blackboard. All stored values are transient and discarded when the agent
 * is removed from the world.
 * <p>
 * Two access styles are supported:
 * <ul>
 * <li>String-keyed {@link #get(String, Class)}/{@link #set(String, Object)} — matches the historical API and keeps
 * debug output readable (keys show up as plain strings).</li>
 * <li>Typed {@link BlackboardKey}-keyed {@link #get(BlackboardKey)}/{@link #set(BlackboardKey, Object)} — avoids
 * repeating the value's {@code Class} at every call site and centralizes the key's name and type in one declaration.
 * Both styles read/write the same underlying storage, keyed by {@link BlackboardKey#id()}.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public final class Blackboard {

    private final Map<String, Object> values = new HashMap<>();

    /**
     * Stores {@code value} under {@code key}, replacing any prior value.
     *
     * @param <T>   the type of value being stored
     * @param key   the string key used to retrieve this value later
     * @param value the value to store
     */
    public <T> void set(String key, T value) {
        values.put(key, value);
    }

    /**
     * Retrieves a value by key, returning {@code null} if the key is absent or the stored object is not an instance of
     * {@code type}.
     *
     * @param <T>  the expected type
     * @param key  the key to look up
     * @param type the expected class of the stored value
     * @return the stored value cast to {@code T}, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        var value = values.get(key);
        return type.isInstance(value) ? (T) value : null;
    }

    /**
     * Returns {@code true} if a value is currently associated with {@code key}.
     *
     * @param key the key to check
     * @return {@code true} if the key is present
     */
    public boolean has(String key) {
        return values.containsKey(key);
    }

    /**
     * Removes the value associated with {@code key}, if any.
     *
     * @param key the key to remove
     */
    public void remove(String key) {
        values.remove(key);
    }

    /**
     * Typed convenience overload of {@link #get(String, Class)} using a {@link BlackboardKey}'s {@code id} and
     * {@code type}.
     *
     * @param key the typed key to look up
     * @return the stored value cast to {@code T}, or {@code null}
     */
    public <T> T get(BlackboardKey<T> key) {
        return get(key.id(), key.type());
    }

    /**
     * Typed convenience overload of {@link #set(String, Object)} using a {@link BlackboardKey}'s {@code id}.
     *
     * @param key   the typed key to store under
     * @param value the value to store
     */
    public <T> void set(BlackboardKey<T> key, T value) {
        values.put(key.id(), value);
    }

    /**
     * Returns {@code true} if a value is currently associated with {@code key}.
     *
     * @param key the typed key to check
     */
    public boolean has(BlackboardKey<?> key) {
        return values.containsKey(key.id());
    }

    /**
     * Removes the value associated with {@code key}, if any.
     *
     * @param key the typed key to remove
     */
    public void remove(BlackboardKey<?> key) {
        values.remove(key.id());
    }
}
