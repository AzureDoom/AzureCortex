package com.azure.azurecortex.action.combat;

import net.minecraft.world.entity.Mob;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * The "choose" half of the choose/execute split: a data-only description of one attack an agent can pick between, used
 * by {@link AttackSelector} to decide which attack to run this tick without any attack-specific logic living in the
 * behavior tree itself.
 * <h3>Why this exists</h3> Deciding between several attacks by hand means a cascade of range/cooldown checks directly
 * in a tree class — one {@code if} block per attack, in a fixed order, that every new attack has to be manually
 * threaded into at the right priority. With {@link AttackProfile}, each attack is just an entry in a list;
 * {@link AttackSelector} picks the best-scoring one that is currently legal. Adding an attack is adding one profile,
 * not editing tree control flow.
 * <p>
 * This only describes <em>when an attack is eligible and how urgently it should be preferred</em> — the actual
 * {@link Action} it wraps still owns its own animation timing and, via {@link MeleeHitResolver}, its own hit
 * resolution. {@link AttackProfile} does not replace either of those; it sits above them as the selection criteria.
 *
 * @param <E>         the agent type this attack applies to
 * @param <G>         the mod-defined goal-type enum used for GOAP feedback attribution
 * @param name        a short, stable identifier for logging/diagnostics; by convention matches the action's cooldown
 *                    key
 * @param action      the {@link Action} that runs this attack once selected
 * @param cooldownKey the {@link CooldownTracker} key that must be ready for this attack to be eligible
 * @param minRange    the minimum distance (blocks) the target must be at for this attack to be usable, or {@code 0} for
 *                    no minimum
 * @param maxRange    the maximum distance (blocks) the target may be at for this attack to be usable
 * @param priority    tie-breaker when more than one profile is eligible in the same tick — higher wins; ties are broken
 *                    by list order (first listed wins)
 */
public record AttackProfile<E extends Mob, G>(
    String name,
    Action<E, G> action,
    String cooldownKey,
    double minRange,
    double maxRange,
    int priority
) {

    /**
     * Returns {@code true} if {@code distance} (blocks, not squared) falls within this profile's usable range band.
     */
    public boolean inRange(double distance) {
        return distance >= minRange && distance <= maxRange;
    }

    /**
     * Returns {@code true} if this attack's cooldown has expired, or if {@code force} is {@code true} (use for contexts
     * that bypass individual attack cooldowns entirely, e.g. a defensive last-stand mode).
     */
    public boolean isReady(CooldownTracker cooldowns, boolean force) {
        return force || cooldowns.ready(cooldownKey);
    }
}
