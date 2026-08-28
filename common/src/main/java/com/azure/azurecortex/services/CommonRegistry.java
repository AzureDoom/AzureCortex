package com.azure.azurecortex.services;

import net.minecraft.core.Registry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import java.util.function.Supplier;

public interface CommonRegistry {

    <T> Supplier<T> register(
        Registry<? super T> registry,
        String registryName,
        Supplier<? extends T> supplier
    );

    <E extends Mob> Supplier<SpawnEggItem> makeSpawnEggFor(
        Supplier<EntityType<E>> entityType,
        int primaryEggColour,
        int secondaryEggColour,
        Item.Properties itemProperties
    );

    boolean isDevelopmentEnvironment();

    boolean isModLoaded(String modId);
}
