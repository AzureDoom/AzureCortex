package com.azure.azurecortex.action.combat;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;

import java.util.List;
import javax.annotation.Nullable;

import com.azure.azurecortex.runtime.CooldownTracker;

/**
 * Picks the best legal {@link AttackProfile} for {@code mob} to use against {@code target} this tick.
 * <p>
 * "Legal" means: line-of-sight/hit resolution is <em>not</em> checked here (that's {@link MeleeHitResolver}'s job at
 * execution time) — only range and cooldown, since those are what a behavior tree needs to know before committing to an
 * action. Among legal candidates, the highest {@link AttackProfile#priority()} wins; ties keep the first-listed
 * candidate.
 */
@SuppressWarnings("unused")
public final class AttackSelector {

    private AttackSelector() {}

    /**
     * @param mob        the attacking mob
     * @param target     the candidate target
     * @param cooldowns  the mob's cooldown tracker
     * @param forceReady if {@code true}, ignores every profile's individual cooldown
     * @param candidates the attacks to choose between, in priority-tie-break order
     * @return the selected {@link AttackProfile}, or {@code null} if none are currently in range and ready
     */
    @Nullable
    public static <E extends EntityLiving, G> AttackProfile<E, G> select(
        EntityLiving mob,
        EntityLivingBase target,
        CooldownTracker cooldowns,
        boolean forceReady,
        List<AttackProfile<E, G>> candidates
    ) {
        double distance = Math.sqrt(mob.getDistanceSq(target));

        AttackProfile<E, G> best = null;
        for (AttackProfile<E, G> candidate : candidates) {
            if (!candidate.inRange(distance))
                continue;
            if (!candidate.isReady(cooldowns, forceReady))
                continue;
            if (best == null || candidate.priority() > best.priority())
                best = candidate;
        }
        return best;
    }
}
