package com.sloppyjoe.world.gen.structure;

import com.sloppyjoe.block.ModBlocks;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * Skyscraper structure piece — visual revamp.
 *
 * Exterior: quartz pillar structural columns + smooth quartz spandrel bands
 *           + light blue stained glass curtain panels → "blue glass office tower" look.
 * Lobby:    polished blackstone floor, smooth quartz walls, polished granite reception desk.
 * Offices:  stone slab floors, carpet by zone (blue → cyan → light-gray), desk clusters.
 * Roof:     white concrete pad, cyan concrete helipad border, yellow terracotta H,
 *           chiseled stone HVAC units, 3-tall end-rod antennae.
 */
public class SkyscraperPiece extends StructurePiece {

    private final BlockPos origin;

    public SkyscraperPiece(StructurePieceType type, BlockPos origin) {
        super(type, 0, new BlockBox(
                origin.getX() - 3, origin.getY() - 2, origin.getZ() - 3,
                origin.getX() + 23, origin.getY() + 80, origin.getZ() + 23));
        this.origin = origin;
    }

    private SkyscraperPiece(StructureContext ctx, NbtCompound nbt) {
        super(ModStructures.SKYSCRAPER_PIECE, nbt);
        this.origin = BlockPos.fromLong(nbt.getLong("Origin"));
    }

    public static SkyscraperPiece fromNbt(StructureContext ctx, NbtCompound nbt) {
        return new SkyscraperPiece(ctx, nbt);
    }

    @Override
    protected void writeNbt(StructureContext ctx, NbtCompound nbt) {
        nbt.putLong("Origin", origin.asLong());
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor sa,
                         ChunkGenerator cg, Random random, BlockBox chunkBox,
                         ChunkPos chunkPos, BlockPos pivot) {
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();

        // ── Palette ──────────────────────────────────────────────────────────
        BlockState air        = Blocks.AIR.getDefaultState();
        BlockState sQuartz    = Blocks.SMOOTH_QUARTZ.getDefaultState();
        BlockState qPillar    = Blocks.QUARTZ_PILLAR.getDefaultState();          // Y-axis default
        BlockState lbGlass    = Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState();
        BlockState marbleTile = ModBlocks.MARBLE_TILE.getDefaultState();
        BlockState polBlack   = Blocks.POLISHED_BLACKSTONE.getDefaultState();
        BlockState polAnd     = Blocks.POLISHED_ANDESITE.getDefaultState();
        BlockState polGranite = Blocks.POLISHED_GRANITE.getDefaultState();
        BlockState white      = Blocks.WHITE_CONCRETE.getDefaultState();
        BlockState cyanConc   = Blocks.CYAN_CONCRETE.getDefaultState();
        BlockState yellowTerra = Blocks.YELLOW_TERRACOTTA.getDefaultState();
        BlockState chiseled   = Blocks.CHISELED_STONE_BRICKS.getDefaultState();
        BlockState ironBars   = Blocks.IRON_BARS.getDefaultState();
        BlockState endRod     = Blocks.END_ROD.getDefaultState();
        BlockState shroom     = Blocks.SHROOMLIGHT.getDefaultState();
        BlockState seaLantern = Blocks.SEA_LANTERN.getDefaultState();
        BlockState bamboo     = Blocks.POTTED_BAMBOO.getDefaultState();
        BlockState whiteCarpet = Blocks.WHITE_CARPET.getDefaultState();

        BlockState sqSlab    = Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState ssSlab    = Blocks.SMOOTH_STONE_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState paSlabBot = Blocks.POLISHED_ANDESITE_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState pgSlabBot = Blocks.POLISHED_GRANITE_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM);

        BlockState oakChairN = Blocks.OAK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.NORTH).with(StairsBlock.HALF, BlockHalf.BOTTOM);
        BlockState oakChairS = Blocks.OAK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.SOUTH).with(StairsBlock.HALF, BlockHalf.BOTTOM);
        BlockState oakChairE = Blocks.OAK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.EAST).with(StairsBlock.HALF, BlockHalf.BOTTOM);
        BlockState oakChairW = Blocks.OAK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.WEST).with(StairsBlock.HALF, BlockHalf.BOTTOM);
        BlockState monitorN  = Blocks.ACACIA_TRAPDOOR.getDefaultState()
                .with(TrapdoorBlock.FACING, Direction.NORTH).with(TrapdoorBlock.OPEN, true)
                .with(TrapdoorBlock.HALF, BlockHalf.BOTTOM);

        // ── Foundation & footprint ───────────────────────────────────────────
        fill(world, chunkBox, ox - 1, oy - 2, oz - 1, ox + 20, oy - 1, oz + 20,
                Blocks.GRAVEL.getDefaultState());
        fill(world, chunkBox, ox - 1, oy, oz - 1, ox + 20, oy, oz + 20, sQuartz);

        // ── Curtain wall (oy+1..oy+71) ───────────────────────────────────────
        //
        //  Vertical structure: quartz pillar at columns 0, 5, 10, 15, 19
        //  Horizontal spandrel: smooth quartz band every 4 floors  (y-oy) % 4 == 0
        //  Glass infill: light blue stained glass everywhere else
        //
        for (int y = oy + 1; y <= oy + 71; y++) {
            boolean isSpandrel = ((y - oy) % 4 == 0);

            // North wall (z = oz) and South wall (z = oz+19)
            for (int x = ox; x <= ox + 19; x++) {
                boolean isPillar = (x == ox || x == ox + 5 || x == ox + 10 || x == ox + 15 || x == ox + 19);
                BlockState w;
                if      (isPillar)    w = qPillar;
                else if (isSpandrel)  w = sQuartz;
                else                  w = lbGlass;
                place(world, chunkBox, new BlockPos(x, y, oz),      w);
                place(world, chunkBox, new BlockPos(x, y, oz + 19), w);
            }
            // East wall (x = ox+19) and West wall (x = ox) — no corner re-placement
            for (int z = oz + 1; z <= oz + 18; z++) {
                boolean isPillar = (z == oz + 5 || z == oz + 10 || z == oz + 15);
                BlockState w;
                if      (isPillar)   w = qPillar;
                else if (isSpandrel) w = sQuartz;
                else                 w = lbGlass;
                place(world, chunkBox, new BlockPos(ox,      y, z), w);
                place(world, chunkBox, new BlockPos(ox + 19, y, z), w);
            }
        }

        // ── Roofline cap (oy+72) ─────────────────────────────────────────────
        fill(world, chunkBox, ox, oy + 72, oz, ox + 19, oy + 72, oz + 19, white);

        // ── Interior air (full building height) ──────────────────────────────
        fill(world, chunkBox, ox + 1, oy + 1, oz + 1, ox + 18, oy + 71, oz + 18, air);

        // ── Elevator shaft: smooth quartz frame + glass 2×2 core (full height) ─
        // Frame: smooth quartz 4×4 block at oy+1..oy+72
        for (int y = oy + 1; y <= oy + 72; y++) {
            // Outer frame of shaft (4×4)
            for (int dx = 0; dx <= 3; dx++) {
                place(world, chunkBox, new BlockPos(ox + 8 + dx, y, oz + 8),  sQuartz);
                place(world, chunkBox, new BlockPos(ox + 8 + dx, y, oz + 11), sQuartz);
            }
            place(world, chunkBox, new BlockPos(ox + 8,  y, oz + 9),  sQuartz);
            place(world, chunkBox, new BlockPos(ox + 8,  y, oz + 10), sQuartz);
            place(world, chunkBox, new BlockPos(ox + 11, y, oz + 9),  sQuartz);
            place(world, chunkBox, new BlockPos(ox + 11, y, oz + 10), sQuartz);
            // Glass inner 2×2 core
            place(world, chunkBox, new BlockPos(ox + 9,  y, oz + 9),  lbGlass);
            place(world, chunkBox, new BlockPos(ox + 10, y, oz + 9),  lbGlass);
            place(world, chunkBox, new BlockPos(ox + 9,  y, oz + 10), lbGlass);
            place(world, chunkBox, new BlockPos(ox + 10, y, oz + 10), lbGlass);
        }

        // ── LOBBY (oy..oy+8, double-height) ──────────────────────────────────

        // Floor: custom marble tile (dark base with white veins) for lobby centre,
        // polished blackstone border ring to frame it
        fill(world, chunkBox, ox + 1, oy, oz + 1, ox + 18, oy, oz + 18, marbleTile);
        // Polished blackstone border ring 1 block in from walls
        fill(world, chunkBox, ox + 1, oy, oz + 1, ox + 18, oy, oz + 1,  polBlack);
        fill(world, chunkBox, ox + 1, oy, oz + 18, ox + 18, oy, oz + 18, polBlack);
        fill(world, chunkBox, ox + 1, oy, oz + 2, ox + 1,  oy, oz + 17, polBlack);
        fill(world, chunkBox, ox + 18, oy, oz + 2, ox + 18, oy, oz + 17, polBlack);

        // South entrance: clear glass in south wall at oy+1..oy+7, x ox+7..ox+12
        for (int x = ox + 7; x <= ox + 12; x++)
            for (int y = oy + 1; y <= oy + 7; y++)
                place(world, chunkBox, new BlockPos(x, y, oz), air);

        // Lobby ceiling: smooth quartz with shroomlight feature grid
        fill(world, chunkBox, ox + 1, oy + 8, oz + 1, ox + 18, oy + 8, oz + 18, sQuartz);
        for (int x = ox + 4; x <= ox + 16; x += 4)
            for (int z = oz + 4; z <= oz + 16; z += 4)
                place(world, chunkBox, new BlockPos(x, oy + 8, z), shroom);

        // Polished granite reception desk (U-shape, opens south)
        // Slab surface: north edge + two sides
        fill(world, chunkBox, ox + 7, oy + 2, oz + 15, ox + 12, oy + 2, oz + 15, pgSlabBot); // north slab
        place(world, chunkBox, new BlockPos(ox + 7,  oy + 2, oz + 13), pgSlabBot); // west side
        place(world, chunkBox, new BlockPos(ox + 7,  oy + 2, oz + 14), pgSlabBot);
        place(world, chunkBox, new BlockPos(ox + 12, oy + 2, oz + 13), pgSlabBot); // east side
        place(world, chunkBox, new BlockPos(ox + 12, oy + 2, oz + 14), pgSlabBot);
        // Polished granite base below slabs
        fill(world, chunkBox, ox + 7, oy + 1, oz + 13, ox + 12, oy + 1, oz + 15, polGranite);
        // Monitors on back wall of desk (acacia trapdoor facing north, open)
        place(world, chunkBox, new BlockPos(ox + 9,  oy + 2, oz + 16), monitorN);
        place(world, chunkBox, new BlockPos(ox + 10, oy + 2, oz + 16), monitorN);
        // Receptionist chairs (oak stairs facing north) inside U
        place(world, chunkBox, new BlockPos(ox + 9,  oy + 2, oz + 14), oakChairN);
        place(world, chunkBox, new BlockPos(ox + 10, oy + 2, oz + 14), oakChairN);

        // 4 potted bamboo at lobby interior corners
        place(world, chunkBox, new BlockPos(ox + 3,  oy + 1, oz + 3),  bamboo);
        place(world, chunkBox, new BlockPos(ox + 16, oy + 1, oz + 3),  bamboo);
        place(world, chunkBox, new BlockPos(ox + 3,  oy + 1, oz + 16), bamboo);
        place(world, chunkBox, new BlockPos(ox + 16, oy + 1, oz + 16), bamboo);

        // Lobby stair run (east side, 4 steps rising south toward office area)
        for (int step = 0; step < 4; step++) {
            place(world, chunkBox, new BlockPos(ox + 16, oy + 1 + step, oz + 10 - step),
                    Blocks.QUARTZ_STAIRS.getDefaultState()
                            .with(StairsBlock.FACING, Direction.NORTH)
                            .with(StairsBlock.HALF, BlockHalf.BOTTOM));
        }

        // ── OFFICE FLOORS (oy+8, oy+12, oy+16 ... oy+68) ─────────────────────
        // 16 floors; carpet zone: oy+8..oy+24 = blue, oy+28..oy+44 = cyan, oy+48..oy+68 = light-gray
        for (int floorY = oy + 8; floorY <= oy + 68; floorY += 4) {
            boolean isConference = (floorY == oy + 28 || floorY == oy + 48);

            BlockState carpet;
            if      (floorY <= oy + 24) carpet = Blocks.BLUE_CARPET.getDefaultState();
            else if (floorY <= oy + 44) carpet = Blocks.CYAN_CARPET.getDefaultState();
            else                         carpet = Blocks.LIGHT_GRAY_CARPET.getDefaultState();

            // Stone slab floor
            fill(world, chunkBox, ox + 1, floorY, oz + 1, ox + 18, floorY, oz + 18, ssSlab);
            // Restore elevator shaft over the slab
            for (int dx = 0; dx <= 3; dx++) {
                place(world, chunkBox, new BlockPos(ox + 8 + dx, floorY, oz + 8),  sQuartz);
                place(world, chunkBox, new BlockPos(ox + 8 + dx, floorY, oz + 11), sQuartz);
            }
            place(world, chunkBox, new BlockPos(ox + 8,  floorY, oz + 9),  sQuartz);
            place(world, chunkBox, new BlockPos(ox + 8,  floorY, oz + 10), sQuartz);
            place(world, chunkBox, new BlockPos(ox + 11, floorY, oz + 9),  sQuartz);
            place(world, chunkBox, new BlockPos(ox + 11, floorY, oz + 10), sQuartz);
            place(world, chunkBox, new BlockPos(ox + 9,  floorY, oz + 9),  lbGlass);
            place(world, chunkBox, new BlockPos(ox + 10, floorY, oz + 9),  lbGlass);
            place(world, chunkBox, new BlockPos(ox + 9,  floorY, oz + 10), lbGlass);
            place(world, chunkBox, new BlockPos(ox + 10, floorY, oz + 10), lbGlass);

            // Clear interior air (3 blocks above slab)
            fill(world, chunkBox, ox + 1, floorY + 1, oz + 1, ox + 18, floorY + 3, oz + 18, air);

            // Carpet layer
            fill(world, chunkBox, ox + 1, floorY + 1, oz + 1, ox + 18, floorY + 1, oz + 18, carpet);
            // Clear aisle (Z: oz+8..oz+11) and elevator area
            fill(world, chunkBox, ox + 1,  floorY + 1, oz + 8,  ox + 18, floorY + 1, oz + 11, air);
            fill(world, chunkBox, ox + 8,  floorY + 1, oz + 8,  ox + 11, floorY + 1, oz + 11, air);

            // Shroomlight ceiling (top of floor air space)
            for (int x = ox + 4; x <= ox + 15; x += 5)
                for (int z = oz + 2; z <= oz + 7; z += 5)
                    place(world, chunkBox, new BlockPos(x, floorY + 3, z), shroom);

            if (!isConference) {
                // Zone A — West desks (X: ox+2, ox+4, ox+6)
                for (int di = 0; di < 3; di++) {
                    int dx = ox + 2 + di * 2;
                    place(world, chunkBox, new BlockPos(dx, floorY + 1, oz + 5), paSlabBot);
                    place(world, chunkBox, new BlockPos(dx, floorY + 2, oz + 4), monitorN);
                    place(world, chunkBox, new BlockPos(dx, floorY + 1, oz + 6), oakChairN);
                }
                // Zone B — East desks (X: ox+13, ox+15, ox+17)
                for (int di = 0; di < 3; di++) {
                    int dx = ox + 13 + di * 2;
                    place(world, chunkBox, new BlockPos(dx, floorY + 1, oz + 5), paSlabBot);
                    place(world, chunkBox, new BlockPos(dx, floorY + 2, oz + 4), monitorN);
                    place(world, chunkBox, new BlockPos(dx, floorY + 1, oz + 6), oakChairN);
                }
                // Oak stair chairs (south side of desk zones, facing desk)
                for (int di = 0; di < 3; di++) {
                    place(world, chunkBox, new BlockPos(ox + 2 + di * 2,  floorY + 1, oz + 7), oakChairS);
                    place(world, chunkBox, new BlockPos(ox + 13 + di * 2, floorY + 1, oz + 7), oakChairS);
                }
            } else {
                // Conference room: polished andesite slab table (3×6, centered)
                fill(world, chunkBox, ox + 7, floorY + 1, oz + 6, ox + 12, floorY + 1, oz + 12, paSlabBot);
                // Seats on all 4 edges
                for (int x = ox + 7; x <= ox + 12; x++) {
                    place(world, chunkBox, new BlockPos(x, floorY + 1, oz + 5),  oakChairS);
                    place(world, chunkBox, new BlockPos(x, floorY + 1, oz + 13), oakChairN);
                }
                for (int z = oz + 6; z <= oz + 12; z++) {
                    place(world, chunkBox, new BlockPos(ox + 6,  floorY + 1, z), oakChairE);
                    place(world, chunkBox, new BlockPos(ox + 13, floorY + 1, z), oakChairW);
                }
                fill(world, chunkBox, ox + 5, floorY + 1, oz + 4, ox + 14, floorY + 1, oz + 14, whiteCarpet);
                // Presentation screen — two observer blocks on north wall
                place(world, chunkBox, new BlockPos(ox + 9,  floorY + 2, oz + 1), Blocks.OBSERVER.getDefaultState());
                place(world, chunkBox, new BlockPos(ox + 10, floorY + 2, oz + 1), Blocks.OBSERVER.getDefaultState());
                // Sea lantern above table
                place(world, chunkBox, new BlockPos(ox + 9,  floorY + 3, oz + 9), seaLantern);
                place(world, chunkBox, new BlockPos(ox + 10, floorY + 3, oz + 9), seaLantern);
            }
        }

        // ── ROOFTOP (oy+72..oy+76) ───────────────────────────────────────────
        fill(world, chunkBox, ox, oy + 72, oz, ox + 19, oy + 72, oz + 19, white);

        // Iron bar railing perimeter
        for (int x = ox; x <= ox + 19; x++) {
            place(world, chunkBox, new BlockPos(x, oy + 73, oz),      ironBars);
            place(world, chunkBox, new BlockPos(x, oy + 73, oz + 19), ironBars);
        }
        for (int z = oz + 1; z <= oz + 18; z++) {
            place(world, chunkBox, new BlockPos(ox,      oy + 73, z), ironBars);
            place(world, chunkBox, new BlockPos(ox + 19, oy + 73, z), ironBars);
        }

        // Helipad (10×10 centred: ox+5..ox+14, oz+5..oz+14)
        fill(world, chunkBox, ox + 5, oy + 72, oz + 5, ox + 14, oy + 72, oz + 14, white);
        // Cyan concrete border strip 1 block outside pad
        for (int x = ox + 4; x <= ox + 15; x++) {
            place(world, chunkBox, new BlockPos(x, oy + 72, oz + 4),  cyanConc);
            place(world, chunkBox, new BlockPos(x, oy + 72, oz + 15), cyanConc);
        }
        for (int z = oz + 5; z <= oz + 14; z++) {
            place(world, chunkBox, new BlockPos(ox + 4,  oy + 72, z), cyanConc);
            place(world, chunkBox, new BlockPos(ox + 15, oy + 72, z), cyanConc);
        }
        // Yellow terracotta "H": left bar, right bar, crossbar
        fill(world, chunkBox, ox + 6,  oy + 72, oz + 6, ox + 7,  oy + 72, oz + 13, yellowTerra);
        fill(world, chunkBox, ox + 12, oy + 72, oz + 6, ox + 13, oy + 72, oz + 13, yellowTerra);
        fill(world, chunkBox, ox + 8,  oy + 72, oz + 9, ox + 11, oy + 72, oz + 10, yellowTerra);
        // Sea lanterns at helipad corners (landing indicators)
        place(world, chunkBox, new BlockPos(ox + 5,  oy + 72, oz + 5),  seaLantern);
        place(world, chunkBox, new BlockPos(ox + 14, oy + 72, oz + 5),  seaLantern);
        place(world, chunkBox, new BlockPos(ox + 5,  oy + 72, oz + 14), seaLantern);
        place(world, chunkBox, new BlockPos(ox + 14, oy + 72, oz + 14), seaLantern);

        // HVAC units: chiseled stone brick clusters
        fill(world, chunkBox, ox + 2, oy + 73, oz + 16, ox + 3, oy + 74, oz + 17, chiseled);
        fill(world, chunkBox, ox + 16, oy + 73, oz + 16, ox + 17, oy + 74, oz + 17, chiseled);

        // 3-tall end-rod antennae
        for (int h = 0; h < 3; h++) {
            place(world, chunkBox, new BlockPos(ox + 2,  oy + 73 + h, oz + 2),  endRod);
            place(world, chunkBox, new BlockPos(ox + 17, oy + 73 + h, oz + 2),  endRod);
            place(world, chunkBox, new BlockPos(ox + 10, oy + 73 + h, oz + 17), endRod);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static void place(StructureWorldAccess world, BlockBox cb, BlockPos pos, BlockState state) {
        if (cb.contains(pos)) world.setBlockState(pos, state, Block.NOTIFY_ALL);
    }

    private static void fill(StructureWorldAccess world, BlockBox cb,
                              int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    place(world, cb, new BlockPos(x, y, z), state);
    }
}
