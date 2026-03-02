package com.sloppyjoe.client;

import com.sloppyjoe.item.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.HorseEntityModel;
import net.minecraft.client.render.entity.state.LivingHorseEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.math.RotationAxis;

/**
 * Renders the pilot saddle (chair) on the horse's back and aviation goggles near its head.
 *
 * Coordinate system for the saddle (no getRootPart call — entity model space):
 *   Origin = TOP of the horse bounding box  (LivingEntityRenderer applies scale(-1,-1,1))
 *   +Y     = DOWN (toward feet)
 *   -Z     = horse's forward (nose) direction
 *   +Z     = horse's rearward (tail) direction
 *
 * Evidence: with Z-180° + translate(0, 0.40, -0.25) the JOYSTICK (model Y=4-10) was visible
 * at feature Y=0.119-0.287, confirming Y=0 is near the top and +Y is downward.
 * The SEAT (model Y=0-4 → feature Y=0.344-0.40) was clipped by the horse mesh.
 * Fix: raise translate Y to 0.35 so the seat bottom is 0.05 blocks above the back surface.
 *
 * Goggles use getRootPart().rotate() + head_parts.rotate() — a different coordinate space.
 */
public class HorsePlaneFeatureRenderer
        extends FeatureRenderer<LivingHorseEntityRenderState, HorseEntityModel> {

    public HorsePlaneFeatureRenderer(
            FeatureRendererContext<LivingHorseEntityRenderState, HorseEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       LivingHorseEntityRenderState state, float limbAngle, float limbDistance) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        // -------- pilot saddle (chair) on the horse's back --------
        matrices.push();
        matrices.translate(0.00, -0.60, -0.20);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
        matrices.scale(1.3f, 1.3f, 1.3f);
        client.getItemRenderer().renderItem(
                new ItemStack(ModItems.PILOT_SADDLE),
                ModelTransformationMode.NONE,
                light,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vertexConsumers,
                client.world,
                0
        );
        matrices.pop();

        // -------- aviation goggles on the horse's snout --------
        // Aviation goggles model: lens elements at Z=13-16 (north face at Z=13 faces -Z = nose).
        // At scale(0.35), model unit = 0.022 blocks.  Z=13 offset = +0.284 toward tail.
        // translate Z=-0.44 puts the lens face (Z=13) at 0.44-0.284 = 0.156 toward nose.
        // Y=0.20 puts us 0.2 below the bounding-box top ≈ snout/eye level.
        matrices.push();
        HorseEntityModel model = getContextModel();
        model.getRootPart().rotate(matrices);
        model.getRootPart().getChild("head_parts").rotate(matrices);
        matrices.translate(0.0, -0.65, -0.44);
        matrices.scale(0.35f, 0.35f, 0.35f);
        client.getItemRenderer().renderItem(
                new ItemStack(ModItems.AVIATION_GOGGLES),
                ModelTransformationMode.NONE,
                light,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vertexConsumers,
                client.world,
                0
        );
        matrices.pop();
    }
}
