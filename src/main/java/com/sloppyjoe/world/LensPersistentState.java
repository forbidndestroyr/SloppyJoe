package com.sloppyjoe.world;

import com.sloppyjoe.item.PerspectiveLensItem;
import com.sloppyjoe.item.PerspectiveLensItem.GrabData;
import com.sloppyjoe.item.PerspectiveLensItem.GrabType;
import com.sloppyjoe.item.PerspectiveLensItem.ExtraBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LensPersistentState extends PersistentState {

    public static final String KEY = "sloppyjoe_lens";

    public final Map<UUID, GrabData>       grabMap         = new HashMap<>();
    public final Map<UUID, List<BlockPos>> barrierMap      = new HashMap<>();
    public final Map<UUID, List<UUID>>     extraDisplayMap = new HashMap<>();

    public static final Type<LensPersistentState> TYPE = new Type<>(
            LensPersistentState::new,
            LensPersistentState::fromNbt,
            null
    );

    public static LensPersistentState getOrCreate(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    // ---- serialization ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        // grabMap
        NbtList grabs = new NbtList();
        for (Map.Entry<UUID, GrabData> entry : grabMap.entrySet()) {
            NbtCompound g = new NbtCompound();
            g.putUuid("uuid", entry.getKey());
            GrabData d = entry.getValue();
            g.putString("type", d.type().name());
            if (d.blockState() != null) {
                g.putInt("blockStateId", Block.STATE_IDS.getRawId(d.blockState()));
            }
            if (d.entityType() != null) {
                g.putString("entityTypeId", Registries.ENTITY_TYPE.getId(d.entityType()).toString());
            }
            if (d.entityNbt() != null) {
                g.put("entityNbt", d.entityNbt().copy());
            }
            g.putDouble("grabDistance", d.grabDistance());
            g.putFloat("scaleX", d.scaleX());
            g.putFloat("scaleY", d.scaleY());
            g.putFloat("scaleZ", d.scaleZ());
            g.putInt("rotationSteps", d.rotationSteps());
            if (d.scalingTargetUuid() != null) {
                g.putUuid("scalingTargetUuid", d.scalingTargetUuid());
            }
            // shapeBoxes — list of constituent VoxelShape AABBs
            NbtList sbList = new NbtList();
            for (Box sb : d.shapeBoxes()) {
                NbtCompound sbc = new NbtCompound();
                sbc.putDouble("x1", sb.minX); sbc.putDouble("y1", sb.minY); sbc.putDouble("z1", sb.minZ);
                sbc.putDouble("x2", sb.maxX); sbc.putDouble("y2", sb.maxY); sbc.putDouble("z2", sb.maxZ);
                sbList.add(sbc);
            }
            g.put("shapeBoxes", sbList);
            // extraBlocks
            NbtList extraList = new NbtList();
            for (ExtraBlock eb : d.extraBlocks()) {
                NbtCompound ec = new NbtCompound();
                ec.putInt("offX", eb.offset().getX());
                ec.putInt("offY", eb.offset().getY());
                ec.putInt("offZ", eb.offset().getZ());
                ec.putInt("stateId", Block.STATE_IDS.getRawId(eb.state()));
                ec.putBoolean("spawnDisplay", eb.spawnDisplay());
                // shapeBoxes for this extra block part
                NbtList ebBoxList = new NbtList();
                for (Box eb2 : eb.shapeBoxes()) {
                    NbtCompound ebc = new NbtCompound();
                    ebc.putDouble("x1", eb2.minX); ebc.putDouble("y1", eb2.minY); ebc.putDouble("z1", eb2.minZ);
                    ebc.putDouble("x2", eb2.maxX); ebc.putDouble("y2", eb2.maxY); ebc.putDouble("z2", eb2.maxZ);
                    ebBoxList.add(ebc);
                }
                ec.put("shapeBoxes", ebBoxList);
                extraList.add(ec);
            }
            g.put("extraBlocks", extraList);
            grabs.add(g);
        }
        nbt.put("grabs", grabs);

        // barrierMap
        NbtList barriers = new NbtList();
        for (Map.Entry<UUID, List<BlockPos>> entry : barrierMap.entrySet()) {
            NbtCompound b = new NbtCompound();
            b.putUuid("uuid", entry.getKey());
            NbtList posList = new NbtList();
            for (BlockPos bp : entry.getValue()) {
                posList.add(NbtLong.of(bp.asLong()));
            }
            b.put("positions", posList);
            barriers.add(b);
        }
        nbt.put("barriers", barriers);

        // extraDisplayMap
        NbtList extras = new NbtList();
        for (Map.Entry<UUID, List<UUID>> entry : extraDisplayMap.entrySet()) {
            NbtCompound e = new NbtCompound();
            e.putUuid("uuid", entry.getKey());
            NbtList uuidList = new NbtList();
            for (UUID u : entry.getValue()) {
                NbtCompound uc = new NbtCompound();
                uc.putUuid("u", u);
                uuidList.add(uc);
            }
            e.put("uuids", uuidList);
            extras.add(e);
        }
        nbt.put("extras", extras);

        return nbt;
    }

    public static LensPersistentState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        LensPersistentState state = new LensPersistentState();

        // grabMap
        NbtList grabs = nbt.getList("grabs", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < grabs.size(); i++) {
            NbtCompound g = grabs.getCompound(i);
            try {
                UUID uuid = g.getUuid("uuid");
                GrabType type = GrabType.valueOf(g.getString("type"));
                BlockState blockState = null;
                if (g.contains("blockStateId")) {
                    blockState = Block.STATE_IDS.get(g.getInt("blockStateId"));
                }
                EntityType<?> entityType = null;
                if (g.contains("entityTypeId")) {
                    Identifier etId = Identifier.tryParse(g.getString("entityTypeId"));
                    if (etId != null && Registries.ENTITY_TYPE.containsId(etId)) {
                        entityType = Registries.ENTITY_TYPE.get(etId);
                    }
                }
                NbtCompound entityNbt = g.contains("entityNbt") ? g.getCompound("entityNbt") : new NbtCompound();
                double grabDistance = g.getDouble("grabDistance");
                float scaleX = g.getFloat("scaleX");
                float scaleY = g.getFloat("scaleY");
                float scaleZ = g.getFloat("scaleZ");
                int rotationSteps = g.getInt("rotationSteps");
                UUID scalingTargetUuid = g.containsUuid("scalingTargetUuid") ? g.getUuid("scalingTargetUuid") : null;
                // shapeBoxes: read new multi-box format; fall back to legacy single-box keys.
                List<Box> shapeBoxes;
                if (g.contains("shapeBoxes", NbtElement.LIST_TYPE)) {
                    NbtList sbList = g.getList("shapeBoxes", NbtElement.COMPOUND_TYPE);
                    shapeBoxes = new ArrayList<>(sbList.size());
                    for (int k = 0; k < sbList.size(); k++) {
                        NbtCompound sbc = sbList.getCompound(k);
                        shapeBoxes.add(new Box(sbc.getDouble("x1"), sbc.getDouble("y1"), sbc.getDouble("z1"),
                                               sbc.getDouble("x2"), sbc.getDouble("y2"), sbc.getDouble("z2")));
                    }
                } else {
                    // Legacy: single bounding box stored as sbMinX … sbMaxZ.
                    shapeBoxes = List.of(new Box(
                            g.getDouble("sbMinX"), g.getDouble("sbMinY"), g.getDouble("sbMinZ"),
                            g.getDouble("sbMaxX"), g.getDouble("sbMaxY"), g.getDouble("sbMaxZ")));
                }
                NbtList extraList = g.getList("extraBlocks", NbtElement.COMPOUND_TYPE);
                List<ExtraBlock> extraBlocks = new ArrayList<>();
                for (int j = 0; j < extraList.size(); j++) {
                    NbtCompound ec = extraList.getCompound(j);
                    Vec3i offset = new Vec3i(ec.getInt("offX"), ec.getInt("offY"), ec.getInt("offZ"));
                    BlockState ebState = Block.STATE_IDS.get(ec.getInt("stateId"));
                    boolean spawnDisplay = ec.getBoolean("spawnDisplay");
                    // shapeBoxes per extra block: new format or legacy single-box fallback.
                    List<Box> ebBoxes;
                    if (ec.contains("shapeBoxes", NbtElement.LIST_TYPE)) {
                        NbtList ebBoxList = ec.getList("shapeBoxes", NbtElement.COMPOUND_TYPE);
                        ebBoxes = new ArrayList<>(ebBoxList.size());
                        for (int k = 0; k < ebBoxList.size(); k++) {
                            NbtCompound ebc = ebBoxList.getCompound(k);
                            ebBoxes.add(new Box(ebc.getDouble("x1"), ebc.getDouble("y1"), ebc.getDouble("z1"),
                                                ebc.getDouble("x2"), ebc.getDouble("y2"), ebc.getDouble("z2")));
                        }
                    } else {
                        ebBoxes = List.of(new Box(
                                ec.getDouble("ebMinX"), ec.getDouble("ebMinY"), ec.getDouble("ebMinZ"),
                                ec.getDouble("ebMaxX"), ec.getDouble("ebMaxY"), ec.getDouble("ebMaxZ")));
                    }
                    if (ebState != null) {
                        extraBlocks.add(new ExtraBlock(offset, ebState, ebBoxes, spawnDisplay));
                    }
                }
                state.grabMap.put(uuid, new GrabData(type, blockState, entityType, entityNbt,
                        grabDistance, scaleX, scaleY, scaleZ, rotationSteps,
                        extraBlocks, shapeBoxes, scalingTargetUuid));
            } catch (Exception ex) {
                // Skip corrupt entries
            }
        }

        // barrierMap
        NbtList barriers = nbt.getList("barriers", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < barriers.size(); i++) {
            NbtCompound b = barriers.getCompound(i);
            try {
                UUID uuid = b.getUuid("uuid");
                NbtList posList = b.getList("positions", NbtElement.LONG_TYPE);
                List<BlockPos> positions = new ArrayList<>();
                for (int j = 0; j < posList.size(); j++) {
                    positions.add(BlockPos.fromLong(((NbtLong) posList.get(j)).longValue()));
                }
                state.barrierMap.put(uuid, positions);
            } catch (Exception ex) {
                // Skip corrupt entries
            }
        }

        // extraDisplayMap
        NbtList extras = nbt.getList("extras", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < extras.size(); i++) {
            NbtCompound e = extras.getCompound(i);
            try {
                UUID uuid = e.getUuid("uuid");
                NbtList uuidList = e.getList("uuids", NbtElement.COMPOUND_TYPE);
                List<UUID> uuids = new ArrayList<>();
                for (int j = 0; j < uuidList.size(); j++) {
                    uuids.add(uuidList.getCompound(j).getUuid("u"));
                }
                state.extraDisplayMap.put(uuid, uuids);
            } catch (Exception ex) {
                // Skip corrupt entries
            }
        }

        return state;
    }
}
