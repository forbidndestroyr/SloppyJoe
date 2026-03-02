package com.sloppyjoe.item;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class GlassJarItem extends Item {

    /**
     * Hollow 3×4×3 glass cage (floor + 4 walls × 2 rows + ceiling).
     * Interior is the 1×2×1 space at offset (1,1,1) and (1,2,1).
     * Mob spawns at (origin + 1.5, 1.0, 1.5) — centered on the floor of the interior.
     */
    private static final List<Vec3i> CAGE_OFFSETS;
    static {
        List<Vec3i> offsets = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 3; z++) {
                    if (y == 0 || y == 3 || x == 0 || x == 2 || z == 0 || z == 2) {
                        offsets.add(new Vec3i(x, y, z));
                    }
                }
            }
        }
        CAGE_OFFSETS = List.copyOf(offsets);
    }

    public GlassJarItem(Settings settings) {
        super(settings);
    }

    public static boolean isFilled(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.copyNbt().contains("EntityType");
    }

    @Override
    public Text getName(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data != null) {
            NbtCompound nbt = data.copyNbt();
            if (nbt.contains("EntityType")) {
                Identifier typeId = Identifier.tryParse(nbt.getString("EntityType"));
                if (typeId != null && Registries.ENTITY_TYPE.containsId(typeId)) {
                    EntityType<?> type = Registries.ENTITY_TYPE.get(typeId);
                    return Text.translatable("item.sloppyjoe.glass_jar.filled",
                            type.getName());
                }
            }
        }
        return super.getName(stack);
    }

    // ---- Called by UseEntityCallback in SloppyJoeMod ----

    public static ActionResult handleEntityUse(PlayerEntity user, World world,
                                               Entity entity, Hand hand) {
        if (world.isClient()) return ActionResult.PASS;
        if (entity instanceof PlayerEntity) return ActionResult.PASS;

        ItemStack stack = user.getStackInHand(hand);
        if (isFilled(stack)) {
            user.sendMessage(Text.literal("\u00a77The jar is already full!"), true);
            return ActionResult.PASS;
        }

        NbtCompound entityNbt = new NbtCompound();
        entity.writeNbt(entityNbt);
        Identifier typeId = Registries.ENTITY_TYPE.getId(entity.getType());

        NbtCompound data = new NbtCompound();
        data.putString("EntityType", typeId.toString());
        data.put("EntityNbt", entityNbt);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));

        entity.discard();
        user.sendMessage(Text.literal(
                "\u00a77Captured \u00a7f" + entity.getType().getName().getString()
                        + "\u00a77 in the jar!"), true);
        return ActionResult.SUCCESS;
    }

    // ---- Right-click block: place cage + release mob ----

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity user = context.getPlayer();
        if (user == null || world.isClient()) return ActionResult.PASS;

        ItemStack stack = context.getStack();
        if (!isFilled(stack)) return ActionResult.PASS;

        NbtComponent dataComp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (dataComp == null) return ActionResult.PASS;
        NbtCompound data = dataComp.copyNbt();

        Identifier typeId = Identifier.tryParse(data.getString("EntityType"));
        if (typeId == null || !Registries.ENTITY_TYPE.containsId(typeId)) return ActionResult.PASS;

        EntityType<?> type = Registries.ENTITY_TYPE.get(typeId);
        NbtCompound entityNbt = data.getCompound("EntityNbt");

        BlockPos origin = context.getBlockPos().offset(context.getSide());

        // Place glass cage — only into air blocks so we never overwrite player builds.
        for (Vec3i offset : CAGE_OFFSETS) {
            BlockPos bp = origin.add(offset);
            if (world.getBlockState(bp).isAir()) {
                world.setBlockState(bp, Blocks.GLASS.getDefaultState(), Block.NOTIFY_ALL);
            }
        }

        // Spawn mob centered in the interior (1.5 offset centers the 1-block interior in XZ).
        Entity spawned = type.create(world, SpawnReason.COMMAND);
        if (spawned != null) {
            spawned.readNbt(entityNbt.copy());
            spawned.setPosition(Vec3d.of(origin).add(1.5, 1.0, 1.5));
            world.spawnEntity(spawned);
        }

        // Jar is now empty.
        stack.remove(DataComponentTypes.CUSTOM_DATA);
        return ActionResult.SUCCESS;
    }
}
