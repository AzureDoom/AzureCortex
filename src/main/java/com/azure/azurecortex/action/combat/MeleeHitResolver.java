package com.azure.azurecortex.action.combat;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;

import com.azure.azurecortex.util.McCompat;

/**
 * The "execute" half of the choose/execute split: given that some higher-level decision has already picked
 * <em>which</em> attack to run, this is the one place that actually resolves whether a strike lands.
 * <h3>Why this exists</h3> Several attack actions independently re-implementing the same three steps (bounding-box
 * reach check, melee line-of-sight check, damage application) with slightly different inflate radii scattered across
 * the codebase is a recurring failure mode — every new attack is one more copy of that block to keep in sync.
 * Centralizing "can this hit land, and did it" here means a new attack only needs to supply its own timing/animation
 * (see {@link AttackProfile}); the actual hit resolution is shared and only has to be correct in one place.
 * <p>
 * This intentionally does <em>not</em> decide which attack to use, apply cooldowns, or terminate the calling action —
 * those remain the calling action's job, since they differ per attack. This class only answers "given this mob, this
 * target, and this reach, does the hit connect" and applies the damage if so.
 */
public final class MeleeHitResolver {

    private MeleeHitResolver() {}

    /**
     * Attempts to resolve a single melee strike from {@code mob} against {@code target} at the given reach.
     * <p>
     * On a landed hit, this also snaps the mob's look direction onto the target and swings the main-hand arm
     * immediately beforehand, exactly as vanilla's {@code EntityAIAttackMelee} does. On 1.12.2 the swing has to be
     * triggered explicitly — {@code attackEntityAsMob} alone deals damage without any animation — and calling
     * {@link EntityLivingBase#swingArm} on the server is what sends the animation packet to tracking clients.
     *
     * @param mob    the attacking mob
     * @param target the defending target
     * @param reach  the bounding-box inflation distance used for the reach check — callers should pass the same value
     *               used for the range gate that led them to attempt this strike in the first place
     * @return {@code true} if the target was actually struck (in range, had line of sight, and
     *         {@link EntityLivingBase#attackEntityAsMob} was invoked); {@code false} if the strike whiffed for either
     *         reason
     */
    public static boolean tryStrike(EntityLiving mob, EntityLivingBase target, double reach) {
        if (!mob.getEntityBoundingBox().grow(reach).intersects(target.getEntityBoundingBox()))
            return false;

        if (!hasMeleeLineOfSight(mob, target))
            return false;

        mob.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
        mob.swingArm(EnumHand.MAIN_HAND);
        mob.attackEntityAsMob(target);
        return true;
    }

    /**
     * Returns {@code true} if a block-collision-only ray trace between {@code mob}'s and {@code target}'s eye positions
     * is unobstructed.
     *
     * @param mob    the attacking mob
     * @param target the defending target
     * @return {@code true} if nothing solid blocks a straight line between the two eye positions
     */
    public static boolean hasMeleeLineOfSight(EntityLiving mob, EntityLivingBase target) {
        RayTraceResult hit = mob.world.rayTraceBlocks(
            McCompat.eyePosition(mob),
            McCompat.eyePosition(target),
            false,
            true,
            false
        );

        return hit == null || hit.typeOfHit == RayTraceResult.Type.MISS;
    }
}
