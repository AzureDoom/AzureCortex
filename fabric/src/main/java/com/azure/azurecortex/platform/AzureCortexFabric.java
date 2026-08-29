package com.azure.azurecortex.platform;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;

import com.azure.azurecortex.AzureCortex;
import com.azure.azurecortex.example.ExampleRegistry;
import com.azure.azurecortex.example.skeleton.CortexSkeletonEntity;
import com.azure.azurecortex.example.spider.CortexSpiderEntity;
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
        FabricDefaultAttributeRegistry.register(
            ExampleRegistry.CORTEX_SPIDER.get(),
            CortexSpiderEntity.createAttributes()
        );
    }
}
