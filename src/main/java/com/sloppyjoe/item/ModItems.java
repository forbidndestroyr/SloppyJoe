package com.sloppyjoe.item;

import com.sloppyjoe.SloppyJoeMod;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModItems {

    // 8 hunger points, 0.8 saturation — a bit more filling than bread (5/0.6)
    public static final Item SLOPPY_JOE = register("sloppy_joe",
            new Item(new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(SloppyJoeMod.MOD_ID, "sloppy_joe")))
                    .food(new FoodComponent.Builder()
                            .nutrition(8)
                            .saturationModifier(0.8f)
                            .build())));

    public static final Item PERSPECTIVE_LENS = register("perspective_lens",
            new PerspectiveLensItem(new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(SloppyJoeMod.MOD_ID, "perspective_lens")))
                    .maxCount(1)));

    public static final Item GLASS_JAR = register("glass_jar",
            new GlassJarItem(new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(SloppyJoeMod.MOD_ID, "glass_jar")))
                    .maxCount(1)));

    public static final Item PILOT_SADDLE = register("pilot_saddle",
            new PilotSaddleItem(new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(SloppyJoeMod.MOD_ID, "pilot_saddle")))
                    .maxCount(1)));

    public static final Item AVIATOR_HAT = register("aviator_hat",
            new Item(new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(SloppyJoeMod.MOD_ID, "aviator_hat")))
                    .maxCount(1)));

    public static final Item AVIATION_GOGGLES = register("aviation_goggles",
            new Item(new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(SloppyJoeMod.MOD_ID, "aviation_goggles")))
                    .maxCount(1)));

    public static final Item PILOT_HELMET = register("pilot_helmet",
            new Item(new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(SloppyJoeMod.MOD_ID, "pilot_helmet")))
                    .maxCount(1)));

    public static final Item HORSE_PLANE_TICKET = register("horse_plane_ticket",
            new HorsePlaneTicketItem(new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(SloppyJoeMod.MOD_ID, "horse_plane_ticket")))
                    .maxCount(16)));

    public static final Item GOJO_BLINDFOLD = register("gojo_blindfold",
            new GojoBlindfoldItem(new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(SloppyJoeMod.MOD_ID, "gojo_blindfold")))
                    .maxCount(1)
                    .equippable(EquipmentSlot.HEAD)));

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(SloppyJoeMod.MOD_ID, name), item);
    }

    public static void initialize() {
        SloppyJoeMod.LOGGER.info("Registering Sloppy Joe items...");
    }
}
