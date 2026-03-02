package com.sloppyjoe;

import com.sloppyjoe.block.ModBlocks;
import com.sloppyjoe.item.ModItems;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroup {

    public static final RegistryKey<ItemGroup> KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP,
            Identifier.of(SloppyJoeMod.MOD_ID, "main")
    );

    public static void initialize() {
        Registry.register(Registries.ITEM_GROUP, KEY,
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(ModItems.SLOPPY_JOE))
                        .displayName(Text.translatable("itemGroup.sloppyjoe.main"))
                        .entries((context, entries) -> {
                            // Food
                            entries.add(ModItems.SLOPPY_JOE);

                            // Perspective tool
                            entries.add(ModItems.PERSPECTIVE_LENS);

                            // Mob capture
                            entries.add(ModItems.GLASS_JAR);

                            // Horse Plane
                            entries.add(ModItems.PILOT_SADDLE);
                            entries.add(ModItems.HORSE_PLANE_TICKET);

                            // Pilot gear (cosmetic)
                            entries.add(ModItems.AVIATOR_HAT);
                            entries.add(ModItems.AVIATION_GOGGLES);
                            entries.add(ModItems.PILOT_HELMET);

                            // Workstation block
                            entries.add(ModBlocks.JOE_COUNTER);

                            // Structure / decorative blocks
                            entries.add(ModBlocks.AIRPORT_TILE);
                            entries.add(ModBlocks.CORRUGATED_METAL);
                            entries.add(ModBlocks.MARBLE_TILE);
                        })
                        .build()
        );
    }
}
