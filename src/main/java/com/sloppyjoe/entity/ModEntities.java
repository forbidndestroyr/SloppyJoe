package com.sloppyjoe.entity;

import com.sloppyjoe.SloppyJoeMod;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

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

    public static void initialize() {
        SloppyJoeMod.LOGGER.info("Registering Sloppy Joe entities...");
    }
}
