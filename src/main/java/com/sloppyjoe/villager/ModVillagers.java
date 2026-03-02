package com.sloppyjoe.villager;

import com.google.common.collect.ImmutableSet;
import com.sloppyjoe.SloppyJoeMod;
import com.sloppyjoe.block.ModBlocks;
import com.sloppyjoe.item.ModItems;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestType;

public class ModVillagers {

    public static final RegistryKey<PointOfInterestType> JOE_POI_KEY =
            RegistryKey.of(RegistryKeys.POINT_OF_INTEREST_TYPE,
                    Identifier.of(SloppyJoeMod.MOD_ID, "joe_counter"));

    public static final RegistryKey<VillagerProfession> JOE_KEY =
            RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION,
                    Identifier.of(SloppyJoeMod.MOD_ID, "joe"));

    private static void registerPointOfInterest() {
        PointOfInterestHelper.register(
                JOE_POI_KEY.getValue(),  // Identifier
                1,                        // maxFreeTickets: only one Joe per counter
                1,                        // searchDistance
                ModBlocks.JOE_COUNTER    // automatically extracts all block states
        );
    }

    private static VillagerProfession registerProfession() {
        return Registry.register(
                Registries.VILLAGER_PROFESSION,
                JOE_KEY.getValue(),
                new VillagerProfession(
                        "joe",
                        entry -> entry.matchesKey(JOE_POI_KEY),
                        entry -> entry.matchesKey(JOE_POI_KEY),
                        ImmutableSet.of(),  // gatherable items (none)
                        ImmutableSet.of(),  // acquirable blocks (none)
                        SoundEvents.ENTITY_VILLAGER_WORK_FISHERMAN
                )
        );
    }

    private static void registerTrades(VillagerProfession profession) {
        // Novice (level 1): 1 emerald → 3 Sloppy Joes, up to 16 times
        TradeOfferHelper.registerVillagerOffers(profession, 1, factories -> {
            factories.add((entity, random) -> new TradeOffer(
                    new TradedItem(Items.EMERALD, 1),
                    new ItemStack(ModItems.SLOPPY_JOE, 3),
                    16, 5, 0.05f
            ));
        });

        // Apprentice (level 2): 2 emeralds → 8 Sloppy Joes, up to 12 times
        TradeOfferHelper.registerVillagerOffers(profession, 2, factories -> {
            factories.add((entity, random) -> new TradeOffer(
                    new TradedItem(Items.EMERALD, 2),
                    new ItemStack(ModItems.SLOPPY_JOE, 8),
                    12, 10, 0.05f
            ));
        });
    }

    public static void initialize() {
        registerPointOfInterest();
        VillagerProfession profession = registerProfession();
        registerTrades(profession);
        SloppyJoeMod.LOGGER.info("Registering Joe villager...");
    }
}
