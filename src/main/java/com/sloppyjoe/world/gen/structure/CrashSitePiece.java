package com.sloppyjoe.world.gen.structure;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

public class CrashSitePiece extends StructurePiece {

    private static final RegistryKey<LootTable> CRASH_LOOT = RegistryKey.of(
            RegistryKeys.LOOT_TABLE, Identifier.of("sloppyjoe", "chests/crash_site"));

    private final BlockPos origin;

    public CrashSitePiece(StructurePieceType type, BlockPos origin) {
        super(type, 0, new BlockBox(
                origin.getX() - 12, origin.getY() - 3, origin.getZ() - 12,
                origin.getX() + 12, origin.getY() + 5,  origin.getZ() + 12));
        this.origin = origin;
    }

    private CrashSitePiece(StructureContext ctx, NbtCompound nbt) {
        super(ModStructures.CRASH_SITE_PIECE, nbt);
        this.origin = BlockPos.fromLong(nbt.getLong("Origin"));
    }

    public static CrashSitePiece fromNbt(StructureContext ctx, NbtCompound nbt) {
        return new CrashSitePiece(ctx, nbt);
    }

    @Override
    protected void writeNbt(StructureContext ctx, NbtCompound nbt) {
        nbt.putLong("Origin", origin.asLong());
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structureAccessor,
                         ChunkGenerator chunkGenerator, Random random, BlockBox chunkBox,
                         ChunkPos chunkPos, BlockPos pivot) {
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();

        BlockState coarseDirt = Blocks.COARSE_DIRT.getDefaultState();
        BlockState gravel     = Blocks.GRAVEL.getDefaultState();
        BlockState air        = Blocks.AIR.getDefaultState();

        // ── Crater (5-block radius) ────────────────────────────────────────
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > 25) continue;
                int dist = (int) Math.sqrt(distSq);

                BlockPos base = new BlockPos(ox + dx, oy, oz + dz);
                BlockPos below = base.down();

                if (dist >= 4) {
                    // Outer rim: gravel
                    place(world, chunkBox, base, gravel);
                } else if (dist >= 2) {
                    // Mid-ring: coarse dirt
                    place(world, chunkBox, base, coarseDirt);
                    place(world, chunkBox, below, coarseDirt);
                } else {
                    // Inner pit: air over coarse dirt
                    place(world, chunkBox, base, air);
                    place(world, chunkBox, below, coarseDirt);
                    place(world, chunkBox, below.down(), coarseDirt);
                }
            }
        }

        // ── Scattered wreckage debris (8-block radius, avoids center 3) ────
        BlockState[] debris = {
                Blocks.COBBLESTONE.getDefaultState(),
                Blocks.DARK_OAK_PLANKS.getDefaultState(),
                Blocks.IRON_BARS.getDefaultState(),
                Blocks.GRAVEL.getDefaultState()
        };
        for (int i = 0; i < 20; i++) {
            int dx = random.nextInt(17) - 8;
            int dz = random.nextInt(17) - 8;
            int distSq = dx * dx + dz * dz;
            if (distSq > 64 || distSq < 9) continue;
            BlockState piece = debris[random.nextInt(debris.length)];
            place(world, chunkBox, new BlockPos(ox + dx, oy, oz + dz), piece);
            if (random.nextBoolean()) {
                place(world, chunkBox, new BlockPos(ox + dx, oy + 1, oz + dz), piece);
            }
        }

        // ── Fire at crater cardinal edges ──────────────────────────────────
        int[][] fireOffsets = {{4, 0}, {-4, 0}, {0, 4}, {0, -4}};
        for (int[] off : fireOffsets) {
            BlockPos fireBase = new BlockPos(ox + off[0], oy, oz + off[1]);
            place(world, chunkBox, fireBase, coarseDirt);
            place(world, chunkBox, fireBase.up(), Blocks.FIRE.getDefaultState());
        }

        // ── Skeleton skulls around crater rim ─────────────────────────────
        int[][] skullOffsets = {{5, 0}, {0, 5}, {-5, 0}};
        for (int[] off : skullOffsets) {
            BlockPos skullBase = new BlockPos(ox + off[0], oy, oz + off[1]);
            place(world, chunkBox, skullBase, coarseDirt);
            place(world, chunkBox, skullBase.up(), Blocks.SKELETON_SKULL.getDefaultState());
        }

        // ── Loot chest at crater centre ────────────────────────────────────
        BlockPos chestBase = new BlockPos(ox, oy - 1, oz);
        BlockPos chestPos  = new BlockPos(ox, oy,     oz);
        place(world, chunkBox, chestBase, coarseDirt);
        place(world, chunkBox, chestPos,
                Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH));
        if (chunkBox.contains(chestPos) && world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(CRASH_LOOT, random.nextLong());
        }
    }

    private static void place(StructureWorldAccess world, BlockBox chunkBox, BlockPos pos, BlockState state) {
        if (chunkBox.contains(pos)) {
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
        }
    }
}
