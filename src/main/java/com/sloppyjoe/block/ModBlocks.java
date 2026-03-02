package com.sloppyjoe.block;

import com.sloppyjoe.SloppyJoeMod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class ModBlocks {

    // The workstation block that turns an unemployed villager into a Joe
    public static final Block JOE_COUNTER = register("joe_counter",
            new Block(AbstractBlock.Settings.create()
                    .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(SloppyJoeMod.MOD_ID, "joe_counter")))
                    .strength(2.5f)
                    .sounds(BlockSoundGroup.WOOD)));

    // Custom structure blocks
    public static final Block AIRPORT_TILE = register("airport_tile",
            new Block(AbstractBlock.Settings.create()
                    .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(SloppyJoeMod.MOD_ID, "airport_tile")))
                    .strength(1.5f)
                    .sounds(BlockSoundGroup.STONE)));

    public static final Block CORRUGATED_METAL = register("corrugated_metal",
            new Block(AbstractBlock.Settings.create()
                    .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(SloppyJoeMod.MOD_ID, "corrugated_metal")))
                    .strength(3.0f)
                    .sounds(BlockSoundGroup.METAL)));

    public static final Block MARBLE_TILE = register("marble_tile",
            new Block(AbstractBlock.Settings.create()
                    .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(SloppyJoeMod.MOD_ID, "marble_tile")))
                    .strength(1.5f)
                    .sounds(BlockSoundGroup.STONE)));

    private static Block register(String name, Block block) {
        Registry.register(Registries.BLOCK, Identifier.of(SloppyJoeMod.MOD_ID, name), block);
        Registry.register(Registries.ITEM, Identifier.of(SloppyJoeMod.MOD_ID, name),
                new BlockItem(block, new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(SloppyJoeMod.MOD_ID, name)))));
        return block;
    }

    public static void initialize() {
        SloppyJoeMod.LOGGER.info("Registering Sloppy Joe blocks...");
    }
}
