package com.azure.azurecortex.example;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.SpawnEggItem;

import java.util.function.Supplier;

import com.azure.azurecortex.AzureCortex;
import com.azure.azurecortex.example.skeleton.CortexSkeletonEntity;
import com.azure.azurecortex.example.spider.CortexSpiderEntity;
import com.azure.azurecortex.example.zombie.CortexZombieEntity;
import com.azure.azurecortex.services.ExampleServices;

public class ExampleRegistry {

    private ExampleRegistry() {}

    public static final Supplier<EntityType<CortexZombieEntity>> CORTEX_ZOMBIE = registerEntity(
        "cortex_zombie",
        CortexZombieEntity::new,
        0.6F,
        1.95F
    );

    public static final Supplier<EntityType<CortexSkeletonEntity>> CORTEX_SKELETON = registerEntity(
        "cortex_skeleton",
        CortexSkeletonEntity::new,
        0.6F,
        1.99F
    );

    public static final Supplier<EntityType<CortexSpiderEntity>> CORTEX_SPIDER = registerEntity(
        "cortex_spider",
        CortexSpiderEntity::new,
        1.4F,
        0.9F
    );

    public static final Supplier<SpawnEggItem> CORTEX_SPIDER_SPAWN_EGG =
        ExampleServices.COMMON_REGISTRY.registerSpawnEgg(
            "cortex_spider_spawn_egg",
            CORTEX_SPIDER
        );

    public static final Supplier<SpawnEggItem> CORTEX_SKELETON_SPAWN_EGG =
        ExampleServices.COMMON_REGISTRY.registerSpawnEgg(
            "cortex_skeleton_spawn_egg",
            CORTEX_SKELETON
        );

    public static final Supplier<SpawnEggItem> CORTEX_ZOMBIE_SPAWN_EGG =
        ExampleServices.COMMON_REGISTRY.registerSpawnEgg(
            "cortex_zombie_spawn_egg",
            CORTEX_ZOMBIE
        );

    static <T extends Entity> SilencedEntityTypeBuilder create(
        EntityType.EntityFactory<T> entity,
        float width,
        float height
    ) {
        return (SilencedEntityTypeBuilder) EntityType.Builder.of(entity, MobCategory.MONSTER).sized(width, height);
    }

    static <T extends Entity> Supplier<EntityType<T>> registerEntity(
        String entityName,
        EntityType.EntityFactory<T> entity,
        float width,
        float height
    ) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(
            Registries.ENTITY_TYPE,
            AzureCortex.id(entityName)
        );
        return ExampleServices.COMMON_REGISTRY.register(
            BuiltInRegistries.ENTITY_TYPE,
            entityName,
            () -> create(entity, width, height).buildWithoutDataFixerCheck(key)
        );
    }

    public static void initialize() {}
}
