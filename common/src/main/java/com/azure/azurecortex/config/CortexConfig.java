package com.azure.azurecortex.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Standalone, dependency-free configuration for the AzureCortex framework itself.
 * <p>
 * The file lives at {@code config/azurecortex.json} relative to the game's config directory. Call {@link #load(Path)}
 * once during mod initialization (each platform module's entrypoint is responsible for resolving the actual config
 * directory path and calling this) and use {@link #get()} thereafter.
 */
public final class CortexConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String FILE_NAME = "azurecortex.json";

    private static volatile CortexConfig instance = new CortexConfig();

    /** Enables particle-based visualization of pathfinding searches and node classifications. */
    public boolean enablePathfindingDebug = false;

    /** Enables periodic one-line diagnostic logging of each agent's current target/plan/action/path state. */
    public boolean enableAiDiagnostics = false;

    /** Enables spreading pathfinding search cost across ticks via incremental/phased sessions. */
    public boolean enableIncrementalPathfinding = true;

    /** Per-tick node-expansion budget used by incremental pathfinding sessions when enabled. */
    public int incrementalPathfindingNodeBudget = 300;

    /**
     * Returns the currently active configuration. Safe to call before {@link #load} — returns default values until a
     * config directory is loaded.
     */
    public static CortexConfig get() {
        return instance;
    }

    /**
     * Loads (or creates, with defaults, if absent) the config file at {@code configDir}/{@value #FILE_NAME}, replacing
     * {@link #get()}'s result with the loaded instance.
     * <p>
     * Each platform module (Fabric/NeoForge) is responsible for resolving its own config directory and calling this
     * once during startup, e.g.:
     *
     * <pre>{@code
     * CortexConfig.load(FabricLoader.getInstance().getConfigDir());
     * }</pre>
     *
     * @param configDir the game's config directory
     * @return the loaded (or freshly created) configuration, same as a subsequent {@link #get()}
     */
    public static synchronized CortexConfig load(Path configDir) {
        var path = configDir.resolve(FILE_NAME);

        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path)) {
                var loaded = GSON.fromJson(reader, CortexConfig.class);
                if (loaded != null) {
                    instance = loaded;
                    return instance;
                }
            } catch (IOException | com.google.gson.JsonParseException ignored) {
                // Fall through and (re)write a fresh default file below.
            }
        }

        instance = new CortexConfig();
        instance.save(configDir);
        return instance;
    }

    /**
     * Writes this configuration to {@code configDir}/{@value #FILE_NAME}, creating the directory if needed.
     *
     * @param configDir the game's config directory
     */
    public void save(Path configDir) {
        try {
            Files.createDirectories(configDir);
            try (var writer = Files.newBufferedWriter(configDir.resolve(FILE_NAME))) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
            // Best-effort: a failed save just means defaults/in-memory values are used for this session.
        }
    }
}
