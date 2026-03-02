package com.sloppyjoe.item;

import com.sloppyjoe.entity.HorsePlaneEntity;
import com.sloppyjoe.entity.ModEntities;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class HorsePlaneTicketItem extends Item {

    public HorsePlaneTicketItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity user = context.getPlayer();
        if (user == null || world.isClient()) return ActionResult.PASS;

        BlockPos spawnPos = context.getBlockPos().offset(context.getSide());

        if (world instanceof ServerWorld serverWorld) {
            HorsePlaneEntity plane = ModEntities.HORSE_PLANE.create(serverWorld, SpawnReason.COMMAND);
            if (plane != null) {
                // Centre on the block face, sitting on top of it.
                plane.setPosition(
                        spawnPos.getX() + 0.5,
                        spawnPos.getY(),
                        spawnPos.getZ() + 0.5
                );
                serverWorld.spawnEntity(plane);

                if (!user.isCreative()) {
                    context.getStack().decrement(1);
                }
                return ActionResult.SUCCESS;
            }
        }

        return ActionResult.PASS;
    }
}
