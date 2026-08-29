package com.azure.azurecortex.platform;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import java.util.function.Supplier;

import com.azure.azurecortex.AzureCortex;
import com.azure.azurecortex.services.CommonRegistry;

@SuppressWarnings("unchecked")
public class FabricCommonRegistry implements CommonRegistry {

    private static <T, R extends Registry<? super T>> Supplier<T> registerSupplier(
        R registry,
        String id,
        Supplier<T> object
    ) {
        final T registeredObject = Registry.register(
            (Registry<T>) registry,
            AzureCortex.id(id),
            object.get()
        );

        return () -> registeredObject;
    }

    @Override
    public <T> Supplier<T> register(Registry<? super T> registry, String registryName, Supplier<? extends T> supplier) {
        return (Supplier<T>) registerSupplier(registry, registryName, supplier);
    }

    @Override
    public <E extends Mob> Supplier<SpawnEggItem> registerSpawnEgg(
        String registryName,
        Supplier<EntityType<E>> entityType
    ) {
        var key = ResourceKey.create(
            Registries.ITEM,
            AzureCortex.id(registryName)
        );

        var item = new SpawnEggItem(
            new Item.Properties()
                .setId(key)
                .spawnEgg(entityType.get())
        );

        Registry.register(
            BuiltInRegistries.ITEM,
            key,
            item
        );

        return () -> item;
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
