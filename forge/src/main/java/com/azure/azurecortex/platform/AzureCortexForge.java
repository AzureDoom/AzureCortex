package com.azure.azurecortex.platform;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import com.azure.azurecortex.AzureCortex;
import com.azure.azurecortex.example.ExampleRegistry;
import com.azure.azurecortex.example.skeleton.CortexSkeletonEntity;
import com.azure.azurecortex.example.spider.CortexSpiderEntity;
import com.azure.azurecortex.example.zombie.CortexZombieEntity;

@Mod.EventBusSubscriber
@Mod(AzureCortex.MOD_ID)
public final class AzureCortexForge {

    public static DeferredRegister<EntityType<?>> entityTypeDeferredRegister = DeferredRegister.create(
        ForgeRegistries.ENTITIES,
        AzureCortex.MOD_ID
    );

    public static DeferredRegister<Item> itemDeferredRegister = DeferredRegister.create(
        ForgeRegistries.ITEMS,
        AzureCortex.MOD_ID
    );

    public AzureCortexForge() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        AzureCortex.init(FMLPaths.CONFIGDIR.get());
        entityTypeDeferredRegister.register(modEventBus);
        itemDeferredRegister.register(modEventBus);
        modEventBus.addListener(this::createEntityAttributes);
    }

    public void createEntityAttributes(final EntityAttributeCreationEvent event) {
        event.put(ExampleRegistry.CORTEX_SKELETON.get(), CortexSkeletonEntity.createAttributes().build());
        event.put(ExampleRegistry.CORTEX_ZOMBIE.get(), CortexZombieEntity.createAttributes().build());
        event.put(ExampleRegistry.CORTEX_SPIDER.get(), CortexSpiderEntity.createAttributes().build());
    }
}
