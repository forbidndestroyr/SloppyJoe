package com.sloppyjoe.world.gen.structure;

import com.sloppyjoe.SloppyJoeMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.structure.StructureType;

public class ModStructures {

    public static StructurePieceType AIRPORT_PIECE;
    public static StructurePieceType CRASH_SITE_PIECE;
    public static StructurePieceType SKYSCRAPER_PIECE;

    public static StructureType<AirportStructure> AIRPORT;
    public static StructureType<CrashSiteStructure> CRASH_SITE;
    public static StructureType<SkyscraperStructure> SKYSCRAPER;

    public static void initialize() {
        AIRPORT_PIECE    = Registry.register(Registries.STRUCTURE_PIECE,
                Identifier.of(SloppyJoeMod.MOD_ID, "airport"),    AirportPiece::fromNbt);
        CRASH_SITE_PIECE = Registry.register(Registries.STRUCTURE_PIECE,
                Identifier.of(SloppyJoeMod.MOD_ID, "crash_site"), CrashSitePiece::fromNbt);
        SKYSCRAPER_PIECE = Registry.register(Registries.STRUCTURE_PIECE,
                Identifier.of(SloppyJoeMod.MOD_ID, "skyscraper"), SkyscraperPiece::fromNbt);

        AIRPORT    = Registry.register(Registries.STRUCTURE_TYPE,
                Identifier.of(SloppyJoeMod.MOD_ID, "airport"),    () -> AirportStructure.CODEC);
        CRASH_SITE = Registry.register(Registries.STRUCTURE_TYPE,
                Identifier.of(SloppyJoeMod.MOD_ID, "crash_site"), () -> CrashSiteStructure.CODEC);
        SKYSCRAPER = Registry.register(Registries.STRUCTURE_TYPE,
                Identifier.of(SloppyJoeMod.MOD_ID, "skyscraper"), () -> SkyscraperStructure.CODEC);

        SloppyJoeMod.LOGGER.info("Registered SloppyJoe structures.");
    }
}
