package com.azure.azurecortex;

import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

import com.azure.azurecortex.config.CortexConfig;
import com.azure.azurecortex.example.ExampleRegistry;

/**
 * Common (loader-agnostic) entry point for AzureCortex.
 * <p>
 * AzureCortex is a framework, not an end-user mod: it registers no blocks, items, or entities of its own. This class
 * exists so each platform module (Fabric/NeoForge) has one shared place to initialize framework-level state — right
 * now, just loading {@link CortexConfig} — regardless of which loader is running.
 */
@SuppressWarnings("unused")
public final class AzureCortex {

    public static final String MOD_ID = "azurecortex";

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private AzureCortex() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * Called once by each platform's entrypoint during mod initialization.
     *
     * @param configDir the game's config directory, as resolved by the calling platform module
     */
    public static void init(Path configDir) {
        CortexConfig.load(configDir);
        LOGGER.info(
            "AzureCortex framework initialized (debug diagnostics: {}, pathfinding debug: {})",
            CortexConfig.get().enableAiDiagnostics,
            CortexConfig.get().enablePathfindingDebug
        );
        ExampleRegistry.initialize();
    }
}
