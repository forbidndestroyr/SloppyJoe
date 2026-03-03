package com.sloppyjoe.client.renderer;

import com.sloppyjoe.entity.HollowPurpleEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Three nested rotating cubes for the Hollow Purple projectile.
 *
 * Layer 1 — outer glow:  deep purple, semi-transparent, slow rotation.
 * Layer 2 — core sphere: bright purple, medium opacity, medium rotation.
 * Layer 3 — hot centre:  near-white magenta, fast counter-rotation.
 *
 * All layers use EntityTranslucent so alpha is respected.
 */
@Environment(EnvType.CLIENT)
public class HollowPurpleEntityRenderer extends EntityRenderer<HollowPurpleEntity, EntityRenderState> {

    // Obsidian texture re-used; vertex colors drive the purple tinting.
    private static final Identifier TEXTURE =
            Identifier.of("minecraft", "textures/block/obsidian.png");

    public HollowPurpleEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void render(EntityRenderState state, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        super.render(state, matrices, vertexConsumers, light);

        float age   = state.age;
        float pulse = (float)(Math.sin(age * 0.18) * 0.07 + 1.0); // gentle size pulse

        VertexConsumer consumer = vertexConsumers.getBuffer(
                RenderLayer.getEntityTranslucent(TEXTURE));

        // Layer 1 — outer glow (deep purple, transparent)
        matrices.push();
        matrices.scale(0.90f * pulse, 0.90f * pulse, 0.90f * pulse);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 1.8f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(age * 0.9f));
        drawCube(matrices, consumer, light, 110, 0, 200, 70);
        matrices.pop();

        // Layer 2 — core (bright purple, mostly opaque)
        matrices.push();
        matrices.scale(0.65f * pulse, 0.65f * pulse, 0.65f * pulse);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-age * 3.0f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(age * 1.5f));
        drawCube(matrices, consumer, light, 175, 50, 255, 190);
        matrices.pop();

        // Layer 3 — hot centre (near-white magenta, solid)
        matrices.push();
        matrices.scale(0.35f * pulse, 0.35f * pulse, 0.35f * pulse);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 5.5f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-age * 3.0f));
        drawCube(matrices, consumer, light, 240, 180, 255, 240);
        matrices.pop();
    }

    private static void drawCube(MatrixStack matrices, VertexConsumer consumer,
                                  int light, int r, int g, int b, int a) {
        var entry   = matrices.peek();
        var matrix  = entry.getPositionMatrix();
        int overlay = OverlayTexture.DEFAULT_UV;
        float s = 1.0f;

        // Bottom (Y-)
        consumer.vertex(matrix, -s, -s, -s).color(r,g,b,a).texture(0f,0f).overlay(overlay).light(light).normal(entry, 0f,-1f,0f);
        consumer.vertex(matrix,  s, -s, -s).color(r,g,b,a).texture(1f,0f).overlay(overlay).light(light).normal(entry, 0f,-1f,0f);
        consumer.vertex(matrix,  s, -s,  s).color(r,g,b,a).texture(1f,1f).overlay(overlay).light(light).normal(entry, 0f,-1f,0f);
        consumer.vertex(matrix, -s, -s,  s).color(r,g,b,a).texture(0f,1f).overlay(overlay).light(light).normal(entry, 0f,-1f,0f);
        // Top (Y+)
        consumer.vertex(matrix, -s,  s,  s).color(r,g,b,a).texture(0f,0f).overlay(overlay).light(light).normal(entry, 0f,1f,0f);
        consumer.vertex(matrix,  s,  s,  s).color(r,g,b,a).texture(1f,0f).overlay(overlay).light(light).normal(entry, 0f,1f,0f);
        consumer.vertex(matrix,  s,  s, -s).color(r,g,b,a).texture(1f,1f).overlay(overlay).light(light).normal(entry, 0f,1f,0f);
        consumer.vertex(matrix, -s,  s, -s).color(r,g,b,a).texture(0f,1f).overlay(overlay).light(light).normal(entry, 0f,1f,0f);
        // North (Z-)
        consumer.vertex(matrix,  s, -s, -s).color(r,g,b,a).texture(0f,1f).overlay(overlay).light(light).normal(entry, 0f,0f,-1f);
        consumer.vertex(matrix, -s, -s, -s).color(r,g,b,a).texture(1f,1f).overlay(overlay).light(light).normal(entry, 0f,0f,-1f);
        consumer.vertex(matrix, -s,  s, -s).color(r,g,b,a).texture(1f,0f).overlay(overlay).light(light).normal(entry, 0f,0f,-1f);
        consumer.vertex(matrix,  s,  s, -s).color(r,g,b,a).texture(0f,0f).overlay(overlay).light(light).normal(entry, 0f,0f,-1f);
        // South (Z+)
        consumer.vertex(matrix, -s, -s,  s).color(r,g,b,a).texture(0f,1f).overlay(overlay).light(light).normal(entry, 0f,0f,1f);
        consumer.vertex(matrix,  s, -s,  s).color(r,g,b,a).texture(1f,1f).overlay(overlay).light(light).normal(entry, 0f,0f,1f);
        consumer.vertex(matrix,  s,  s,  s).color(r,g,b,a).texture(1f,0f).overlay(overlay).light(light).normal(entry, 0f,0f,1f);
        consumer.vertex(matrix, -s,  s,  s).color(r,g,b,a).texture(0f,0f).overlay(overlay).light(light).normal(entry, 0f,0f,1f);
        // West (X-)
        consumer.vertex(matrix, -s, -s, -s).color(r,g,b,a).texture(0f,1f).overlay(overlay).light(light).normal(entry,-1f,0f,0f);
        consumer.vertex(matrix, -s, -s,  s).color(r,g,b,a).texture(1f,1f).overlay(overlay).light(light).normal(entry,-1f,0f,0f);
        consumer.vertex(matrix, -s,  s,  s).color(r,g,b,a).texture(1f,0f).overlay(overlay).light(light).normal(entry,-1f,0f,0f);
        consumer.vertex(matrix, -s,  s, -s).color(r,g,b,a).texture(0f,0f).overlay(overlay).light(light).normal(entry,-1f,0f,0f);
        // East (X+)
        consumer.vertex(matrix,  s, -s,  s).color(r,g,b,a).texture(0f,1f).overlay(overlay).light(light).normal(entry, 1f,0f,0f);
        consumer.vertex(matrix,  s, -s, -s).color(r,g,b,a).texture(1f,1f).overlay(overlay).light(light).normal(entry, 1f,0f,0f);
        consumer.vertex(matrix,  s,  s, -s).color(r,g,b,a).texture(1f,0f).overlay(overlay).light(light).normal(entry, 1f,0f,0f);
        consumer.vertex(matrix,  s,  s,  s).color(r,g,b,a).texture(0f,0f).overlay(overlay).light(light).normal(entry, 1f,0f,0f);
    }
}
