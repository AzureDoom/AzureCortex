package com.azure.azurecortex.platform;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

import java.util.function.Supplier;

import com.azure.azurecortex.services.CommonRegistry;

public class NeoForgeCommonRegistry implements CommonRegistry {

    @SuppressWarnings("unchecked")
    @Override
    public <T> Supplier<T> register(Registry<? super T> registry, String registryName, Supplier<? extends T> supplier) {
        if (registry == BuiltInRegistries.ITEM) {
            return (Supplier<T>) AzureCortexNeoForge.itemDeferredRegister.register(
                registryName,
                (Supplier<Item>) supplier
            );
        } else if (registry == BuiltInRegistries.ENTITY_TYPE) {
            return (Supplier<T>) AzureCortexNeoForge.entityTypeDeferredRegister.register(
                registryName,
                (Supplier<EntityType<?>>) supplier
            );
        }

        throw new IllegalArgumentException(
            "Received registration attempt for an unhandled registry. Registry: " + registry
        );
    }

    @Override
    public <E extends Mob> Supplier<SpawnEggItem> registerSpawnEgg(
        String registryName,
        Supplier<EntityType<E>> entityType
    ) {
        return AzureCortexNeoForge.itemDeferredRegister.registerItem(
            registryName,
            properties -> new SpawnEggItem(
                properties.spawnEgg(entityType.get())
            )
        );
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.getCurrent().isProduction();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
