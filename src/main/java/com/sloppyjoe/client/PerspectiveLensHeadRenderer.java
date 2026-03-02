package com.sloppyjoe.client;

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
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.math.RotationAxis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PerspectiveLensHeadRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    // Keyed by entity integer ID (available as PlayerEntityRenderState.id)
    private static final Map<Integer, Float> equipProgress = new HashMap<>();
    private static final Set<Integer> equipped = new HashSet<>();

    public PerspectiveLensHeadRenderer(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    public static void setEquipped(int entityId, boolean on) {
        if (on) {
            equipped.add(entityId);
            equipProgress.putIfAbsent(entityId, 0.0f);
        } else {
            equipped.remove(entityId);
            // Keep in equipProgress so the take-off animation can finish; tickAll() prunes at 0
        }
    }

    public static void tickAll() {
        equipProgress.entrySet().removeIf(entry -> {
            int id = entry.getKey();
            boolean on = equipped.contains(id);
            float p = entry.getValue();
            p = on ? Math.min(1.0f, p + 0.15f) : Math.max(0.0f, p - 0.15f);
            entry.setValue(p);
            return !on && p == 0.0f;
        });
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        Float progress = equipProgress.get(state.id);
        if (progress == null || progress == 0.0f) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        PlayerEntityModel model = getContextModel();
        ItemStack stack = new ItemStack(ModItems.PERSPECTIVE_LENS);

        matrices.push();

        // Step 1 — head tracking.
        // The entity renderer has already applied scale(-1,-1,1) and translated the origin to
        // approximately the head pivot.  getRootPart().rotate() + getHead().rotate() add the
        // head's current yaw/pitch on top, exactly as HeadFeatureRenderer does it.
        model.getRootPart().rotate(matrices);
        model.getHead().rotate(matrices);

        // Step 2 — replicate HeadFeatureRenderer.translate():
        //   translate(0, -0.25, 0)  — in flipped-Y space, negative = upward; moves to eye level
        //   rotate Y 180°           — glasses face forward (player looks in -Z; model faces +Z)
        //   scale(0.625,-0.625,-0.625) — re-flip Y/Z back to normal orientation for the item
        //
        // Animation: start 0.3 units below face (positive = downward in flipped space)
        //            and tilt 30° back (X rotation) so they "slide up" onto the face.
        float slideOffset = 0.3f * (1.0f - progress);   // 0 at full progress, +0.3 at start
        float tiltDegrees = -30.0f * (1.0f - progress); // 0 at full progress, -30° at start

        matrices.translate(0.0f, -0.25f + slideOffset, 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
        matrices.scale(0.625f, -0.625f, -0.625f);

        // Step 3 — apply tilt animation now that we're in item-oriented space (Y=up, Z=forward)
        if (tiltDegrees != 0.0f) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tiltDegrees));
        }

        // Step 4 — push glasses onto the face surface.
        // The head pivot is at the center of the head model; the face is 4 pixels (0.25 blocks)
        // forward.  After scale(0.625, -0.625, -0.625) the Z axis is negated, so -Z in item space
        // points toward the face.  0.25 entity-blocks / 0.625 = 0.4 units in -Z.
        matrices.translate(0.0f, 0.0f, -0.4f);

        // Step 5 — render.  NONE mode means no additional display transform is applied;
        // our manual transforms above fully position the item.
        client.getItemRenderer().renderItem(
                stack,
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
