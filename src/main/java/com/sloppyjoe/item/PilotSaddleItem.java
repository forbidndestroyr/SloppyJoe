package com.sloppyjoe.item;

import com.sloppyjoe.entity.HorsePlaneEntity;
import com.sloppyjoe.entity.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class PilotSaddleItem extends Item {

    public PilotSaddleItem(Settings settings) {
        super(settings);
    }

    /**
     * Called from UseEntityCallback in SloppyJoeMod.
     * Right-clicking a vanilla horse with the pilot saddle converts it into a flying HorsePlaneEntity
     * and auto-mounts the player.
     */
    public static ActionResult handleEntityUse(PlayerEntity user, World world, Entity entity, Hand hand) {
        // Only apply to un-converted horses
        if (!(entity instanceof HorseEntity horse) || horse instanceof HorsePlaneEntity) {
            return ActionResult.PASS;
        }
        // Client fires the callback too — skip to avoid double execution
        if (world.isClient()) return ActionResult.SUCCESS;
        // Cannot convert a horse that already has a rider
        if (horse.hasPassengers()) return ActionResult.PASS;

        ServerWorld serverWorld = (ServerWorld) world;

        HorsePlaneEntity plane = ModEntities.HORSE_PLANE.create(serverWorld, SpawnReason.COMMAND);
        if (plane == null) return ActionResult.FAIL;

        // Inherit position, facing, and tame state
        plane.setPosition(horse.getPos());
        plane.setYaw(horse.getYaw());
        plane.makeTame(); // always tame the converted horse plane

        // Inherit custom name if the horse had one
        if (horse.hasCustomName()) {
            plane.setCustomName(horse.getCustomName());
        }

        // Replace the old horse with the flying horse
        horse.discard();
        serverWorld.spawnEntity(plane);

        // Consume one saddle in survival
        if (!user.isCreative()) {
            user.getStackInHand(hand).decrement(1);
        }

        // Auto-mount the player who applied the saddle
        user.startRiding(plane);
        return ActionResult.SUCCESS;
    }
}
