package com.sloppyjoe.entity;

import com.sloppyjoe.SloppyJoeMod;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import com.sloppyjoe.entity.DomainOrbEntity;
import com.sloppyjoe.entity.HollowPurpleEntity;

public class ModEntities {

    public static final RegistryKey<EntityType<?>> HORSE_PLANE_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(SloppyJoeMod.MOD_ID, "horse_plane"));

    public static final EntityType<HorsePlaneEntity> HORSE_PLANE = Registry.register(
            Registries.ENTITY_TYPE,
            HORSE_PLANE_KEY,
            EntityType.Builder.<HorsePlaneEntity>create(HorsePlaneEntity::new, SpawnGroup.CREATURE)
                    .dimensions(1.4f, 1.6f)
                    .build(HORSE_PLANE_KEY)
    );

    public static final RegistryKey<EntityType<?>> DOMAIN_ORB_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(SloppyJoeMod.MOD_ID, "domain_orb"));

    public static final EntityType<DomainOrbEntity> DOMAIN_ORB = Registry.register(
            Registries.ENTITY_TYPE,
            DOMAIN_ORB_KEY,
            EntityType.Builder.<DomainOrbEntity>create(DomainOrbEntity::new, SpawnGroup.MISC)
                    .dimensions(0.5f, 0.5f)
                    .build(DOMAIN_ORB_KEY)
    );

    public static final RegistryKey<EntityType<?>> HOLLOW_PURPLE_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(SloppyJoeMod.MOD_ID, "hollow_purple"));

    public static final EntityType<HollowPurpleEntity> HOLLOW_PURPLE = Registry.register(
            Registries.ENTITY_TYPE,
            HOLLOW_PURPLE_KEY,
            EntityType.Builder.<HollowPurpleEntity>create(HollowPurpleEntity::new, SpawnGroup.MISC)
                    .dimensions(1.0f, 1.0f)
                    .build(HOLLOW_PURPLE_KEY)
    );

    public static void initialize() {
        SloppyJoeMod.LOGGER.info("Registering Sloppy Joe entities...");
    }
}
