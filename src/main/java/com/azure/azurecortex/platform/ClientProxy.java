package com.azure.azurecortex.platform;

import net.minecraft.client.renderer.entity.RenderSkeleton;
import net.minecraft.client.renderer.entity.RenderZombie;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

import com.azure.azurecortex.example.CortexExamplesTab;
import com.azure.azurecortex.example.skeleton.CortexSkeletonEntity;
import com.azure.azurecortex.example.zombie.CortexZombieEntity;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(CortexZombieEntity.class, RenderZombie::new);
        RenderingRegistry.registerEntityRenderingHandler(CortexSkeletonEntity.class, RenderSkeleton::new);
        CortexExamplesTab.init();
    }
}
