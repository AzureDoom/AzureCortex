package com.azure.azurecortex.platform;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import com.azure.azurecortex.AzureCortex;
import com.azure.azurecortex.example.ExampleRegistry;
import com.azure.azurecortex.example.skeleton.CortexSkeletonEntity;
import com.azure.azurecortex.example.zombie.CortexZombieEntity;

@Mod(AzureCortex.MOD_ID)
public final class AzureCortexForge {

    public static DeferredRegister<EntityType<?>> entityTypeDeferredRegister = DeferredRegister.create(
        ForgeRegistries.ENTITY_TYPES,
        AzureCortex.MOD_ID
    );

    public static DeferredRegister<Item> itemDeferredRegister = DeferredRegister.create(
        ForgeRegistries.ITEMS,
        AzureCortex.MOD_ID
    );

    public AzureCortexForge(FMLJavaModLoadingContext loadingContext) {
        var modEventBus = loadingContext.getModEventBus();
        AzureCortex.init(FMLPaths.CONFIGDIR.get());
        entityTypeDeferredRegister.register(modEventBus);
        itemDeferredRegister.register(modEventBus);
        modEventBus.addListener(this::createEntityAttributes);
        modEventBus.addListener(this::addSpawnPlacements);
        modEventBus.addListener(this::addCreativeTabs);
    }

    public void createEntityAttributes(final EntityAttributeCreationEvent event) {
        event.put(ExampleRegistry.CORTEX_SKELETON.get(), CortexSkeletonEntity.createAttributes().build());
        event.put(ExampleRegistry.CORTEX_ZOMBIE.get(), CortexZombieEntity.createAttributes().build());
    }

    public void addCreativeTabs(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ExampleRegistry.CORTEX_SKELETON_SPAWN_EGG.get());
            event.accept(ExampleRegistry.CORTEX_ZOMBIE_SPAWN_EGG.get());
        }
    }

    public void addSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(
            ExampleRegistry.CORTEX_SKELETON.get(),
            SpawnPlacements.Type.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            CortexSkeletonEntity::checkAnyLightMonsterSpawnRules,
            SpawnPlacementRegisterEvent.Operation.AND
        );
        event.register(
            ExampleRegistry.CORTEX_ZOMBIE.get(),
            SpawnPlacements.Type.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            CortexZombieEntity::checkAnyLightMonsterSpawnRules,
            SpawnPlacementRegisterEvent.Operation.AND
        );
    }
}
