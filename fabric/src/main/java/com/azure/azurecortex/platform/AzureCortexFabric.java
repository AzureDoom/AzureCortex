package com.azure.azurecortex.platform;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.levelgen.Heightmap;

import com.azure.azurecortex.AzureCortex;
import com.azure.azurecortex.example.ExampleRegistry;
import com.azure.azurecortex.example.skeleton.CortexSkeletonEntity;
import com.azure.azurecortex.example.zombie.CortexZombieEntity;

public final class AzureCortexFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        AzureCortex.init(FabricLoader.getInstance().getConfigDir());
        FabricDefaultAttributeRegistry.register(
            ExampleRegistry.CORTEX_ZOMBIE.get(),
            CortexZombieEntity.createAttributes()
        );
        FabricDefaultAttributeRegistry.register(
            ExampleRegistry.CORTEX_SKELETON.get(),
            CortexSkeletonEntity.createAttributes()
        );
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> {
            entries.accept(ExampleRegistry.CORTEX_SKELETON_SPAWN_EGG.get());
            entries.accept(ExampleRegistry.CORTEX_ZOMBIE_SPAWN_EGG.get());
        });
        SpawnPlacements.register(
            ExampleRegistry.CORTEX_SKELETON.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            CortexSkeletonEntity::checkAnyLightMonsterSpawnRules
        );
        SpawnPlacements.register(
            ExampleRegistry.CORTEX_ZOMBIE.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            CortexZombieEntity::checkAnyLightMonsterSpawnRules
        );
    }
}
