package com.sloppyjoe.item;

import com.sloppyjoe.network.GlassesEquipPayload;
import com.sloppyjoe.network.GrabSyncPayload;
import com.sloppyjoe.network.ReleaseSyncPayload;
import com.sloppyjoe.world.LensPersistentState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PerspectiveLensItem extends Item {

    public enum GrabType { BLOCK, ENTITY, PLAYER_SCALE }

    /**
     * One extra block that is part of the same multi-block structure as the grabbed block.
     * {@code shapeBoxes} contains every constituent AABB from the block's VoxelShape,
     * captured at grab time (while neighbours are still in place) for context-dependent shapes.
     * {@code spawnDisplay} controls whether a display entity is spawned for this part on release.
     */
    public record ExtraBlock(Vec3i offset, BlockState state, List<Box> shapeBoxes, boolean spawnDisplay) {}

    public record GrabData(
            GrabType type,
            BlockState blockState,
            EntityType<?> entityType,
            NbtCompound entityNbt,        // PLAYER_SCALE: stores "OriginalScale" double
            double grabDistance,
            float scaleX, float scaleY, float scaleZ,   // per-axis scale multipliers (default 1.0f)
            int rotationSteps,            // 0–7; each step = 45° CCW around Y
            List<ExtraBlock> extraBlocks,
            List<Box> shapeBoxes,         // constituent VoxelShape AABBs captured at grab time
            UUID scalingTargetUuid        // non-null only for PLAYER_SCALE grabs
    ) {
        // Convenience constructor for entity/player grabs (no block shape needed).
        public GrabData(GrabType type, BlockState blockState, EntityType<?> entityType,
                        NbtCompound entityNbt, double grabDistance, float scaleX, float scaleY, float scaleZ) {
            this(type, blockState, entityType, entityNbt, grabDistance, scaleX, scaleY, scaleZ,
                    0, List.of(), List.of(), null);
        }
    }

    private static final float SCALE_STEP = 1.25f;
    private static final float SCALE_MIN  = 0.05f;
    private static final float SCALE_MAX  = 50.0f;
    // Per-axis barrier span cap: 16 covers up to ×16 scale on any single axis.
    private static final int BARRIER_MAX_DIM   = 16;
    // Total barrier cap per spawnScaledBlock call (all parts combined).
    private static final int BARRIER_MAX_TOTAL = 2048;

    public PerspectiveLensItem(Settings settings) {
        super(settings);
    }

    public static void cancelGrab(MinecraftServer server, UUID playerUuid) {
        LensPersistentState state = LensPersistentState.getOrCreate(server);
        GrabData existing = state.grabMap.get(playerUuid);
        if (existing != null && existing.type() == GrabType.PLAYER_SCALE) {
            // Revert target player's scale to original
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(existing.scalingTargetUuid());
            if (target != null) {
                double original = existing.entityNbt().getDouble("OriginalScale");
                EntityAttributeInstance inst = target.getAttributes().getCustomInstance(EntityAttributes.SCALE);
                if (inst != null) {
                    inst.setBaseValue(original);
                }
            }
        }
        state.grabMap.remove(playerUuid);
        state.markDirty();
    }

    public static void adjustScale(MinecraftServer server, UUID playerUuid, boolean increase, int axis) {
        LensPersistentState state = LensPersistentState.getOrCreate(server);
        GrabData data = state.grabMap.get(playerUuid);
        if (data == null) return;

        float factor = increase ? SCALE_STEP : 1f / SCALE_STEP;
        float newX = data.scaleX();
        float newY = data.scaleY();
        float newZ = data.scaleZ();
        if (axis == 0 || axis == 1) newX = clamp(newX * factor);
        if (axis == 0 || axis == 2) newY = clamp(newY * factor);
        if (axis == 0 || axis == 3) newZ = clamp(newZ * factor);

        GrabData updated = new GrabData(data.type(), data.blockState(), data.entityType(),
                data.entityNbt(), data.grabDistance(), newX, newY, newZ,
                data.rotationSteps(), data.extraBlocks(), data.shapeBoxes(), data.scalingTargetUuid());
        state.grabMap.put(playerUuid, updated);
        state.markDirty();

        // For PLAYER_SCALE: live-update target player's scale (scaleY is used as uniform player scale)
        if (data.type() == GrabType.PLAYER_SCALE && data.scalingTargetUuid() != null) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(data.scalingTargetUuid());
            if (target != null) {
                EntityAttributeInstance inst = target.getAttributes().getCustomInstance(EntityAttributes.SCALE);
                if (inst != null) {
                    double original = data.entityNbt().getDouble("OriginalScale");
                    inst.setBaseValue(original * newY);
                }
            }
        }
    }

    public static void adjustRotation(MinecraftServer server, UUID playerUuid, boolean clockwise) {
        LensPersistentState state = LensPersistentState.getOrCreate(server);
        GrabData data = state.grabMap.get(playerUuid);
        if (data == null) return;

        int steps = (data.rotationSteps() + (clockwise ? 1 : 7)) % 8;
        GrabData updated = new GrabData(data.type(), data.blockState(), data.entityType(),
                data.entityNbt(), data.grabDistance(), data.scaleX(), data.scaleY(), data.scaleZ(),
                steps, data.extraBlocks(), data.shapeBoxes(), data.scalingTargetUuid());
        state.grabMap.put(playerUuid, updated);
        state.markDirty();
    }

    private static float clamp(float v) {
        return Math.max(SCALE_MIN, Math.min(SCALE_MAX, v));
    }

    // ---------- UseEntityCallback entry point ----------

    public static ActionResult handleEntityUse(PlayerEntity user, World world, Entity entity, Hand hand) {
        if (world.isClient()) return ActionResult.PASS;

        UUID uuid = user.getUuid();
        LensPersistentState state = LensPersistentState.getOrCreate(world.getServer());
        if (state.grabMap.containsKey(uuid)) return ActionResult.PASS;

        // Re-grab a previously released BlockDisplayEntity
        if (entity instanceof DisplayEntity.BlockDisplayEntity display) {
            List<BlockPos> barriers = state.barrierMap.remove(display.getUuid());
            if (barriers != null) {
                for (BlockPos bp : barriers) {
                    if (world.getBlockState(bp).isOf(Blocks.BARRIER)) {
                        world.setBlockState(bp, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    }
                }
            }

            List<UUID> extraUuids = state.extraDisplayMap.remove(display.getUuid());
            if (extraUuids != null && world instanceof ServerWorld serverWorld) {
                for (UUID extraUuid : extraUuids) {
                    Entity extra = serverWorld.getEntity(extraUuid);
                    if (extra != null) extra.discard();
                }
            }
            state.markDirty();

            BlockState bs = display.getBlockState();
            double dist = user.getEyePos().distanceTo(entity.getPos());
            int stateId = Block.STATE_IDS.getRawId(bs);
            List<Box> shapeBoxes = computeShapeBoxes(world, display.getBlockPos(), bs, user);
            state.grabMap.put(uuid, new GrabData(GrabType.BLOCK, bs, null, new NbtCompound(),
                    dist, 1.0f, 1.0f, 1.0f, 0, List.of(), shapeBoxes, null));
            state.markDirty();
            display.discard();
            ServerPlayNetworking.send((ServerPlayerEntity) user, new GrabSyncPayload(
                    true, stateId, Registries.ENTITY_TYPE.getId(EntityType.PIG), new NbtCompound(), dist));
            broadcastGlassesEquip(world, uuid, true);
            return ActionResult.SUCCESS;
        }

        // Player entity: PLAYER_SCALE grab (player stays in world)
        if (entity instanceof ServerPlayerEntity target && target != user) {
            EntityAttributeInstance inst = target.getAttributes().getCustomInstance(EntityAttributes.SCALE);
            double originalScale = (inst != null) ? inst.getBaseValue() : 1.0;
            NbtCompound nbt = new NbtCompound();
            nbt.putDouble("OriginalScale", originalScale);
            UUID targetUuid = target.getUuid();
            double dist = user.getEyePos().distanceTo(entity.getPos());
            state.grabMap.put(uuid, new GrabData(
                    GrabType.PLAYER_SCALE, null,
                    EntityType.PLAYER, nbt, dist,
                    1f, 1f, 1f, 0,
                    List.of(), List.of(), targetUuid));
            state.markDirty();
            ServerPlayNetworking.send((ServerPlayerEntity) user,
                    new GrabSyncPayload(false, 0, Registries.ENTITY_TYPE.getId(EntityType.PLAYER), nbt, dist));
            broadcastGlassesEquip(world, uuid, true);
            return ActionResult.SUCCESS;
        }

        // Grab any other entity
        double dist = user.getEyePos().distanceTo(entity.getPos());
        NbtCompound nbt = new NbtCompound();
        entity.writeNbt(nbt);
        EntityType<?> type = entity.getType();
        Identifier typeId = Registries.ENTITY_TYPE.getId(type);
        entity.discard();

        state.grabMap.put(uuid, new GrabData(GrabType.ENTITY, null, type, nbt, dist, 1.0f, 1.0f, 1.0f));
        state.markDirty();
        ServerPlayNetworking.send((ServerPlayerEntity) user,
                new GrabSyncPayload(false, 0, typeId, nbt, dist));
        broadcastGlassesEquip(world, uuid, true);
        return ActionResult.SUCCESS;
    }

    // ---------- helpers ----------

    private static void broadcastGlassesEquip(World world, UUID playerUuid, boolean on) {
        if (world.getServer() == null) return;
        world.getServer().getPlayerManager().sendToAll(
                ServerPlayNetworking.createS2CPacket(new GlassesEquipPayload(playerUuid, on)));
    }

    /**
     * Decomposes the block's collision VoxelShape into its constituent AABB boxes.
     * Returning every sub-box (not just the coarse bounding-box union) gives faithful
     * barrier placement for complex shapes such as stairs, fences, walls, and trapdoors.
     *
     * <p>Must be called BEFORE any part of the multi-block structure is removed so that
     * context-dependent shapes (fences connecting to neighbours, etc.) resolve correctly.</p>
     */
    private static List<Box> computeShapeBoxes(World world, BlockPos pos, BlockState state, Entity entity) {
        VoxelShape shape = state.getCollisionShape(world, pos, ShapeContext.of(entity));
        if (shape.isEmpty()) return List.of();
        List<Box> boxes = new ArrayList<>();
        shape.forEachBox((x1, y1, z1, x2, y2, z2) -> boxes.add(new Box(x1, y1, z1, x2, y2, z2)));
        return boxes.isEmpty() ? List.of() : List.copyOf(boxes);
    }

    /**
     * Detects connected halves of multi-block structures and returns them as ExtraBlock entries.
     * Shape boxes are computed here while all blocks are still in place so that
     * context-dependent collision shapes are captured correctly.
     */
    private static List<ExtraBlock> collectExtraBlocks(World world, BlockPos pos, BlockState state, Entity entity) {
        List<ExtraBlock> extras = new ArrayList<>();

        // Doors and tall double-block plants (DoubleBlockHalf: LOWER / UPPER)
        if (state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
            BlockPos otherPos = (half == DoubleBlockHalf.LOWER) ? pos.up() : pos.down();
            BlockState other = world.getBlockState(otherPos);
            if (other.isOf(state.getBlock())) {
                List<Box> otherBoxes = computeShapeBoxes(world, otherPos, other, entity);
                extras.add(new ExtraBlock(
                        new Vec3i(0, otherPos.getY() - pos.getY(), 0),
                        other, otherBoxes, true));
            }
        }

        // Beds (BedPart: FOOT / HEAD)
        if (state.contains(Properties.BED_PART) && state.contains(Properties.HORIZONTAL_FACING)) {
            BedPart part = state.get(Properties.BED_PART);
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            BlockPos otherPos = (part == BedPart.FOOT)
                    ? pos.offset(facing) : pos.offset(facing.getOpposite());
            BlockState other = world.getBlockState(otherPos);
            if (other.isOf(state.getBlock())) {
                List<Box> otherBoxes = computeShapeBoxes(world, otherPos, other, entity);
                extras.add(new ExtraBlock(
                        new Vec3i(otherPos.getX() - pos.getX(), 0, otherPos.getZ() - pos.getZ()),
                        other, otherBoxes, false));
            }
        }

        return extras;
    }

    // ---------- useOnBlock: grab block OR release ----------

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity user = context.getPlayer();
        if (user == null || world.isClient()) return ActionResult.PASS;

        UUID uuid = user.getUuid();
        LensPersistentState state = LensPersistentState.getOrCreate(world.getServer());
        GrabData existing = state.grabMap.get(uuid);

        if (existing == null) {
            // --- GRAB ---
            BlockPos pos = context.getBlockPos();
            BlockState bs = world.getBlockState(pos);
            if (bs.isAir()) return ActionResult.PASS;

            double dist = user.getEyePos().distanceTo(context.getHitPos());
            // Capture ALL constituent VoxelShape AABBs before removing any blocks.
            List<Box> shapeBoxes = computeShapeBoxes(world, pos, bs, user);
            List<ExtraBlock> extraBlocks = collectExtraBlocks(world, pos, bs, user);

            for (ExtraBlock extra : extraBlocks) {
                BlockPos extraPos = pos.add(extra.offset());
                if (world.getBlockState(extraPos).isOf(bs.getBlock())) {
                    world.setBlockState(extraPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

            state.grabMap.put(uuid, new GrabData(GrabType.BLOCK, bs, null, new NbtCompound(),
                    dist, 1.0f, 1.0f, 1.0f, 0, extraBlocks, shapeBoxes, null));
            state.markDirty();

            int stateId = Block.STATE_IDS.getRawId(bs);
            ServerPlayNetworking.send((ServerPlayerEntity) user, new GrabSyncPayload(
                    true, stateId, Registries.ENTITY_TYPE.getId(EntityType.PIG), new NbtCompound(), dist));
            broadcastGlassesEquip(world, uuid, true);

        } else {
            // --- RELEASE ---
            BlockPos targetPos = context.getBlockPos().offset(context.getSide());
            double releaseDistance = user.getEyePos().distanceTo(context.getHitPos());
            double grabDistance = existing.grabDistance();

            switch (existing.type()) {
                case BLOCK -> {
                    float sx = (float)(releaseDistance / grabDistance) * existing.scaleX();
                    float sy = (float)(releaseDistance / grabDistance) * existing.scaleY();
                    float sz = (float)(releaseDistance / grabDistance) * existing.scaleZ();
                    sx = Math.max(SCALE_MIN, sx);
                    sy = Math.max(SCALE_MIN, sy);
                    sz = Math.max(SCALE_MIN, sz);
                    spawnScaledBlock(world, existing, sx, sy, sz, targetPos, state);
                    user.sendMessage(net.minecraft.text.Text.literal(
                            String.format("\u00a77Released at scale \u00a7f%.3f\u00a77/\u00a7f%.3f\u00a77/\u00a7f%.3fx",
                                    sx, sy, sz)), true);
                }
                case ENTITY -> {
                    float sx = (float)(releaseDistance / grabDistance) * existing.scaleX();
                    float sy = (float)(releaseDistance / grabDistance) * existing.scaleY();
                    float sz = (float)(releaseDistance / grabDistance) * existing.scaleZ();
                    sx = Math.max(SCALE_MIN, sx);
                    sy = Math.max(SCALE_MIN, sy);
                    sz = Math.max(SCALE_MIN, sz);
                    // For entities, use scaleY as the uniform scale (EntityAttributes.SCALE is uniform)
                    spawnScaledEntity(world, existing, sy, Vec3d.ofCenter(targetPos));
                    user.sendMessage(net.minecraft.text.Text.literal(
                            String.format("\u00a77Released at scale \u00a7f%.3fx", sy)), true);
                }
                case PLAYER_SCALE -> {
                    ServerPlayerEntity target = (ServerPlayerEntity) world.getServer()
                            .getPlayerManager().getPlayer(existing.scalingTargetUuid());
                    if (target != null) {
                        float effectiveScale = (float)(releaseDistance / grabDistance) * existing.scaleY();
                        effectiveScale = Math.max(SCALE_MIN, effectiveScale);
                        EntityAttributeInstance inst = target.getAttributes()
                                .getCustomInstance(EntityAttributes.SCALE);
                        if (inst != null) {
                            double original = existing.entityNbt().getDouble("OriginalScale");
                            inst.setBaseValue(original * effectiveScale);
                        }
                    }
                    user.sendMessage(net.minecraft.text.Text.literal(
                            "\u00a77Player scale finalized"), true);
                }
            }

            state.grabMap.remove(uuid);
            state.markDirty();
            ServerPlayNetworking.send((ServerPlayerEntity) user, new ReleaseSyncPayload());
            broadcastGlassesEquip(world, uuid, false);
        }

        return ActionResult.SUCCESS;
    }

    // ---------- block display entity spawning ----------

    private static void spawnScaledBlock(World world, GrabData data,
                                         float sx, float sy, float sz,
                                         BlockPos targetPos, LensPersistentState state) {
        Vec3d entityPos = Vec3d.of(targetPos);
        DisplayEntity.BlockDisplayEntity mainDisplay =
                createDisplayEntity(world, data.blockState(), sx, sy, sz, data.rotationSteps(), entityPos);
        if (mainDisplay == null) return;
        world.spawnEntity(mainDisplay);

        List<BlockPos> allBarriers = new ArrayList<>();
        // Pass the exact floating-point entity position so fractional offsets
        // are accounted for in the world-space extent calculation.
        placeBarriers(world, data.shapeBoxes(), sx, sy, sz, data.rotationSteps(), entityPos, allBarriers);

        List<UUID> extraUuids = new ArrayList<>();
        for (ExtraBlock extra : data.extraBlocks()) {
            // Scaled offset from the main entity position (Y-only for doors, XZ for beds).
            Vec3d extraEntityPos = entityPos.add(
                    extra.offset().getX() * sx,
                    extra.offset().getY() * sy,
                    extra.offset().getZ() * sz);

            // Use the precise Vec3d position — not BlockPos.ofFloored — so fractional
            // entity positions (e.g. door upper half at y+1.5 when sy=1.5) produce
            // barriers that are aligned to where the display entity actually renders.
            placeBarriers(world, extra.shapeBoxes(), sx, sy, sz, data.rotationSteps(), extraEntityPos, allBarriers);

            if (extra.spawnDisplay()) {
                DisplayEntity.BlockDisplayEntity extraDisplay =
                        createDisplayEntity(world, extra.state(), sx, sy, sz, data.rotationSteps(), extraEntityPos);
                if (extraDisplay != null) {
                    world.spawnEntity(extraDisplay);
                    extraUuids.add(extraDisplay.getUuid());
                }
            }
        }

        if (!allBarriers.isEmpty()) {
            state.barrierMap.put(mainDisplay.getUuid(), allBarriers);
            state.markDirty();
        }
        if (!extraUuids.isEmpty()) {
            state.extraDisplayMap.put(mainDisplay.getUuid(), extraUuids);
            state.markDirty();
        }
    }

    /** Creates (but does not spawn) a scaled+rotated BlockDisplayEntity positioned at {@code pos}. */
    private static DisplayEntity.BlockDisplayEntity createDisplayEntity(
            World world, BlockState bs, float sx, float sy, float sz, int rotationSteps, Vec3d pos) {
        Entity created = EntityType.BLOCK_DISPLAY.create(world, SpawnReason.COMMAND);
        if (!(created instanceof DisplayEntity.BlockDisplayEntity display)) return null;
        display.setBlockState(bs);
        Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(rotationSteps * 45f));
        display.setTransformation(new AffineTransformation(
                null, rot, new org.joml.Vector3f(sx, sy, sz), null));
        display.setPosition(pos);
        return display;
    }

    /**
     * Places invisible barrier blocks in every integer-aligned cell that the scaled,
     * rotated block visually occupies.
     *
     * <p>The display entity's model (local space [0,1]³) is first scaled by (sx,sy,sz)
     * then left-rotated around Y by {@code rotationSteps × 45°} — matching the exact
     * {@link AffineTransformation} applied in {@link #createDisplayEntity}.
     * Each constituent VoxelShape AABB ({@code shapeBoxes}) is transformed individually
     * so complex shapes (stairs, fences, walls…) are represented faithfully rather than
     * as their coarse union bounding box.</p>
     *
     * <p>World-space extents are computed in floating-point and converted to block cells
     * with {@code floor}/{@code ceil-ε} so barriers are placed exactly where the visual
     * occupies a cell without bleeding into the next cell when the edge lands on an integer
     * boundary.  Negative cell offsets (possible after Y-rotation) are handled correctly.</p>
     *
     * <p>Per-axis span is capped at {@value #BARRIER_MAX_DIM} and the total barrier count
     * for the whole spawning operation is capped at {@value #BARRIER_MAX_TOTAL}.</p>
     *
     * @param entityPos world position of the display entity (may be fractional for extra blocks)
     */
    private static void placeBarriers(World world, List<Box> shapeBoxes,
                                      float sx, float sy, float sz, int rotationSteps,
                                      Vec3d entityPos, List<BlockPos> barriers) {
        if (shapeBoxes.isEmpty()) return;
        if (barriers.size() >= BARRIER_MAX_TOTAL) return;

        // Pre-compute rotation — matches Quaternionf.rotateY(angle) used in createDisplayEntity.
        // Transformation order: scale first, then left-rotate (L * S).
        //   rotated.x = sx*cx * cos  −  sz*cz * sin
        //   rotated.z = sx*cx * sin  +  sz*cz * cos
        double angle = Math.toRadians(rotationSteps * 45.0);
        double cos   = Math.cos(angle);
        double sin   = Math.sin(angle);

        BlockPos.Mutable bp = new BlockPos.Mutable();

        for (Box box : shapeBoxes) {
            if (barriers.size() >= BARRIER_MAX_TOTAL) return;

            // Rotate all four XZ corners of this scaled box to find the tight AABB
            // of the rotated region in world space (Y axis is unaffected by Y-rotation).
            double minRX =  Double.MAX_VALUE,  maxRX = -Double.MAX_VALUE;
            double minRZ =  Double.MAX_VALUE,  maxRZ = -Double.MAX_VALUE;
            double[] cxs = { box.minX, box.maxX };
            double[] czs = { box.minZ, box.maxZ };
            for (double cx : cxs) {
                for (double cz : czs) {
                    double rx = sx * cx * cos - sz * cz * sin;
                    double rz = sx * cx * sin + sz * cz * cos;
                    if (rx < minRX) minRX = rx;
                    if (rx > maxRX) maxRX = rx;
                    if (rz < minRZ) minRZ = rz;
                    if (rz > maxRZ) maxRZ = rz;
                }
            }

            // Absolute world-space extent of this box component.
            double worldMinX = entityPos.x + minRX;
            double worldMinY = entityPos.y + sy * box.minY;
            double worldMinZ = entityPos.z + minRZ;
            double worldMaxX = entityPos.x + maxRX;
            double worldMaxY = entityPos.y + sy * box.maxY;
            double worldMaxZ = entityPos.z + maxRZ;

            // Convert to integer block cells.
            // A barrier at cell C covers [C, C+1].  We want all C where the visual
            // overlaps the interior of [C, C+1]: worldMin < C+1  AND  worldMax > C.
            // → cellMin = floor(worldMin)
            // → cellMax = ceil(worldMax − ε) − 1
            //   The ε (1e-9) prevents an exact integer worldMax from bleeding into the
            //   next cell (e.g. worldMax=2.0 should NOT place a barrier at cell 2).
            int cellMinX = (int) Math.floor(worldMinX);
            int cellMinY = (int) Math.floor(worldMinY);
            int cellMinZ = (int) Math.floor(worldMinZ);
            int cellMaxX = (int) Math.ceil(worldMaxX - 1e-9) - 1;
            int cellMaxY = (int) Math.ceil(worldMaxY - 1e-9) - 1;
            int cellMaxZ = (int) Math.ceil(worldMaxZ - 1e-9) - 1;

            int spanX = cellMaxX - cellMinX + 1;
            int spanY = cellMaxY - cellMinY + 1;
            int spanZ = cellMaxZ - cellMinZ + 1;

            if (spanX <= 0 || spanY <= 0 || spanZ <= 0) continue;
            if (spanX > BARRIER_MAX_DIM || spanY > BARRIER_MAX_DIM || spanZ > BARRIER_MAX_DIM) continue;
            // Guard against the unlikely case where one box alone would exceed the total.
            if (barriers.size() + (long) spanX * spanY * spanZ > BARRIER_MAX_TOTAL) continue;

            for (int bx = cellMinX; bx <= cellMaxX; bx++) {
                for (int by = cellMinY; by <= cellMaxY; by++) {
                    for (int bz = cellMinZ; bz <= cellMaxZ; bz++) {
                        if (barriers.size() >= BARRIER_MAX_TOTAL) return;
                        bp.set(bx, by, bz);
                        if (world.getBlockState(bp).isAir()) {
                            world.setBlockState(bp, Blocks.BARRIER.getDefaultState(), Block.NOTIFY_ALL);
                            barriers.add(bp.toImmutable());
                        }
                    }
                }
            }
        }
    }

    private static void spawnScaledEntity(World world, GrabData data, float effectiveScale, Vec3d pos) {
        Entity spawned = data.entityType().create(world, SpawnReason.COMMAND);
        if (spawned == null) return;

        spawned.readNbt(data.entityNbt().copy());

        com.sloppyjoe.SloppyJoeMod.LOGGER.info(
                "[PerspectiveLens] spawnScaledEntity type={} effectiveScale={}",
                data.entityType().getUntranslatedName(), effectiveScale);

        if (spawned instanceof LivingEntity living) {
            EntityAttributeInstance inst =
                    living.getAttributes().getCustomInstance(EntityAttributes.SCALE);
            if (inst != null) {
                double before = inst.getBaseValue();
                inst.setBaseValue(before * effectiveScale);
                com.sloppyjoe.SloppyJoeMod.LOGGER.info(
                        "[PerspectiveLens] SCALE {} -> {}", before, inst.getBaseValue());
            } else {
                com.sloppyjoe.SloppyJoeMod.LOGGER.warn(
                        "[PerspectiveLens] SCALE instance is null for {}",
                        data.entityType().getUntranslatedName());
            }
        } else {
            com.sloppyjoe.SloppyJoeMod.LOGGER.info(
                    "[PerspectiveLens] entity is not LivingEntity, skipping scale");
        }

        spawned.setPosition(pos);
        world.spawnEntity(spawned);
    }
}
