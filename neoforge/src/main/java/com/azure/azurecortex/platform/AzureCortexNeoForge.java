package com.azure.azurecortex.platform;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

import com.azure.azurecortex.AzureCortex;

@Mod(AzureCortex.MOD_ID)
public final class AzureCortexNeoForge {

    public AzureCortexNeoForge(IEventBus modEventBus) {
        AzureCortex.init(FMLPaths.CONFIGDIR.get());
    }
}
