package com.sloppyjoe.client;

import com.sloppyjoe.client.cutscene.CutsceneManager;
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
import net.minecraft.util.Identifier;

/**
 * Renders an emissive blue glow over the local player's eye area during the
 * Gojo domain cutscene (CUTSCENE_LIFT → CUTSCENE_EYES → CUTSCENE_ZOOM_OUT → CUTSCENE_VOICE).
 *
 * Uses the same head-tracking transforms as PerspectiveLensHeadRenderer so the
 * overlay locks perfectly to the head and follows head rotation.
 *
 * Texture: textures/cutscene/gojo_eye_glow.png — 64×64 with white pixels in the
 * eye UV region of the skin, transparent everywhere else.  The blue tint is applied
 * via the vertex color passed to ModelPart.render().
 */
public class DomainEyeGlowRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier EYE_GLOW_TEXTURE =
            Identifier.of("sloppyjoe", "textures/cutscene/gojo_eye_glow.png");

    public DomainEyeGlowRenderer(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        // Only render for the local player (the caster)
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (state.id != client.player.getId()) return;

        float intensity = glowIntensity();
        if (intensity <= 0f) return;

        // 0xAADDFF blended with given alpha — bright electric blue
        int alpha = Math.min(255, (int)(intensity * 255));
        int color = (alpha << 24) | 0x00AADDFF;

        PlayerEntityModel model = getContextModel();

        matrices.push();

        // Track the head exactly — mirrors PerspectiveLensHeadRenderer.
        model.getRootPart().rotate(matrices);
        model.getHead().rotate(matrices);

        // Render the head model part with our emissive eye texture.
        // RenderLayer.getEntityTranslucentEmissive ignores the light map (always full brightness),
        // which makes the eyes look like they are self-illuminated.
        VertexConsumer consumer = vertexConsumers.getBuffer(
                RenderLayer.getEntityTranslucentEmissive(EYE_GLOW_TEXTURE));
        model.getHead().render(matrices, consumer, 15728880, OverlayTexture.DEFAULT_UV, color);

        matrices.pop();
    }

    // -----------------------------------------------------------------------

    private static float glowIntensity() {
        if (!CutsceneManager.isActive()) return 0f;
        return switch (CutsceneManager.getPhase()) {
            case CUTSCENE_LIFT     -> CutsceneManager.getPhaseProgress() * 0.9f;
            case CUTSCENE_EYES     -> 1.0f;
            case CUTSCENE_ZOOM_OUT -> (1f - CutsceneManager.getPhaseProgress()) * 0.8f;
            case CUTSCENE_VOICE    -> 0.65f;
            default                -> 0f;
        };
    }
}
