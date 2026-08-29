package com.azure.azurecortex.platform;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;

import com.azure.azurecortex.example.ExampleRegistry;

public class AzureCortexClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ExampleRegistry.CORTEX_SKELETON.get(), SkeletonRenderer::new);
        EntityRendererRegistry.register(ExampleRegistry.CORTEX_ZOMBIE.get(), ZombieRenderer::new);
        EntityRendererRegistry.register(ExampleRegistry.CORTEX_SPIDER.get(), SpiderRenderer::new);
    }
}
