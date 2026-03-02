package com.sloppyjoe;

import com.sloppyjoe.ModItemGroup;
import com.sloppyjoe.block.ModBlocks;
import com.sloppyjoe.update.AutoUpdater;
import com.sloppyjoe.entity.ModEntities;
import com.sloppyjoe.world.gen.structure.ModStructures;
import com.sloppyjoe.item.GlassJarItem;
import com.sloppyjoe.item.ModItems;
import com.sloppyjoe.item.PerspectiveLensItem;
import com.sloppyjoe.item.PilotSaddleItem;
import com.sloppyjoe.network.CancelGrabPayload;
import com.sloppyjoe.network.GlassesEquipPayload;
import com.sloppyjoe.network.GrabSyncPayload;
import com.sloppyjoe.network.ReleaseSyncPayload;
import com.sloppyjoe.network.RotatePayload;
import com.sloppyjoe.network.ScaleAdjustPayload;
import com.sloppyjoe.villager.ModVillagers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.sloppyjoe.entity.HorsePlaneEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SloppyJoeMod implements ModInitializer {

    public static final String MOD_ID = "sloppyjoe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        AutoUpdater.cleanupDisabledJars();
        ModBlocks.initialize();
        ModItems.initialize();
        ModEntities.initialize();
        ModVillagers.initialize();
        ModStructures.initialize();
        ModItemGroup.initialize();

        // Register entity attributes
        FabricDefaultAttributeRegistry.register(ModEntities.HORSE_PLANE, HorsePlaneEntity.createHorsePlaneAttributes());

        // Register network payload types
        PayloadTypeRegistry.playS2C().register(GrabSyncPayload.ID, GrabSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReleaseSyncPayload.ID, ReleaseSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GlassesEquipPayload.ID, GlassesEquipPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CancelGrabPayload.ID, CancelGrabPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ScaleAdjustPayload.ID, ScaleAdjustPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RotatePayload.ID, RotatePayload.CODEC);

        // Cancel grab
        ServerPlayNetworking.registerGlobalReceiver(CancelGrabPayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    PerspectiveLensItem.cancelGrab(context.server(), context.player().getUuid());
                    ServerPlayNetworking.send(context.player(), new ReleaseSyncPayload());
                    context.server().getPlayerManager().sendToAll(
                            ServerPlayNetworking.createS2CPacket(
                                    new GlassesEquipPayload(context.player().getUuid(), false)));
                }));

        // Scale +/- with axis
        ServerPlayNetworking.registerGlobalReceiver(ScaleAdjustPayload.ID, (payload, context) ->
                context.server().execute(() ->
                        PerspectiveLensItem.adjustScale(context.server(),
                                context.player().getUuid(), payload.increase(), payload.axis())));

        // Rotate
        ServerPlayNetworking.registerGlobalReceiver(RotatePayload.ID, (payload, context) ->
                context.server().execute(() ->
                        PerspectiveLensItem.adjustRotation(context.server(),
                                context.player().getUuid(), payload.clockwise())));

        // Right-click on any entity type
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand == Hand.MAIN_HAND && player.getStackInHand(hand).isOf(ModItems.PERSPECTIVE_LENS)) {
                return PerspectiveLensItem.handleEntityUse(player, world, entity, hand);
            }
            if (hand == Hand.MAIN_HAND && player.getStackInHand(hand).isOf(ModItems.GLASS_JAR)) {
                return GlassJarItem.handleEntityUse(player, world, entity, hand);
            }
            if (hand == Hand.MAIN_HAND && player.getStackInHand(hand).isOf(ModItems.PILOT_SADDLE)) {
                return PilotSaddleItem.handleEntityUse(player, world, entity, hand);
            }
            return ActionResult.PASS;
        });

        LOGGER.info("Sloppy Joe Mod loaded!");
    }
}
