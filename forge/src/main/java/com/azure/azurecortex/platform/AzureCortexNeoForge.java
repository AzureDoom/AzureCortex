package com.azure.azurecortex.platform;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import com.azure.azurecortex.AzureCortex;

@Mod(AzureCortex.MOD_ID)
public final class AzureCortexNeoForge {

    public AzureCortexNeoForge(FMLJavaModLoadingContext loadingContext) {
        AzureCortex.init(FMLPaths.CONFIGDIR.get());
    }
}
