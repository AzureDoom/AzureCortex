package com.azure.azurecortex.example;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public interface SilencedEntityTypeBuilder {

    <T extends Entity> EntityType<T> buildWithoutDataFixerCheck(
        ResourceKey<EntityType<?>> name
    );
}
