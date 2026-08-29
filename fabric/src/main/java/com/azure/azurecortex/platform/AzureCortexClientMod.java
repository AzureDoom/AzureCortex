package com.azure.azurecortex.platform;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;

import com.azure.azurecortex.example.ExampleRegistry;

public class AzureCortexClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRenderers.register(ExampleRegistry.CORTEX_SKELETON.get(), SkeletonRenderer::new);
        EntityRenderers.register(ExampleRegistry.CORTEX_ZOMBIE.get(), ZombieRenderer::new);
        EntityRenderers.register(ExampleRegistry.CORTEX_SPIDER.get(), SpiderRenderer::new);
    }
}
