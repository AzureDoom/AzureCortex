package com.azure.azurecortex.mixin;

import com.google.common.collect.ImmutableSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.azure.azurecortex.example.SilencedEntityTypeBuilder;

@Mixin(EntityType.Builder.class)
public class MixinEntityTypeBuilder_SilenceDataFixerError implements SilencedEntityTypeBuilder {

    @Final
    @Shadow
    private EntityType.EntityFactory<Entity> factory;

    @Final
    @Shadow
    private MobCategory category;

    @Shadow
    private ImmutableSet<Block> immuneTo;

    @Shadow
    private boolean serialize;

    @Shadow
    private boolean summon;

    @Shadow
    private boolean fireImmune;

    @Shadow
    private boolean canSpawnFarFromPlayer;

    @Shadow
    private int clientTrackingRange;

    @Shadow
    private int updateInterval;

    @Shadow
    private EntityDimensions dimensions;

    @Unique
    @Override
    @SuppressWarnings("unchecked")
    public <T extends Entity> EntityType<T> buildWithoutDataFixerCheck() {
        return new EntityType<>(
            (EntityType.EntityFactory<T>) this.factory,
            this.category,
            this.serialize,
            this.summon,
            this.fireImmune,
            this.canSpawnFarFromPlayer,
            this.immuneTo,
            this.dimensions,
            this.clientTrackingRange,
            this.updateInterval
        );
    }
}
