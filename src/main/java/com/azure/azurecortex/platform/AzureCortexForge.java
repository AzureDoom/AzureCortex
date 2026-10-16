package com.azure.azurecortex.platform;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.azure.azurecortex.AzureCortex;
import com.azure.azurecortex.example.ExampleRegistry;

@Mod(
    modid = AzureCortex.MOD_ID,
    name = AzureCortex.MOD_NAME,
    useMetadata = true,
    acceptedMinecraftVersions = "[1.12.2]"
)
public final class AzureCortexForge {

    @SidedProxy(
        clientSide = "com.azure.azurecortex.platform.ClientProxy",
        serverSide = "com.azure.azurecortex.platform.CommonProxy"
    )
    public static CommonProxy proxy;

    public AzureCortexForge() {
        MinecraftForge.EVENT_BUS.register(ExampleRegistry.class);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        AzureCortex.init(event.getModConfigurationDirectory().toPath());
        proxy.preInit();
    }
}
