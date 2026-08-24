package com.azure.azurecortex.platform;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import com.azure.azurecortex.AzureCortex;

public final class AzureCortexFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        AzureCortex.init(FabricLoader.getInstance().getConfigDir());
    }
}
