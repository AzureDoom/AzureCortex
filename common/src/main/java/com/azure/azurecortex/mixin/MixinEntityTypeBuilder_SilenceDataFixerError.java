package com.azure.azurecortex.mixin;

import net.minecraft.resources.DependantName;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Optional;

import com.azure.azurecortex.example.SilencedEntityTypeBuilder;

@Mixin(EntityType.Builder.class)
public class MixinEntityTypeBuilder_SilenceDataFixerError implements SilencedEntityTypeBuilder {

    @Final
    @Shadow
    private EntityType.EntityFactory<?> factory;

    @Final
    @Shadow
    private MobCategory category;

    @Shadow
    private TagKey<Block> immuneTo;

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

    @Shadow
    private float spawnDimensionsScale;

    @Shadow
    private EntityAttachments.Builder attachments;

    @Shadow
    private FeatureFlagSet requiredFeatures;

    @Shadow
    private DependantName<EntityType<?>, Optional<ResourceKey<LootTable>>> lootTable;

    @Final
    @Shadow
    private DependantName<EntityType<?>, String> descriptionId;

    @Shadow
    private boolean allowedInPeaceful;

    @Unique
    @Override
    @SuppressWarnings({ "unchecked" })
    public <T extends Entity> EntityType<T> buildWithoutDataFixerCheck(
        ResourceKey<EntityType<?>> name
    ) {
        return new EntityType<>(
            (EntityType.EntityFactory<T>) this.factory,
            this.category,
            this.serialize,
            this.summon,
            this.fireImmune,
            this.canSpawnFarFromPlayer,
            this.immuneTo,
            this.dimensions.withAttachments(this.attachments),
            this.spawnDimensionsScale,
            this.clientTrackingRange,
            this.updateInterval,
            this.descriptionId.get(name),
            this.lootTable.get(name),
            this.requiredFeatures,
            this.allowedInPeaceful
        );
    }
}
