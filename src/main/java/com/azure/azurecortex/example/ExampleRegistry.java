package com.azure.azurecortex.example;

import net.minecraft.entity.Entity;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.azure.azurecortex.AzureCortex;
import com.azure.azurecortex.example.skeleton.CortexSkeletonEntity;
import com.azure.azurecortex.example.zombie.CortexZombieEntity;

/**
 * Registers the bundled example entities.
 * <p>
 * On 1.12.2 an entity's size lives in its constructor (both examples inherit vanilla's), its attributes come from
 * {@code applyEntityAttributes} (also inherited), and spawn eggs are vanilla's own {@code spawn_egg} carrying the
 * entity id in NBT — {@link EntityEntryBuilder#egg} registers them. They appear in the Miscellaneous tab like every
 * other spawn egg, and in {@code CortexExamplesTab} on the client.
 * <p>
 * This class is registered on the Forge event bus explicitly from {@code AzureCortexForge}'s constructor rather than
 * through {@code @Mod.EventBusSubscriber}, so registration doesn't depend on annotation scanning picking it up.
 */
public final class ExampleRegistry {

    private static final List<EntityEntry> ENTRIES = new ArrayList<>();

    private static int nextNetworkId = 0;

    static {
        ENTRIES.add(entry(CortexZombieEntity.class, "cortex_zombie", 44975, 7969893));
        ENTRIES.add(entry(CortexSkeletonEntity.class, "cortex_skeleton", 12698049, 4802889));
    }

    private ExampleRegistry() {}

    /** The example entity entries, in registration order. */
    public static List<EntityEntry> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        for (EntityEntry entry : ENTRIES) {
            event.getRegistry().register(entry);
        }
        AzureCortex.LOGGER.info("Registered {} AzureCortex example entities", ENTRIES.size());
    }

    private static <E extends Entity> EntityEntry entry(Class<E> type, String name, int primaryEgg, int secondaryEgg) {
        return EntityEntryBuilder.<E>create()
            .entity(type)
            .id(AzureCortex.id(name), nextNetworkId++)
            .name(AzureCortex.MOD_ID + "." + name)
            .tracker(80, 3, true)
            .egg(primaryEgg, secondaryEgg)
            .build();
    }
}
