package com.sloppyjoe.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sloppyjoe.network.GrabSyncPayload;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public class PerspectiveLensGhostRenderer {

    private static final float SCALE_STEP = 1.25f;
    private static final float SCALE_MIN  = 0.05f;
    private static final float SCALE_MAX  = 50.0f;
    private static final float GHOST_DIST = 2.5f;

    @Nullable
    private static GrabSyncPayload currentGrab = null;
    private static float scaleX        = 1f;
    private static float scaleY        = 1f;
    private static float scaleZ        = 1f;
    private static int   rotationSteps = 0;
    private static int   scaleAxis     = 0; // 0=uniform, 1=X, 2=Y, 3=Z

    public static void setGrab(GrabSyncPayload payload) {
        currentGrab    = payload;
        scaleX         = 1f;
        scaleY         = 1f;
        scaleZ         = 1f;
        rotationSteps  = 0;
        scaleAxis      = 0;
    }

    public static void clearGrab() {
        currentGrab    = null;
        scaleX         = 1f;
        scaleY         = 1f;
        scaleZ         = 1f;
        rotationSteps  = 0;
        scaleAxis      = 0;
    }

    public static boolean hasGrab() {
        return currentGrab != null;
    }

    public static float getGrabDistance() {
        return currentGrab != null ? (float) currentGrab.grabDistance() : 1.0f;
    }

    /** Returns scaleY as the representative "multiplier" for action bar display. */
    public static float getScaleMultiplier() {
        return scaleY;
    }

    public static float getScaleX() { return scaleX; }
    public static float getScaleY() { return scaleY; }
    public static float getScaleZ() { return scaleZ; }
    public static int   getRotationSteps() { return rotationSteps; }
    public static int   getScaleAxis() { return scaleAxis; }

    public static void setScaleAxis(int axis) {
        scaleAxis = axis;
    }

    public static void adjustScale(boolean increase, int axis) {
        float factor = increase ? SCALE_STEP : 1f / SCALE_STEP;
        if (axis == 0 || axis == 1) scaleX = clamp(scaleX * factor);
        if (axis == 0 || axis == 2) scaleY = clamp(scaleY * factor);
        if (axis == 0 || axis == 3) scaleZ = clamp(scaleZ * factor);
    }

    public static void adjustRotation(boolean clockwise) {
        rotationSteps = (rotationSteps + (clockwise ? 1 : 7)) % 8;
    }

    private static float clamp(float v) {
        return Math.max(SCALE_MIN, Math.min(SCALE_MAX, v));
    }

    /** Returns the current crosshair hit distance, or defaultDist when not hitting anything. */
    public static float getCrosshairDistance(MinecraftClient client, float defaultDist) {
        if (client.player == null || client.crosshairTarget == null) return defaultDist;
        HitResult hit = client.crosshairTarget;
        if (hit.getType() == HitResult.Type.MISS) return defaultDist;
        float d = (float) client.player.getEyePos().distanceTo(hit.getPos());
        return Math.max(0.5f, d);
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            GrabSyncPayload grab = currentGrab;
            MinecraftClient client = MinecraftClient.getInstance();
            if (grab == null || client.player == null || client.world == null) return;
            if (!client.options.getPerspective().isFirstPerson()) return;

            // Skip ghost rendering for PLAYER_SCALE grabs
            Identifier grabTypeId = grab.entityTypeId();
            if (!grab.isBlock() && grabTypeId != null
                    && grabTypeId.equals(Registries.ENTITY_TYPE.getId(EntityType.PLAYER))) {
                return;
            }

            MatrixStack matrices = context.matrixStack();
            if (matrices == null) return;

            float grabDist = (float) grab.grabDistance();

            // Base visual scale: proportional to grab distance, capped at 1.0.
            float baseScale = Math.min(GHOST_DIST / grabDist, 1.0f);
            // Hard cap so extreme multiplier values don't create a screen-filling ghost.
            float vx = Math.min(baseScale * scaleX, 2.0f);
            float vy = Math.min(baseScale * scaleY, 2.0f);
            float vz = Math.min(baseScale * scaleZ, 2.0f);

            VertexConsumerProvider.Immediate vertexConsumers =
                    client.getBufferBuilders().getEntityVertexConsumers();

            matrices.push();

            // Ghost is always visible, renders over world geometry.
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            // Enter camera space and push to fixed distance.
            matrices.multiply(context.camera().getRotation());
            matrices.translate(0.0, 0.0, -GHOST_DIST);
            matrices.scale(vx, vy, vz);
            // Apply Y rotation
            matrices.multiply(new Quaternionf().rotateY((float) Math.toRadians(rotationSteps * 45f)));

            if (grab.isBlock()) {
                BlockState state = Block.STATE_IDS.get(grab.blockStateId());
                if (state != null) {
                    ItemStack stack = new ItemStack(state.getBlock());
                    if (!stack.isEmpty()) {
                        RenderSystem.setShaderColor(0.7f, 0.85f, 1.0f, 0.6f);
                        client.getItemRenderer().renderItem(
                                stack,
                                ModelTransformationMode.FIXED,
                                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                                OverlayTexture.DEFAULT_UV,
                                matrices,
                                vertexConsumers,
                                client.world,
                                0
                        );
                        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                    }
                }
            } else {
                Identifier typeId = grab.entityTypeId();
                if (typeId != null && Registries.ENTITY_TYPE.containsId(typeId)) {
                    EntityType<?> entityType = Registries.ENTITY_TYPE.get(typeId);
                    Entity tempEntity = entityType.create(client.world, SpawnReason.COMMAND);
                    if (tempEntity != null) {
                        tempEntity.readNbt(grab.entityNbt());
                        RenderSystem.setShaderColor(0.7f, 0.85f, 1.0f, 0.6f);
                        client.getEntityRenderDispatcher().render(
                                tempEntity,
                                0.0, 0.0, 0.0,
                                1.0f,
                                matrices,
                                vertexConsumers,
                                LightmapTextureManager.MAX_LIGHT_COORDINATE
                        );
                        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                        tempEntity.discard();
                    }
                }
            }

            vertexConsumers.draw();

            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();

            matrices.pop();
        });
    }
}
