package com.sloppyjoe.world.gen.structure;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.Optional;

public class AirportStructure extends Structure {

    public static final MapCodec<AirportStructure> CODEC =
            RecordCodecBuilder.mapCodec(i ->
                    i.group(configCodecBuilder(i)).apply(i, AirportStructure::new));

    public AirportStructure(Config config) {
        super(config);
    }

    @Override
    public Optional<StructurePosition> getStructurePosition(Context ctx) {
        int y = ctx.chunkGenerator().getHeightInGround(
                ctx.chunkPos().getCenterX(), ctx.chunkPos().getCenterZ(),
                Heightmap.Type.WORLD_SURFACE_WG, ctx.world(), ctx.noiseConfig());
        BlockPos origin = new BlockPos(ctx.chunkPos().getStartX(), y, ctx.chunkPos().getStartZ());
        return Optional.of(new StructurePosition(origin, collector ->
                collector.addPiece(new AirportPiece(ModStructures.AIRPORT_PIECE, origin))));
    }

    @Override
    public StructureType<?> getType() {
        return ModStructures.AIRPORT;
    }
}
