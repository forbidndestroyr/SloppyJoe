package com.sloppyjoe.client.renderer;

import com.sloppyjoe.entity.DomainOrbEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Renders a slowly-rotating dark cube for the DomainOrbEntity.
 */
@Environment(EnvType.CLIENT)
public class DomainOrbEntityRenderer extends EntityRenderer<DomainOrbEntity, EntityRenderState> {

    private static final Identifier TEXTURE =
            Identifier.of("minecraft", "textures/block/obsidian.png");

    public DomainOrbEntityRenderer(EntityRendererFactory.Context ctx) {
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

        matrices.push();

        // Pulsing scale — age not available from EntityRenderState directly, use a sine of time
        float pulse = 0.4f;
        matrices.scale(pulse, pulse, pulse);

        // Continuous rotation around Y axis using the render state's age
        float angle = (state.age) * 2.0f;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle * 0.5f));

        VertexConsumer consumer = vertexConsumers.getBuffer(
                net.minecraft.client.render.RenderLayer.getEntityCutoutNoCull(TEXTURE));

        drawCube(matrices, consumer, light);

        matrices.pop();
    }

    private static void drawCube(MatrixStack matrices, VertexConsumer consumer, int light) {
        var entry = matrices.peek();
        var matrix = entry.getPositionMatrix();
        int overlay = OverlayTexture.DEFAULT_UV;

        float s = 1.0f;

        // Bottom (Y-)
        consumer.vertex(matrix, -s, -s, -s).color(10, 10, 15, 220).texture(0f, 0f).overlay(overlay).light(light).normal(entry, 0f, -1f, 0f);
        consumer.vertex(matrix,  s, -s, -s).color(10, 10, 15, 220).texture(1f, 0f).overlay(overlay).light(light).normal(entry, 0f, -1f, 0f);
        consumer.vertex(matrix,  s, -s,  s).color(10, 10, 15, 220).texture(1f, 1f).overlay(overlay).light(light).normal(entry, 0f, -1f, 0f);
        consumer.vertex(matrix, -s, -s,  s).color(10, 10, 15, 220).texture(0f, 1f).overlay(overlay).light(light).normal(entry, 0f, -1f, 0f);
        // Top (Y+)
        consumer.vertex(matrix, -s,  s,  s).color(10, 10, 15, 220).texture(0f, 0f).overlay(overlay).light(light).normal(entry, 0f, 1f, 0f);
        consumer.vertex(matrix,  s,  s,  s).color(10, 10, 15, 220).texture(1f, 0f).overlay(overlay).light(light).normal(entry, 0f, 1f, 0f);
        consumer.vertex(matrix,  s,  s, -s).color(10, 10, 15, 220).texture(1f, 1f).overlay(overlay).light(light).normal(entry, 0f, 1f, 0f);
        consumer.vertex(matrix, -s,  s, -s).color(10, 10, 15, 220).texture(0f, 1f).overlay(overlay).light(light).normal(entry, 0f, 1f, 0f);
        // North (Z-)
        consumer.vertex(matrix,  s, -s, -s).color(10, 10, 15, 220).texture(0f, 1f).overlay(overlay).light(light).normal(entry, 0f, 0f, -1f);
        consumer.vertex(matrix, -s, -s, -s).color(10, 10, 15, 220).texture(1f, 1f).overlay(overlay).light(light).normal(entry, 0f, 0f, -1f);
        consumer.vertex(matrix, -s,  s, -s).color(10, 10, 15, 220).texture(1f, 0f).overlay(overlay).light(light).normal(entry, 0f, 0f, -1f);
        consumer.vertex(matrix,  s,  s, -s).color(10, 10, 15, 220).texture(0f, 0f).overlay(overlay).light(light).normal(entry, 0f, 0f, -1f);
        // South (Z+)
        consumer.vertex(matrix, -s, -s,  s).color(10, 10, 15, 220).texture(0f, 1f).overlay(overlay).light(light).normal(entry, 0f, 0f, 1f);
        consumer.vertex(matrix,  s, -s,  s).color(10, 10, 15, 220).texture(1f, 1f).overlay(overlay).light(light).normal(entry, 0f, 0f, 1f);
        consumer.vertex(matrix,  s,  s,  s).color(10, 10, 15, 220).texture(1f, 0f).overlay(overlay).light(light).normal(entry, 0f, 0f, 1f);
        consumer.vertex(matrix, -s,  s,  s).color(10, 10, 15, 220).texture(0f, 0f).overlay(overlay).light(light).normal(entry, 0f, 0f, 1f);
        // West (X-)
        consumer.vertex(matrix, -s, -s, -s).color(10, 10, 15, 220).texture(0f, 1f).overlay(overlay).light(light).normal(entry, -1f, 0f, 0f);
        consumer.vertex(matrix, -s, -s,  s).color(10, 10, 15, 220).texture(1f, 1f).overlay(overlay).light(light).normal(entry, -1f, 0f, 0f);
        consumer.vertex(matrix, -s,  s,  s).color(10, 10, 15, 220).texture(1f, 0f).overlay(overlay).light(light).normal(entry, -1f, 0f, 0f);
        consumer.vertex(matrix, -s,  s, -s).color(10, 10, 15, 220).texture(0f, 0f).overlay(overlay).light(light).normal(entry, -1f, 0f, 0f);
        // East (X+)
        consumer.vertex(matrix,  s, -s,  s).color(10, 10, 15, 220).texture(0f, 1f).overlay(overlay).light(light).normal(entry, 1f, 0f, 0f);
        consumer.vertex(matrix,  s, -s, -s).color(10, 10, 15, 220).texture(1f, 1f).overlay(overlay).light(light).normal(entry, 1f, 0f, 0f);
        consumer.vertex(matrix,  s,  s, -s).color(10, 10, 15, 220).texture(1f, 0f).overlay(overlay).light(light).normal(entry, 1f, 0f, 0f);
        consumer.vertex(matrix,  s,  s,  s).color(10, 10, 15, 220).texture(0f, 0f).overlay(overlay).light(light).normal(entry, 1f, 0f, 0f);
    }
}
