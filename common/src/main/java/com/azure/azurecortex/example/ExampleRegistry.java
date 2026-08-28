package com.azure.azurecortex.example;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import java.util.function.Supplier;

import com.azure.azurecortex.example.skeleton.CortexSkeletonEntity;
import com.azure.azurecortex.example.zombie.CortexZombieEntity;
import com.azure.azurecortex.services.ExampleServices;

public class ExampleRegistry {

    private ExampleRegistry() {}

    public static final Supplier<EntityType<CortexZombieEntity>> CORTEX_ZOMBIE = registerEntity(
        "cortex_zombie",
        CortexZombieEntity::new,
        1.95F
    );

    public static final Supplier<EntityType<CortexSkeletonEntity>> CORTEX_SKELETON = registerEntity(
        "cortex_skeleton",
        CortexSkeletonEntity::new,
        1.99F
    );

    public static final Supplier<SpawnEggItem> CORTEX_SKELETON_SPAWN_EGG = registerItem(
        "cortex_skeleton_spawn_egg",
        ExampleServices.COMMON_REGISTRY.makeSpawnEggFor(
            ExampleRegistry.CORTEX_SKELETON,
            12698049,
            4802889,
            new Item.Properties()
        )
    );

    public static final Supplier<SpawnEggItem> CORTEX_ZOMBIE_SPAWN_EGG = registerItem(
        "cortex_zombie_spawn_egg",
        ExampleServices.COMMON_REGISTRY.makeSpawnEggFor(
            ExampleRegistry.CORTEX_ZOMBIE,
            44975,
            7969893,
            new Item.Properties()
        )
    );

    static <T extends Entity> Supplier<EntityType<T>> registerEntity(
        String entityName,
        EntityType.EntityFactory<T> entity,
        float height
    ) {
        return ExampleServices.COMMON_REGISTRY.register(
            BuiltInRegistries.ENTITY_TYPE,
            entityName,
            () -> create(entity, height).buildWithoutDataFixerCheck()
        );
    }

    static <T extends Entity> SilencedEntityTypeBuilder create(
        EntityType.EntityFactory<T> entity,
        float height
    ) {
        return (SilencedEntityTypeBuilder) EntityType.Builder.of(entity, MobCategory.MONSTER)
            .sized((float) 0.6, height);
    }

    public static <T extends Item> Supplier<T> registerItem(String itemName, Supplier<T> item) {
        return ExampleServices.COMMON_REGISTRY.register(BuiltInRegistries.ITEM, itemName, item);
    }

    public static void initialize() {}
}
