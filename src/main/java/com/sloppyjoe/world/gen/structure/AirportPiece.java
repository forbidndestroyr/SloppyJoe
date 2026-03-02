package com.sloppyjoe.world.gen.structure;

import com.sloppyjoe.block.ModBlocks;
import com.sloppyjoe.entity.HorsePlaneEntity;
import com.sloppyjoe.entity.ModEntities;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
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

/**
 * Airport structure piece — visual revamp.
 *
 * Block-palette philosophy:
 *   Public terminal  → smooth quartz, quartz pillar, cyan stained glass, polished diorite floors
 *   Industrial zones → black concrete columns, gray concrete fill, iron block roof, dark oak planks
 *   Control tower    → polished blackstone base, smooth quartz shaft, cyan glass observation deck
 *   Airside ground   → gray/light-gray concrete, yellow/white concrete markings
 */
public class AirportPiece extends StructurePiece {

    private static final RegistryKey<LootTable> AIRPORT_LOOT = RegistryKey.of(
            RegistryKeys.LOOT_TABLE, Identifier.of("sloppyjoe", "chests/airport_hangar"));
    private static final RegistryKey<LootTable> CRASH_LOOT = RegistryKey.of(
            RegistryKeys.LOOT_TABLE, Identifier.of("sloppyjoe", "chests/crash_site"));

    private final BlockPos origin;

    public AirportPiece(StructurePieceType type, BlockPos origin) {
        super(type, 0, new BlockBox(
                origin.getX() - 10, origin.getY() - 5, origin.getZ() - 30,
                origin.getX() + 115, origin.getY() + 50, origin.getZ() + 220));
        this.origin = origin;
    }

    private AirportPiece(StructureContext ctx, NbtCompound nbt) {
        super(ModStructures.AIRPORT_PIECE, nbt);
        this.origin = BlockPos.fromLong(nbt.getLong("Origin"));
    }

    public static AirportPiece fromNbt(StructureContext ctx, NbtCompound nbt) {
        return new AirportPiece(ctx, nbt);
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

        // Ground tarmac + sub-base gravel + terrain clear
        fill(world, chunkBox, ox - 5, oy,     oz - 25, ox + 105, oy,     oz + 215,
                Blocks.GRAY_CONCRETE.getDefaultState());
        fill(world, chunkBox, ox - 5, oy - 4, oz - 25, ox + 105, oy - 1, oz + 215,
                Blocks.GRAVEL.getDefaultState());
        fill(world, chunkBox, ox - 5, oy + 1, oz - 25, ox + 105, oy + 45, oz + 215,
                Blocks.AIR.getDefaultState());

        buildFence(world, chunkBox, ox, oy, oz);
        buildParkingLot(world, chunkBox, ox, oy, oz);
        buildTerminal(world, chunkBox, ox, oy, oz, random);
        buildControlTower(world, chunkBox, ox, oy, oz);
        buildHangar1(world, chunkBox, ox, oy, oz, random);
        buildHangar2(world, chunkBox, ox, oy, oz, random);
        buildApron(world, chunkBox, ox, oy, oz);
        buildTaxiway(world, chunkBox, ox, oy, oz);
        buildRunway(world, chunkBox, ox, oy, oz);
        buildWindsock(world, chunkBox, ox, oy, oz);

        // HorsePlane spawn inside Hangar 1
        BlockPos planeCheck = new BlockPos(ox + 12, oy + 1, oz + 47);
        if (chunkBox.contains(planeCheck)) {
            ServerWorld sw = world.toServerWorld();
            HorsePlaneEntity plane = new HorsePlaneEntity(ModEntities.HORSE_PLANE, sw);
            plane.refreshPositionAndAngles(ox + 12.5, oy + 1, oz + 47.5, 0f, 0f);
            plane.makeTame();
            sw.spawnEntityAndPassengers(plane);
        }
    }

    // =========================================================================
    // PERIMETER FENCE
    // =========================================================================
    private static void buildFence(StructureWorldAccess world, BlockBox cb, int ox, int oy, int oz) {
        BlockState bars  = Blocks.IRON_BARS.getDefaultState();
        BlockState black = Blocks.BLACK_CONCRETE.getDefaultState();

        // South fence — gap at parking entrance (ox+35..ox+44)
        for (int x = ox - 6; x <= ox + 106; x++) {
            if (x < ox + 35 || x > ox + 44) place(world, cb, new BlockPos(x, oy + 1, oz - 26), bars);
            place(world, cb, new BlockPos(x, oy + 1, oz + 216), bars);
        }
        // East/West fences — gap on west side for vehicle gate (oz+50..oz+54)
        for (int z = oz - 25; z <= oz + 215; z++) {
            if (z < oz + 50 || z > oz + 54) place(world, cb, new BlockPos(ox - 6, oy + 1, z), bars);
            place(world, cb, new BlockPos(ox + 106, oy + 1, z), bars);
        }
        // Black concrete posts (3 tall) every 8 blocks
        for (int x = ox - 6; x <= ox + 106; x += 8) {
            for (int h = 1; h <= 3; h++) {
                place(world, cb, new BlockPos(x, oy + h, oz - 26), black);
                place(world, cb, new BlockPos(x, oy + h, oz + 216), black);
            }
        }
        for (int z = oz - 25; z <= oz + 215; z += 8) {
            for (int h = 1; h <= 3; h++) {
                place(world, cb, new BlockPos(ox - 6,  oy + h, z), black);
                place(world, cb, new BlockPos(ox + 106, oy + h, z), black);
            }
        }
    }

    // =========================================================================
    // PARKING LOT  (Z: oz-22..oz-1, X: ox..ox+79)
    // Light gray surface, white bay lines, black drive lanes, end-rod lights
    // =========================================================================
    private static void buildParkingLot(StructureWorldAccess world, BlockBox cb, int ox, int oy, int oz) {
        BlockState lgConc = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
        BlockState white  = Blocks.WHITE_CONCRETE.getDefaultState();
        BlockState black  = Blocks.BLACK_CONCRETE.getDefaultState();
        BlockState endRod = Blocks.END_ROD.getDefaultState();

        fill(world, cb, ox, oy, oz - 22, ox + 79, oy, oz - 1, lgConc);
        for (int x = ox; x <= ox + 79; x += 5)
            fill(world, cb, x, oy, oz - 22, x, oy, oz - 1, white);
        // Two-way drive lane
        fill(world, cb, ox, oy, oz - 14, ox + 79, oy, oz - 13, black);
        fill(world, cb, ox, oy, oz - 10, ox + 79, oy, oz - 9,  black);
        for (int lx = ox + 10; lx <= ox + 79; lx += 20) {
            place(world, cb, new BlockPos(lx, oy,     oz - 12), black);
            place(world, cb, new BlockPos(lx, oy + 1, oz - 12), endRod);
        }
    }

    // =========================================================================
    // TERMINAL  (X: ox..ox+79, Z: oz..oz+22, H: 14)
    //
    // Ground floor (oy..oy+6): polished diorite floor, shroomlight ceiling
    // Upper floor  (oy+7..oy+13): cyan carpet, light-blue panoramic glass
    // =========================================================================
    private static void buildTerminal(StructureWorldAccess world, BlockBox cb,
                                      int ox, int oy, int oz, Random random) {
        // ── Palette ──────────────────────────────────────────────────────────
        BlockState air         = Blocks.AIR.getDefaultState();
        BlockState sQuartz     = Blocks.SMOOTH_QUARTZ.getDefaultState();
        BlockState qPillar     = Blocks.QUARTZ_PILLAR.getDefaultState();        // Y-axis default
        BlockState airportTile = ModBlocks.AIRPORT_TILE.getDefaultState();
        BlockState polDiorite  = Blocks.POLISHED_DIORITE.getDefaultState();
        BlockState polAndesite = Blocks.POLISHED_ANDESITE.getDefaultState();
        BlockState polGranite  = Blocks.POLISHED_GRANITE.getDefaultState();
        BlockState polBlack    = Blocks.POLISHED_BLACKSTONE.getDefaultState();
        BlockState cyanGlass   = Blocks.CYAN_STAINED_GLASS.getDefaultState();
        BlockState lbGlass     = Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState();
        BlockState gGlassPn    = Blocks.GRAY_STAINED_GLASS_PANE.getDefaultState();
        BlockState shroom      = Blocks.SHROOMLIGHT.getDefaultState();
        BlockState lantern     = Blocks.LANTERN.getDefaultState();
        BlockState hangLant    = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
        BlockState chain       = Blocks.CHAIN.getDefaultState();
        BlockState ironBars    = Blocks.IRON_BARS.getDefaultState();
        BlockState cyanCarpet  = Blocks.CYAN_CARPET.getDefaultState();
        BlockState blackWool   = Blocks.BLACK_WOOL.getDefaultState();
        BlockState blackConc   = Blocks.BLACK_CONCRETE.getDefaultState();
        BlockState oakPlanks   = Blocks.OAK_PLANKS.getDefaultState();
        BlockState bamboo      = Blocks.POTTED_BAMBOO.getDefaultState();
        BlockState smoker      = Blocks.SMOKER.getDefaultState();
        BlockState craftTable  = Blocks.CRAFTING_TABLE.getDefaultState();

        BlockState granSlabBot = Blocks.POLISHED_GRANITE_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState andSlabBot  = Blocks.POLISHED_ANDESITE_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState sqSlab      = Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState ssSlab      = Blocks.SMOOTH_STONE_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM);

        BlockState oakChairN  = Blocks.OAK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.NORTH).with(StairsBlock.HALF, BlockHalf.BOTTOM);
        BlockState oakChairS  = Blocks.OAK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.SOUTH).with(StairsBlock.HALF, BlockHalf.BOTTOM);
        BlockState oakChairW  = Blocks.OAK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.WEST).with(StairsBlock.HALF, BlockHalf.BOTTOM);
        BlockState qStairN    = Blocks.QUARTZ_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.NORTH).with(StairsBlock.HALF, BlockHalf.BOTTOM);
        BlockState monitorS   = Blocks.OAK_TRAPDOOR.getDefaultState()
                .with(TrapdoorBlock.FACING, Direction.SOUTH).with(TrapdoorBlock.OPEN, true)
                .with(TrapdoorBlock.HALF, BlockHalf.BOTTOM);
        BlockState ironTrap   = Blocks.IRON_TRAPDOOR.getDefaultState()
                .with(TrapdoorBlock.FACING, Direction.NORTH).with(TrapdoorBlock.OPEN, true)
                .with(TrapdoorBlock.HALF, BlockHalf.BOTTOM);

        // ── Exterior shell ───────────────────────────────────────────────────
        // Side walls, north wall (lower solid, upper panoramic glass), roof
        fill(world, cb, ox,      oy, oz,      ox,      oy + 13, oz + 22, sQuartz);   // west
        fill(world, cb, ox + 79, oy, oz,      ox + 79, oy + 13, oz + 22, sQuartz);   // east
        fill(world, cb, ox,      oy, oz + 22, ox + 79, oy + 7,  oz + 22, sQuartz);   // north lower
        fill(world, cb, ox,      oy + 8, oz + 22, ox + 79, oy + 12, oz + 22, lbGlass); // north upper glass
        fill(world, cb, ox,      oy + 13, oz + 22, ox + 79, oy + 13, oz + 22, sQuartz); // north header
        fill(world, cb, ox,      oy + 13, oz,      ox + 79, oy + 13, oz + 22, sQuartz); // roof

        // Shroomlights embedded in roof (warm airport ambience seen from outside)
        for (int x = ox + 8; x < ox + 79; x += 10)
            for (int z = oz + 5; z < oz + 22; z += 6)
                place(world, cb, new BlockPos(x, oy + 13, z), shroom);

        // South facade: polished andesite base sill, quartz pillar columns, cyan glass panels
        fill(world, cb, ox, oy,      oz, ox + 79, oy,      oz, polAndesite);    // base sill
        fill(world, cb, ox, oy + 1,  oz, ox + 79, oy + 11, oz, cyanGlass);      // glass field
        fill(world, cb, ox, oy + 12, oz, ox + 79, oy + 12, oz, sQuartz);        // header band
        // Quartz pillar columns over the glass (every 8 blocks)
        int[] pillars = {ox, ox+8, ox+16, ox+24, ox+32, ox+40, ox+48, ox+56, ox+64, ox+72, ox+79};
        for (int px : pillars)
            fill(world, cb, px, oy + 1, oz, px, oy + 11, oz, qPillar);
        // Main entrance cutout (air gap oy+1..oy+4)
        fill(world, cb, ox + 37, oy + 1, oz, ox + 42, oy + 4, oz, air);

        // North wall gate openings at upper floor level (4 gate zones)
        for (int zone = 0; zone < 4; zone++) {
            int gx = ox + 8 + zone * 19;
            fill(world, cb, gx, oy + 8, oz + 22, gx + 3, oy + 11, oz + 22, air);
        }

        // Interior air clear
        fill(world, cb, ox + 1, oy + 1, oz + 1, ox + 78, oy + 12, oz + 21, air);

        // ── Ground floor (oy..oy+6) ──────────────────────────────────────────

        // Floor: custom airport tile (has built-in grout grid texture)
        fill(world, cb, ox + 1, oy, oz + 1, ox + 78, oy, oz + 21, airportTile);
        // Cyan carpet wayfinding strip along center corridor
        fill(world, cb, ox + 1, oy, oz + 10, ox + 78, oy, oz + 11, cyanCarpet);

        // Shroomlight ceiling (top of ground floor air at oy+6)
        for (int x = ox + 8; x <= ox + 72; x += 12)
            for (int z = oz + 3; z <= oz + 20; z += 8)
                place(world, cb, new BlockPos(x, oy + 6, z), shroom);

        // Check-in counter row (Z: oz+13..oz+15)
        // Polished blackstone back wall
        fill(world, cb, ox + 1, oy + 1, oz + 16, ox + 78, oy + 3, oz + 16, polBlack);
        // Queue barrier posts at Z=oz+12
        for (int x = ox + 4; x <= ox + 76; x += 8)
            place(world, cb, new BlockPos(x, oy + 1, oz + 12), ironBars);
        // 9 check-in desks with polished granite counters
        for (int i = 0; i < 9; i++) {
            int dx = ox + 4 + i * 8;
            place(world, cb, new BlockPos(dx - 1, oy + 1, oz + 14), granSlabBot);
            place(world, cb, new BlockPos(dx,     oy + 1, oz + 14), granSlabBot);
            place(world, cb, new BlockPos(dx + 1, oy + 1, oz + 14), granSlabBot);
            place(world, cb, new BlockPos(dx,     oy + 2, oz + 15), monitorS);
            place(world, cb, new BlockPos(dx,     oy + 1, oz + 13), oakChairN);
            // Cyan carpet mat at desk feet
            place(world, cb, new BlockPos(dx - 1, oy, oz + 13), cyanCarpet);
            place(world, cb, new BlockPos(dx,     oy, oz + 13), cyanCarpet);
            place(world, cb, new BlockPos(dx + 1, oy, oz + 13), cyanCarpet);
        }

        // Security checkpoint (Z: oz+17..oz+20) — 3 lanes
        for (int lane = 0; lane < 3; lane++) {
            int lx = ox + 12 + lane * 20;
            fill(world, cb, lx, oy, oz + 17, lx + 2, oy, oz + 20, blackConc);
            fill(world, cb, lx - 1, oy + 1, oz + 17, lx - 1, oy + 2, oz + 20, ironBars);
            fill(world, cb, lx + 3, oy + 1, oz + 17, lx + 3, oy + 2, oz + 20, ironBars);
            // Polished blackstone arch
            place(world, cb, new BlockPos(lx - 1, oy + 3, oz + 18), polBlack);
            place(world, cb, new BlockPos(lx + 3, oy + 3, oz + 18), polBlack);
            for (int x = lx; x <= lx + 2; x++)
                place(world, cb, new BlockPos(x, oy + 3, oz + 18), polBlack);
            place(world, cb, new BlockPos(lx, oy + 1, oz + 17), ironTrap);
        }

        // West seating area (X: ox+4..ox+30, Z: oz+3..oz+11)
        for (int sx = ox + 4; sx <= ox + 30; sx += 3) {
            for (int sz = oz + 3; sz <= oz + 11; sz += 2)
                place(world, cb, new BlockPos(sx, oy + 1, sz), oakChairW);
            place(world, cb, new BlockPos(sx, oy + 5, oz + 7), chain);
            place(world, cb, new BlockPos(sx, oy + 4, oz + 7), hangLant);
        }

        // East shops (X: ox+55..ox+78) — oak plank floor, polished granite counters
        fill(world, cb, ox + 55, oy, oz + 3, ox + 78, oy, oz + 12, oakPlanks);
        fill(world, cb, ox + 66, oy + 1, oz + 3, ox + 66, oy + 2, oz + 12, polGranite); // divider
        // Duty Free counter
        fill(world, cb, ox + 55, oy + 1, oz + 11, ox + 65, oy + 1, oz + 11, granSlabBot);
        BlockPos dtChest = new BlockPos(ox + 60, oy + 1, oz + 12);
        place(world, cb, dtChest, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH));
        if (cb.contains(dtChest) && world.getBlockEntity(dtChest) instanceof ChestBlockEntity che)
            che.setLootTable(AIRPORT_LOOT, random.nextLong());
        place(world, cb, new BlockPos(ox + 59, oy + 2, oz + 10), lantern);
        place(world, cb, new BlockPos(ox + 56, oy + 1, oz + 3), bamboo);
        place(world, cb, new BlockPos(ox + 64, oy + 1, oz + 3), bamboo);
        // Food Court counter
        fill(world, cb, ox + 67, oy + 1, oz + 11, ox + 78, oy + 1, oz + 11, granSlabBot);
        place(world, cb, new BlockPos(ox + 71, oy + 1, oz + 12), smoker);
        place(world, cb, new BlockPos(ox + 73, oy + 1, oz + 12), craftTable);
        place(world, cb, new BlockPos(ox + 71, oy + 2, oz + 10), lantern);
        place(world, cb, new BlockPos(ox + 67, oy + 1, oz + 3), bamboo);
        place(world, cb, new BlockPos(ox + 77, oy + 1, oz + 3), bamboo);

        // Stairwell — quartz stairs rising north, iron bar rails
        for (int step = 0; step < 5; step++) {
            int sy = oy + 1 + step;
            int sz = oz + 5 - step;
            place(world, cb, new BlockPos(ox + 38, sy, sz), qStairN);
            place(world, cb, new BlockPos(ox + 39, sy, sz), qStairN);
            place(world, cb, new BlockPos(ox + 37, sy + 1, sz), ironBars);
            place(world, cb, new BlockPos(ox + 41, sy + 1, sz), ironBars);
        }
        for (int ly = oy + 6; ly <= oy + 8; ly++)
            place(world, cb, new BlockPos(ox + 38, ly, oz + 1),
                    Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.SOUTH));

        // ── Upper floor (oy+7 slab, oy+8..oy+12 interior) ───────────────────
        fill(world, cb, ox + 1, oy + 7, oz + 1, ox + 78, oy + 7, oz + 21, ssSlab);
        fill(world, cb, ox + 1, oy + 8, oz + 1, ox + 78, oy + 12, oz + 21, air);
        fill(world, cb, ox + 1, oy + 8, oz + 1, ox + 78, oy + 8, oz + 21, cyanCarpet);
        // Shroomlights in ceiling
        for (int x = ox + 8; x <= ox + 72; x += 12)
            for (int z = oz + 3; z <= oz + 20; z += 8)
                place(world, cb, new BlockPos(x, oy + 12, z), shroom);

        // Gate lounge zones A1-A4 (4 × 19 blocks wide)
        for (int zone = 0; zone < 4; zone++) {
            int gx = ox + 1 + zone * 19;
            fill(world, cb, gx + 1, oy + 8, oz + 3, gx + 17, oy + 8, oz + 3, blackWool); // queue line
            for (int sx = gx + 2; sx <= gx + 16; sx += 3)
                for (int sz = oz + 7; sz <= oz + 14; sz += 2)
                    place(world, cb, new BlockPos(sx, oy + 9, sz), oakChairN); // face north toward apron
            // Polished andesite zone divider pillar
            if (zone < 3) {
                for (int py = oy + 9; py <= oy + 11; py++)
                    place(world, cb, new BlockPos(gx + 18, py, oz + 5), polAndesite);
            }
        }

        // ── Jet bridges — smooth quartz tunnels from gate openings to apron ──
        for (int zone = 0; zone < 4; zone++) {
            int gx = ox + 8 + zone * 19;
            for (int jz = oz + 23; jz <= oz + 27; jz++) {
                // Floor
                for (int x = gx; x <= gx + 3; x++)
                    place(world, cb, new BlockPos(x, oy + 8, jz), sQuartz);
                // Side glass pane walls
                place(world, cb, new BlockPos(gx,     oy + 9,  jz), gGlassPn);
                place(world, cb, new BlockPos(gx + 3, oy + 9,  jz), gGlassPn);
                place(world, cb, new BlockPos(gx,     oy + 10, jz), gGlassPn);
                place(world, cb, new BlockPos(gx + 3, oy + 10, jz), gGlassPn);
                // Roof
                for (int x = gx; x <= gx + 3; x++)
                    place(world, cb, new BlockPos(x, oy + 11, jz), sQuartz);
            }
        }
    }

    // =========================================================================
    // CONTROL TOWER
    // Polished blackstone base → smooth quartz shaft (black concrete corners)
    // → cyan stained glass observation deck
    // =========================================================================
    private static void buildControlTower(StructureWorldAccess world, BlockBox cb, int ox, int oy, int oz) {
        BlockState air        = Blocks.AIR.getDefaultState();
        BlockState sQuartz    = Blocks.SMOOTH_QUARTZ.getDefaultState();
        BlockState polBlack   = Blocks.POLISHED_BLACKSTONE.getDefaultState();
        BlockState polAnd     = Blocks.POLISHED_ANDESITE.getDefaultState();
        BlockState blackConc  = Blocks.BLACK_CONCRETE.getDefaultState();
        BlockState cyanGlass  = Blocks.CYAN_STAINED_GLASS.getDefaultState();
        BlockState glassPn    = Blocks.GLASS_PANE.getDefaultState();
        BlockState shroom     = Blocks.SHROOMLIGHT.getDefaultState();
        BlockState seaLantern = Blocks.SEA_LANTERN.getDefaultState();
        BlockState endRod     = Blocks.END_ROD.getDefaultState();
        BlockState ironBars   = Blocks.IRON_BARS.getDefaultState();
        BlockState observer   = Blocks.OBSERVER.getDefaultState();
        BlockState ladder     = Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.NORTH);

        int tx = ox + 85;
        int tz = oz + 2;

        // Base (9×9, oy..oy+5): polished blackstone heavy plinth + smooth quartz cap
        fill(world, cb, tx, oy,     tz, tx + 8, oy + 3, tz + 8, polBlack);
        fill(world, cb, tx, oy + 4, tz, tx + 8, oy + 4, tz + 8, blackConc);
        fill(world, cb, tx, oy + 5, tz, tx + 8, oy + 5, tz + 8, sQuartz);
        fill(world, cb, tx + 1, oy + 1, tz + 1, tx + 7, oy + 4, tz + 7, air);
        fill(world, cb, tx + 1, oy, tz + 1, tx + 7, oy, tz + 7, polAnd);  // interior floor

        // Shaft (5×5 exterior, 3×3 interior air, oy+6..oy+34)
        // Smooth quartz faces with black concrete corner columns
        fill(world, cb, tx + 2, oy + 6, tz + 2, tx + 6, oy + 34, tz + 6, sQuartz);
        fill(world, cb, tx + 3, oy + 6, tz + 3, tx + 5, oy + 34, tz + 5, air);
        fill(world, cb, tx + 2, oy + 6, tz + 2, tx + 2, oy + 34, tz + 2, blackConc);
        fill(world, cb, tx + 6, oy + 6, tz + 2, tx + 6, oy + 34, tz + 2, blackConc);
        fill(world, cb, tx + 2, oy + 6, tz + 6, tx + 2, oy + 34, tz + 6, blackConc);
        fill(world, cb, tx + 6, oy + 6, tz + 6, tx + 6, oy + 34, tz + 6, blackConc);
        // Interior ladder
        for (int ly = oy + 6; ly <= oy + 34; ly++)
            place(world, cb, new BlockPos(tx + 4, ly, tz + 5), ladder);
        // Slit windows every 6 blocks of shaft height (glass pane)
        for (int ly = oy + 9; ly <= oy + 33; ly += 6) {
            place(world, cb, new BlockPos(tx + 4, ly, tz + 2), glassPn);
            place(world, cb, new BlockPos(tx + 2, ly, tz + 4), glassPn);
        }

        // Observation deck (9×9×4, oy+35..oy+39)
        fill(world, cb, tx, oy + 35, tz, tx + 8, oy + 35, tz + 8, polAnd);   // floor
        fill(world, cb, tx, oy + 39, tz, tx + 8, oy + 39, tz + 8, sQuartz);  // ceiling
        fill(world, cb, tx + 1, oy + 36, tz + 1, tx + 7, oy + 38, tz + 7, air);
        // Full cyan stained glass curtain wall
        for (int ly = oy + 36; ly <= oy + 38; ly++) {
            for (int x = tx; x <= tx + 8; x++) {
                place(world, cb, new BlockPos(x, ly, tz),     cyanGlass);
                place(world, cb, new BlockPos(x, ly, tz + 8), cyanGlass);
            }
            for (int z = tz + 1; z <= tz + 7; z++) {
                place(world, cb, new BlockPos(tx,     ly, z), cyanGlass);
                place(world, cb, new BlockPos(tx + 8, ly, z), cyanGlass);
            }
        }
        // Interior radar screens + lighting
        place(world, cb, new BlockPos(tx + 2, oy + 36, tz + 2), observer);
        place(world, cb, new BlockPos(tx + 6, oy + 36, tz + 2), observer);
        place(world, cb, new BlockPos(tx + 2, oy + 36, tz + 6), observer);
        place(world, cb, new BlockPos(tx + 6, oy + 36, tz + 6), observer);
        place(world, cb, new BlockPos(tx + 4, oy + 36, tz + 4), shroom);
        place(world, cb, new BlockPos(tx + 2, oy + 38, tz + 2), seaLantern);
        place(world, cb, new BlockPos(tx + 6, oy + 38, tz + 6), seaLantern);

        // Roof cap + iron bar railing + 3 antennae
        fill(world, cb, tx, oy + 40, tz, tx + 8, oy + 40, tz + 8, sQuartz);
        for (int x = tx; x <= tx + 8; x++) {
            place(world, cb, new BlockPos(x, oy + 41, tz),     ironBars);
            place(world, cb, new BlockPos(x, oy + 41, tz + 8), ironBars);
        }
        for (int z = tz + 1; z <= tz + 7; z++) {
            place(world, cb, new BlockPos(tx,     oy + 41, z), ironBars);
            place(world, cb, new BlockPos(tx + 8, oy + 41, z), ironBars);
        }
        place(world, cb, new BlockPos(tx + 2, oy + 41, tz + 4), endRod);
        place(world, cb, new BlockPos(tx + 4, oy + 41, tz + 4), endRod);
        place(world, cb, new BlockPos(tx + 6, oy + 41, tz + 4), endRod);
    }

    // =========================================================================
    // HANGAR 1 — HorsePlane  (X: ox-5..ox+29, Z: oz+28..oz+67, H: 22)
    // Black concrete frame, gray concrete fill panels, dark oak plank floor,
    // iron block roof with roof truss cross-beams, shroomlight ceiling lighting
    // =========================================================================
    private static void buildHangar1(StructureWorldAccess world, BlockBox cb,
                                     int ox, int oy, int oz, Random random) {
        BlockState air        = Blocks.AIR.getDefaultState();
        BlockState ironBlock  = Blocks.IRON_BLOCK.getDefaultState();
        BlockState blackConc  = Blocks.BLACK_CONCRETE.getDefaultState();
        BlockState grayConc   = ModBlocks.CORRUGATED_METAL.getDefaultState();
        BlockState darkOak    = Blocks.DARK_OAK_PLANKS.getDefaultState();
        BlockState yellow     = Blocks.YELLOW_CONCRETE.getDefaultState();
        BlockState shroom     = Blocks.SHROOMLIGHT.getDefaultState();
        BlockState ironBars   = Blocks.IRON_BARS.getDefaultState();
        BlockState barrel     = Blocks.BARREL.getDefaultState();
        BlockState craftTable = Blocks.CRAFTING_TABLE.getDefaultState();
        BlockState furnace    = Blocks.FURNACE.getDefaultState();
        BlockState polGranite = Blocks.POLISHED_GRANITE.getDefaultState();

        int hx1 = ox - 5, hx2 = ox + 29;
        int hz1 = oz + 28, hz2 = oz + 67;
        int topY = oy + 22;

        // Floor — dark oak planks with yellow centerline
        fill(world, cb, hx1 + 1, oy, hz1 + 1, hx2 - 1, oy, hz2, darkOak);
        fill(world, cb, ox + 12, oy, hz1 + 1, ox + 12, oy, hz2, yellow);

        // Walls — corrugated metal fill + black concrete structural columns every 8 blocks
        // South wall (back wall, away from apron — hz1 side)
        fill(world, cb, hx1, oy, hz1, hx2, topY, hz1, grayConc);
        for (int cx = hx1; cx <= hx2; cx += 8)
            fill(world, cb, cx, oy, hz1, cx, topY, hz1, blackConc);
        // East wall
        fill(world, cb, hx2, oy, hz1, hx2, topY, hz2, grayConc);
        for (int cz = hz1; cz <= hz2; cz += 8)
            fill(world, cb, hx2, oy, cz, hx2, topY, cz, blackConc);
        // West wall
        fill(world, cb, hx1, oy, hz1, hx1, topY, hz2, grayConc);
        for (int cz = hz1; cz <= hz2; cz += 8)
            fill(world, cb, hx1, oy, cz, hx1, topY, cz, blackConc);
        // North face (hz2) is open — no wall built here

        // Roof — iron blocks + black concrete truss cross-beams every 8 blocks
        fill(world, cb, hx1, topY, hz1, hx2, topY, hz2, ironBlock);
        for (int cz = hz1; cz <= hz2; cz += 8)
            fill(world, cb, hx1 + 1, topY - 1, cz, hx2 - 1, topY - 1, cz, blackConc);

        // Shroomlights on underside of roof at bay centers
        for (int cz = hz1 + 4; cz <= hz2 - 4; cz += 8)
            place(world, cb, new BlockPos(ox + 12, topY - 1, cz), shroom);

        // Interior air
        fill(world, cb, hx1 + 1, oy + 1, hz1 + 1, hx2 - 1, topY - 2, hz2, air);

        // Fuel station — polished granite platform with 4 barrels (east side, near back wall)
        fill(world, cb, hx2 - 3, oy, hz1 + 4, hx2 - 2, oy, hz1 + 15, polGranite);
        for (int i = 0; i < 4; i++)
            place(world, cb, new BlockPos(hx2 - 2, oy + 1, hz1 + 5 + i * 3), barrel);

        // Mechanic station — SW corner (near back wall)
        place(world, cb, new BlockPos(hx1 + 2, oy + 1, hz1 + 2), craftTable);
        place(world, cb, new BlockPos(hx1 + 3, oy + 1, hz1 + 2), furnace);
        place(world, cb, new BlockPos(hx1 + 4, oy + 1, hz1 + 2), barrel);

        // Loot chest — back (south) wall center
        BlockPos chestPos = new BlockPos(ox + 12, oy + 1, hz1 + 1);
        place(world, cb, chestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH));
        if (cb.contains(chestPos) && world.getBlockEntity(chestPos) instanceof ChestBlockEntity che)
            che.setLootTable(AIRPORT_LOOT, random.nextLong());
    }

    // =========================================================================
    // HANGAR 2 — Service  (X: ox+65..ox+100, Z: oz+28..oz+67, H: 22)
    // Same industrial frame; gravel oil stains; equipment cluster; crash_site loot
    // =========================================================================
    private static void buildHangar2(StructureWorldAccess world, BlockBox cb,
                                     int ox, int oy, int oz, Random random) {
        BlockState air        = Blocks.AIR.getDefaultState();
        BlockState ironBlock  = Blocks.IRON_BLOCK.getDefaultState();
        BlockState blackConc  = Blocks.BLACK_CONCRETE.getDefaultState();
        BlockState grayConc   = ModBlocks.CORRUGATED_METAL.getDefaultState();
        BlockState darkOak    = Blocks.DARK_OAK_PLANKS.getDefaultState();
        BlockState gravel     = Blocks.GRAVEL.getDefaultState();
        BlockState shroom     = Blocks.SHROOMLIGHT.getDefaultState();
        BlockState ironBars   = Blocks.IRON_BARS.getDefaultState();
        BlockState barrel     = Blocks.BARREL.getDefaultState();
        BlockState craftTable = Blocks.CRAFTING_TABLE.getDefaultState();
        BlockState dispenser  = Blocks.DISPENSER.getDefaultState()
                .with(DispenserBlock.FACING, Direction.NORTH);

        int hx1 = ox + 65, hx2 = ox + 100;
        int hz1 = oz + 28, hz2 = oz + 67;
        int topY = oy + 22;
        int cx   = (hx1 + hx2) / 2;
        int cz   = (hz1 + hz2) / 2;

        // Dark oak floor
        fill(world, cb, hx1 + 1, oy, hz1 + 1, hx2 - 1, oy, hz2, darkOak);

        // Walls with black concrete structural columns
        // South wall (back wall, hz1 side)
        fill(world, cb, hx1, oy, hz1, hx2, topY, hz1, grayConc);
        for (int c = hx1; c <= hx2; c += 8) fill(world, cb, c, oy, hz1, c, topY, hz1, blackConc);
        fill(world, cb, hx2, oy, hz1, hx2, topY, hz2, grayConc);
        for (int c = hz1; c <= hz2; c += 8) fill(world, cb, hx2, oy, c, hx2, topY, c, blackConc);
        fill(world, cb, hx1, oy, hz1, hx1, topY, hz2, grayConc);
        for (int c = hz1; c <= hz2; c += 8) fill(world, cb, hx1, oy, c, hx1, topY, c, blackConc);
        // North face (hz2) is open

        // Iron block roof with truss beams
        fill(world, cb, hx1, topY, hz1, hx2, topY, hz2, ironBlock);
        for (int c = hz1; c <= hz2; c += 8)
            fill(world, cb, hx1 + 1, topY - 1, c, hx2 - 1, topY - 1, c, blackConc);
        for (int c = hz1 + 4; c <= hz2 - 4; c += 8)
            place(world, cb, new BlockPos(cx, topY - 1, c), shroom);

        // Interior air
        fill(world, cb, hx1 + 1, oy + 1, hz1 + 1, hx2 - 1, topY - 2, hz2, air);

        // Gravel oil-stain patches
        fill(world, cb, hx1 + 5,  oy, hz1 + 5,  hx1 + 7,  oy, hz1 + 7,  gravel);
        fill(world, cb, hx1 + 14, oy, hz1 + 18, hx1 + 16, oy, hz1 + 20, gravel);
        fill(world, cb, hx1 + 24, oy, hz1 + 28, hx1 + 26, oy, hz1 + 30, gravel);

        // Equipment cluster at center
        place(world, cb, new BlockPos(cx,     oy + 1, cz), ironBlock);
        place(world, cb, new BlockPos(cx + 1, oy + 1, cz), ironBlock);
        place(world, cb, new BlockPos(cx,     oy + 2, cz), craftTable);
        place(world, cb, new BlockPos(cx,     oy + 1, cz + 1), dispenser);
        // Barrels near back (south) wall
        place(world, cb, new BlockPos(hx1 + 3, oy + 1, hz1 + 2), barrel);
        place(world, cb, new BlockPos(hx1 + 5, oy + 1, hz1 + 2), barrel);
        place(world, cb, new BlockPos(hx1 + 7, oy + 1, hz1 + 2), barrel);

        // Loot chest — back (south) wall center
        BlockPos chestPos = new BlockPos(cx, oy + 1, hz1 + 1);
        place(world, cb, chestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH));
        if (cb.contains(chestPos) && world.getBlockEntity(chestPos) instanceof ChestBlockEntity che)
            che.setLootTable(CRASH_LOOT, random.nextLong());
    }

    // =========================================================================
    // APRON MARKINGS
    // =========================================================================
    private static void buildApron(StructureWorldAccess world, BlockBox cb, int ox, int oy, int oz) {
        BlockState yellow = Blocks.YELLOW_CONCRETE.getDefaultState();
        BlockState white  = Blocks.WHITE_CONCRETE.getDefaultState();

        fill(world, cb, ox - 5, oy, oz + 48, ox + 100, oy, oz + 48, yellow);
        int[][] pads = {{ox + 10, oz + 50}, {ox + 42, oz + 55}, {ox + 75, oz + 50}};
        for (int[] pad : pads) {
            int px = pad[0], pz = pad[1];
            for (int x = px; x <= px + 5; x++) {
                place(world, cb, new BlockPos(x, oy, pz),     white);
                place(world, cb, new BlockPos(x, oy, pz + 5), white);
            }
            for (int z = pz + 1; z <= pz + 4; z++) {
                place(world, cb, new BlockPos(px,     oy, z), white);
                place(world, cb, new BlockPos(px + 5, oy, z), white);
            }
        }
        for (int x = ox + 33; x <= ox + 52; x += 3)
            fill(world, cb, x, oy, oz + 84, x + 1, oy, oz + 84, white);
    }

    // =========================================================================
    // TAXIWAY  (X: ox+33..ox+52, Z: oz+68..oz+84)
    // =========================================================================
    private static void buildTaxiway(StructureWorldAccess world, BlockBox cb, int ox, int oy, int oz) {
        fill(world, cb, ox + 42, oy, oz + 68, ox + 42, oy, oz + 84, Blocks.YELLOW_CONCRETE.getDefaultState());
        BlockState white = Blocks.WHITE_CONCRETE.getDefaultState();
        for (int x = ox + 33; x <= ox + 52; x += 3)
            fill(world, cb, x, oy, oz + 83, x + 1, oy, oz + 83, white);
    }

    // =========================================================================
    // RUNWAY  (X: ox+33..ox+52 = 20w, Z: oz+85..oz+204 = 120L)
    // Light gray concrete (distinct from taxiway gray), standard markings
    // =========================================================================
    private static void buildRunway(StructureWorldAccess world, BlockBox cb, int ox, int oy, int oz) {
        BlockState lgConc     = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
        BlockState white      = Blocks.WHITE_CONCRETE.getDefaultState();
        BlockState yellow     = Blocks.YELLOW_CONCRETE.getDefaultState();
        BlockState seaLantern = Blocks.SEA_LANTERN.getDefaultState();
        BlockState gravel     = Blocks.GRAVEL.getDefaultState();

        int rx1 = ox + 33, rx2 = ox + 52;
        int rz1 = oz + 85;

        // Runway surface (light gray — brighter/cleaner than gray taxiway)
        fill(world, cb, rx1, oy, rz1, rx2, oy, oz + 204, lgConc);

        // Threshold markings — white stripes at both ends
        for (int x = rx1; x <= rx2; x += 3) {
            fill(world, cb, x, oy, rz1,      x + 1, oy, rz1 + 3,      white);
            fill(world, cb, x, oy, oz + 201, x + 1, oy, oz + 204,     white);
        }
        // Displaced threshold yellow zone
        fill(world, cb, rx1, oy, rz1, rx2, oy, rz1 + 5, yellow);

        // Centerline dashes (3-on / 1-off)
        for (int dz = 0; dz < 120; dz++)
            if (dz % 4 != 3)
                place(world, cb, new BlockPos(ox + 42, oy, rz1 + dz), white);

        // Aiming point markers (two 3×6 white rects each side of centreline)
        fill(world, cb, rx1 + 2, oy, oz + 125, rx1 + 4, oy, oz + 130, white);
        fill(world, cb, rx2 - 4, oy, oz + 125, rx2 - 2, oy, oz + 130, white);
        fill(world, cb, rx1 + 2, oy, oz + 165, rx1 + 4, oy, oz + 170, white);
        fill(world, cb, rx2 - 4, oy, oz + 165, rx2 - 2, oy, oz + 170, white);

        // Edge lights every 10 blocks
        for (int dz = 0; dz <= 120; dz += 10) {
            place(world, cb, new BlockPos(rx1, oy + 1, rz1 + dz), seaLantern);
            place(world, cb, new BlockPos(rx2, oy + 1, rz1 + dz), seaLantern);
        }

        // Overrun gravel
        fill(world, cb, rx1, oy, oz + 205, rx2, oy, oz + 209, gravel);
    }

    // =========================================================================
    // WINDSOCK
    // =========================================================================
    private static void buildWindsock(StructureWorldAccess world, BlockBox cb, int ox, int oy, int oz) {
        fill(world, cb, ox + 106, oy + 1, oz + 100, ox + 106, oy + 4, oz + 100,
                Blocks.IRON_BARS.getDefaultState());
        place(world, cb, new BlockPos(ox + 106, oy + 5, oz + 100), Blocks.ORANGE_WOOL.getDefaultState());
        place(world, cb, new BlockPos(ox + 107, oy + 5, oz + 100), Blocks.WHITE_WOOL.getDefaultState());
    }

    // =========================================================================
    // HELPERS
    // =========================================================================
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
