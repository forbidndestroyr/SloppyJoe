package com.sloppyjoe.client;

import com.sloppyjoe.entity.HorsePlaneEntity;
import com.sloppyjoe.item.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.math.RotationAxis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Renders the combined pilot helmet (hat + goggles) on a player's head when
 * they are riding a HorsePlaneEntity.  Animates on/off with a slide-up effect.
 *
 * PILOT_IDS is rebuilt each client tick by SloppyJoeModClient.
 */
public class HorsePlanePilotRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    /** Player entity IDs currently riding a HorsePlane, updated each tick. */
    public static final Set<Integer> PILOT_IDS = new HashSet<>();

    /** 0.0 = fully removed, 1.0 = fully worn.  Pruned when idle at 0. */
    private static final Map<Integer, Float> equipProgress = new HashMap<>();

    public HorsePlanePilotRenderer(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    // ---- called every tick from SloppyJoeModClient ----

    /** Rebuilds PILOT_IDS from all visible HorsePlane passengers. */
    public static void updatePilots(net.minecraft.client.world.ClientWorld world) {
        Set<Integer> nowRiding = new HashSet<>();
        if (world != null) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof HorsePlaneEntity plane) {
                    for (Entity passenger : plane.getPassengerList()) {
                        nowRiding.add(passenger.getId());
                    }
                }
            }
        }

        // Start / stop animations for players that mounted or dismounted
        for (int id : nowRiding) {
            if (!PILOT_IDS.contains(id)) {
                equipProgress.putIfAbsent(id, 0.0f);
            }
        }
        PILOT_IDS.clear();
        PILOT_IDS.addAll(nowRiding);
    }

    /** Advances equip/unequip animations; called once per client tick. */
    public static void tickAll() {
        equipProgress.entrySet().removeIf(entry -> {
            int id = entry.getKey();
            boolean on = PILOT_IDS.contains(id);
            float p = entry.getValue();
            p = on ? Math.min(1.0f, p + 0.15f) : Math.max(0.0f, p - 0.15f);
            entry.setValue(p);
            return !on && p == 0.0f;   // prune once fully removed
        });
    }

    // ---- render ----

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        Float progress = equipProgress.get(state.id);
        if (progress == null || progress == 0.0f) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        PlayerEntityModel model = getContextModel();

        matrices.push();

        // -- head tracking (same as PerspectiveLensHeadRenderer) --
        model.getRootPart().rotate(matrices);
        model.getHead().rotate(matrices);

        // Move to eye level, face forward, re-flip Y.
        // After this: +Y = world up, -Z = toward face.
        float slideOffset = 0.30f * (1.0f - progress);   // 0 at full equip
        float tiltDegrees = -30.0f * (1.0f - progress);  // 0 at full equip

        matrices.translate(0.0f, -0.25f + slideOffset, 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
        matrices.scale(0.625f, -0.625f, -0.625f);

        // Apply slide-on tilt animation
        if (tiltDegrees != 0.0f) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tiltDegrees));
        }

        // Push to face surface (~0.4 toward the face, same as PerspectiveLensHeadRenderer)
        matrices.translate(0.0f, 0.0f, -0.4f);

        client.getItemRenderer().renderItem(
                new ItemStack(ModItems.PILOT_HELMET),
                ModelTransformationMode.NONE,
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vertexConsumers,
                client.world,
                0
        );

        matrices.pop();
    }
}
