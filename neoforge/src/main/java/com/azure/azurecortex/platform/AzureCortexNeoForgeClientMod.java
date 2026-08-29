package com.azure.azurecortex.platform;

import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import com.azure.azurecortex.AzureCortex;
import com.azure.azurecortex.example.ExampleRegistry;

@EventBusSubscriber(modid = AzureCortex.MOD_ID, value = Dist.CLIENT)
public class AzureCortexNeoForgeClientMod {

    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ExampleRegistry.CORTEX_SKELETON.get(), SkeletonRenderer::new);
        event.registerEntityRenderer(ExampleRegistry.CORTEX_ZOMBIE.get(), ZombieRenderer::new);
        event.registerEntityRenderer(ExampleRegistry.CORTEX_SPIDER.get(), SpiderRenderer::new);
    }
}
