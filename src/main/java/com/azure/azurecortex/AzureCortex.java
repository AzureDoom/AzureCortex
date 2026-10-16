package com.azure.azurecortex;

import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

import com.azure.azurecortex.config.CortexConfig;

@SuppressWarnings("unused")
public final class AzureCortex {

    public static final String MOD_ID = "azurecortex";

    public static final String MOD_NAME = "AzureCortex";

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private AzureCortex() {}

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static void init(Path configDir) {
        CortexConfig.load(configDir);
        LOGGER.info(
            "AzureCortex framework initialized (debug diagnostics: {}, pathfinding debug: {})",
            CortexConfig.get().enableAiDiagnostics,
            CortexConfig.get().enablePathfindingDebug
        );
    }
}
