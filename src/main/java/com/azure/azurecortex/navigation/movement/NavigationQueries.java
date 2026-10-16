package com.azure.azurecortex.navigation.movement;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/**
 * Decision-level queries about what kind of movement is required to reach a position: an ordinary walk, a one-block
 * step/jump, or something the ground-walking model can't do on its own (a climb).
 */
@SuppressWarnings("unused")
public final class NavigationQueries {

    private NavigationQueries() {}

    /** Describes the type of movement required to occupy a given block position. */
    public enum MovementType {
        /** The mob can walk to this position normally. */
        WALK,
        /** The mob must jump one block up to reach this position. */
        JUMP,
        /** There is no footing within two blocks; the mob would have to climb (e.g. a ladder) to occupy it. */
        CLIMB
    }

    /**
     * Determines the {@link MovementType} required for the mob to occupy {@code pos}.
     *
     * @param mob the mob being evaluated
     * @param pos the target block position
     * @return the movement type needed
     */
    public static MovementType requiredMovementAt(EntityLiving mob, BlockPos pos) {
        BlockPos below = pos.down();
        IBlockState stateBelow = mob.world.getBlockState(below);

        if (stateBelow.isSideSolid(mob.world, below, EnumFacing.UP)) {
            return MovementType.WALK;
        }

        BlockPos twoBelow = below.down();
        IBlockState stateTwoBelow = mob.world.getBlockState(twoBelow);

        if (stateTwoBelow.isSideSolid(mob.world, twoBelow, EnumFacing.UP)) {
            return MovementType.JUMP;
        }

        return MovementType.CLIMB;
    }
}
