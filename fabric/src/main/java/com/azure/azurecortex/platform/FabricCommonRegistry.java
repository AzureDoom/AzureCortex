package com.azure.azurecortex.platform;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
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
    public <E extends Mob> Supplier<SpawnEggItem> makeSpawnEggFor(
        Supplier<EntityType<E>> entityType,
        int primaryEggColour,
        int secondaryEggColour,
        Item.Properties itemProperties
    ) {
        return () -> new SpawnEggItem(entityType.get(), primaryEggColour, secondaryEggColour, itemProperties);
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
