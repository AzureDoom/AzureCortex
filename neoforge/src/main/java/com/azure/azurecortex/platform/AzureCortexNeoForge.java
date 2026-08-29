package com.azure.azurecortex.platform;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.azure.azurecortex.AzureCortex;
import com.azure.azurecortex.example.ExampleRegistry;
import com.azure.azurecortex.example.skeleton.CortexSkeletonEntity;
import com.azure.azurecortex.example.spider.CortexSpiderEntity;
import com.azure.azurecortex.example.zombie.CortexZombieEntity;

@Mod(AzureCortex.MOD_ID)
public final class AzureCortexNeoForge {

    public static DeferredRegister<EntityType<?>> entityTypeDeferredRegister = DeferredRegister.create(
        BuiltInRegistries.ENTITY_TYPE,
        AzureCortex.MOD_ID
    );

    public static DeferredRegister<Item> itemDeferredRegister = DeferredRegister.create(
        BuiltInRegistries.ITEM,
        AzureCortex.MOD_ID
    );

    public AzureCortexNeoForge(IEventBus modEventBus) {
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
        event.put(ExampleRegistry.CORTEX_SPIDER.get(), CortexSpiderEntity.createAttributes().build());
    }

    public void addCreativeTabs(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ExampleRegistry.CORTEX_SKELETON_SPAWN_EGG.get());
            event.accept(ExampleRegistry.CORTEX_ZOMBIE_SPAWN_EGG.get());
            event.accept(ExampleRegistry.CORTEX_SPIDER_SPAWN_EGG.get());
        }
    }

    public void addSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
            ExampleRegistry.CORTEX_SKELETON.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            CortexSkeletonEntity::checkAnyLightMonsterSpawnRules,
            RegisterSpawnPlacementsEvent.Operation.AND
        );
        event.register(
            ExampleRegistry.CORTEX_ZOMBIE.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            CortexZombieEntity::checkAnyLightMonsterSpawnRules,
            RegisterSpawnPlacementsEvent.Operation.AND
        );
        event.register(
            ExampleRegistry.CORTEX_SPIDER.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            CortexSpiderEntity::checkAnyLightMonsterSpawnRules,
            RegisterSpawnPlacementsEvent.Operation.AND
        );
    }
}
