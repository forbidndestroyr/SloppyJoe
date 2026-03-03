package com.sloppyjoe.client;

import com.sloppyjoe.client.cutscene.CutsceneManager;
import com.sloppyjoe.item.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Renders a white gauze blindfold strip on any player wearing the Gojo Blindfold helmet item.
 *
 * During CUTSCENE_LIFT the blindfold slides upward (in head-local space) to reveal
 * the glowing blue eyes underneath. It stays raised for the remainder of the cutscene.
 *
 * Texture: assets/sloppyjoe/textures/entity/gojo_blindfold_overlay.png
 *   64×64 RGBA PNG in player-skin UV format; white band at the eye rows (y=11-13),
 *   transparent everywhere else.
 */
public class GojoBlindfoldRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier BLINDFOLD_TEXTURE =
            Identifier.of("sloppyjoe", "textures/entity/gojo_blindfold_overlay.png");

    private static final int BLINDFOLD_COLOR = 0xFFF5F0E8;

    // How far the blindfold slides upward (in model units, negative = up in head-local space)
    private static final float LIFT_AMOUNT = 0.45f;

    public GojoBlindfoldRenderer(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        var entity = client.world.getEntityById(state.id);
        if (!(entity instanceof PlayerEntity player)) return;
        if (!player.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.GOJO_BLINDFOLD)) return;

        PlayerEntityModel model = getContextModel();
        matrices.push();

        model.getRootPart().rotate(matrices);
        model.getHead().rotate(matrices);

        // Compute vertical slide offset based on cutscene phase
        float slideY = computeSlide();
        if (slideY != 0f) {
            matrices.translate(0f, slideY, 0f);
        }

        VertexConsumer consumer = vertexConsumers.getBuffer(
                RenderLayer.getEntityTranslucent(BLINDFOLD_TEXTURE));
        model.getHead().render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, BLINDFOLD_COLOR);

        matrices.pop();
    }

    /**
     * Returns the Y-translation offset for the blindfold overlay in head-local space.
     * Negative Y = upward (head-local space after getRootPart/getHead rotate).
     */
    private static float computeSlide() {
        if (!CutsceneManager.isActive()) return 0f;
        return switch (CutsceneManager.getPhase()) {
            case CUTSCENE_LIFT -> -LIFT_AMOUNT * easeOut(CutsceneManager.getPhaseProgress());
            // Keep raised for remaining cutscene phases
            case CUTSCENE_EYES, CUTSCENE_ZOOM_OUT, CUTSCENE_VOICE -> -LIFT_AMOUNT;
            default -> 0f;
        };
    }

    private static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t);
    }
}
