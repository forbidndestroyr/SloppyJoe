package com.sloppyjoe.client;

import com.sloppyjoe.entity.HorsePlaneEntity;
import com.sloppyjoe.entity.ModEntities;
import com.sloppyjoe.update.AutoUpdater;
import com.sloppyjoe.network.CancelGrabPayload;
import com.sloppyjoe.network.GlassesEquipPayload;
import com.sloppyjoe.network.GrabSyncPayload;
import com.sloppyjoe.network.ReleaseSyncPayload;
import com.sloppyjoe.network.RotatePayload;
import com.sloppyjoe.network.ScaleAdjustPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.HorseEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.HorseEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.LivingHorseEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class SloppyJoeModClient implements ClientModInitializer {

    private static KeyBinding cancelGrabKey;
    private static KeyBinding scaleUpKey;
    private static KeyBinding scaleDownKey;
    private static KeyBinding cycleAxisKey;
    private static KeyBinding rotateLeftKey;
    private static KeyBinding rotateRightKey;
    private static KeyBinding toggleRollKey;

    private static int currentAxis = 0; // 0=uniform, 1=X, 2=Y, 3=Z
    static final String[] AXIS_NAMES = {"Uniform", "X", "Y", "Z"};

    // --- Horse-plane camera roll ---
    // horsePlaneRollTarget: raw velocity-derived value, updated each tick.
    // Smoothing (lerp) is done per-frame inside CameraMixin so the roll is
    // buttery-smooth at any framerate instead of stepping every 50 ms.
    private static float horsePlaneRollTarget = 0f;
    private static boolean horsePlaneRollEnabled = true;

    /** Called by CameraMixin to read the current roll target (radians). */
    public static float getHorsePlaneRollTarget() { return horsePlaneRollTarget; }

    /** Called by CameraMixin to check whether the roll effect is active. */
    public static boolean isHorsePlaneRollEnabled() { return horsePlaneRollEnabled; }

    @Override
    public void onInitializeClient() {
        AutoUpdater.initClient();

        // S2C receivers
        ClientPlayNetworking.registerGlobalReceiver(GrabSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    PerspectiveLensGhostRenderer.setGrab(payload);
                    if (context.client().player != null) {
                        PerspectiveLensHeadRenderer.setEquipped(context.client().player.getId(), true);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(ReleaseSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    PerspectiveLensGhostRenderer.clearGrab();
                    if (context.client().player != null) {
                        PerspectiveLensHeadRenderer.setEquipped(context.client().player.getId(), false);
                    }
                }));

        // Payload from other players' grabs/releases — look up their entity by UUID
        ClientPlayNetworking.registerGlobalReceiver(GlassesEquipPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().world == null) return;
                    var player = context.client().world.getPlayerByUuid(payload.playerUuid());
                    if (player != null) {
                        PerspectiveLensHeadRenderer.setEquipped(player.getId(), payload.equipped());
                    }
                }));

        // Register feature renderers for player glasses and horse-plane saddle/goggles
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, entityRenderer, registrationHelper, context) -> {
            if (entityType == EntityType.PLAYER) {
                @SuppressWarnings("unchecked")
                FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> playerCtx =
                        (FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) (Object) entityRenderer;
                registrationHelper.register(new PerspectiveLensHeadRenderer(playerCtx));
                registrationHelper.register(new HorsePlanePilotRenderer(playerCtx));
            }
            if (entityType == ModEntities.HORSE_PLANE) {
                @SuppressWarnings("unchecked")
                FeatureRendererContext<LivingHorseEntityRenderState, HorseEntityModel> horseCtx =
                        (FeatureRendererContext<LivingHorseEntityRenderState, HorseEntityModel>) (Object) entityRenderer;
                registrationHelper.register(new HorsePlaneFeatureRenderer(horseCtx));
            }
        });

        // Keybinds
        cancelGrabKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sloppyjoe.cancel_grab",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "key.categories.sloppyjoe"
        ));
        scaleUpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sloppyjoe.scale_up",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_EQUAL,   // = / + key
                "key.categories.sloppyjoe"
        ));
        scaleDownKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sloppyjoe.scale_down",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_MINUS,   // - key
                "key.categories.sloppyjoe"
        ));
        cycleAxisKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sloppyjoe.cycle_axis",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                "key.categories.sloppyjoe"
        ));
        rotateLeftKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sloppyjoe.rotate_left",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_BRACKET,   // [ key
                "key.categories.sloppyjoe"
        ));
        rotateRightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sloppyjoe.rotate_right",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_BRACKET,  // ] key
                "key.categories.sloppyjoe"
        ));
        toggleRollKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sloppyjoe.toggle_roll",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,              // R key
                "key.categories.sloppyjoe"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Compute the roll TARGET each tick from the plane's lateral velocity.
            // The actual per-frame smoothing and application is done in CameraMixin
            // so the camera roll is interpolated every rendered frame, not every tick.
            horsePlaneRollTarget = 0f;
            if (client.options.getPerspective().isFirstPerson()
                    && client.player != null
                    && client.player.getVehicle() instanceof HorsePlaneEntity plane) {
                Vec3d vel = plane.getVelocity();
                float yawRad = (float) Math.toRadians(plane.getYaw());
                // Right vector in Minecraft: (-cos yaw, 0, -sin yaw).
                // Rightward lateral velocity = vel · right = -(vel.x*cos + vel.z*sin).
                // Negate to get a positive value when moving right → bank right.
                float lateral = -(float)(vel.x * Math.cos(yawRad) + vel.z * Math.sin(yawRad));
                horsePlaneRollTarget = Math.max(-0.35f, Math.min(0.35f, lateral * 0.4f));
            }

            // Toggle roll on/off
            while (toggleRollKey.wasPressed()) {
                horsePlaneRollEnabled = !horsePlaneRollEnabled;
                if (client.player != null) {
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal(horsePlaneRollEnabled
                                    ? "\u00a77Camera roll: \u00a7aON"
                                    : "\u00a77Camera roll: \u00a7cOFF"), true);
                }
            }

            // Keep the pilot gear set current so HorsePlanePilotRenderer can identify riders.
            HorsePlanePilotRenderer.updatePilots(client.world);
            HorsePlanePilotRenderer.tickAll();

            PerspectiveLensHeadRenderer.tickAll();

            // Cancel grab
            while (cancelGrabKey.wasPressed()) {
                if (PerspectiveLensGhostRenderer.hasGrab()) {
                    ClientPlayNetworking.send(new CancelGrabPayload());
                }
            }

            if (PerspectiveLensGhostRenderer.hasGrab()) {
                // Cycle scale axis
                while (cycleAxisKey.wasPressed()) {
                    currentAxis = (currentAxis + 1) % 4;
                    PerspectiveLensGhostRenderer.setScaleAxis(currentAxis);
                }

                // Scale up
                while (scaleUpKey.wasPressed()) {
                    PerspectiveLensGhostRenderer.adjustScale(true, currentAxis);
                    ClientPlayNetworking.send(new ScaleAdjustPayload(true, currentAxis));
                }
                // Scale down
                while (scaleDownKey.wasPressed()) {
                    PerspectiveLensGhostRenderer.adjustScale(false, currentAxis);
                    ClientPlayNetworking.send(new ScaleAdjustPayload(false, currentAxis));
                }

                // Rotate left ([)
                while (rotateLeftKey.wasPressed()) {
                    PerspectiveLensGhostRenderer.adjustRotation(false);
                    ClientPlayNetworking.send(new RotatePayload(false));
                }
                // Rotate right (])
                while (rotateRightKey.wasPressed()) {
                    PerspectiveLensGhostRenderer.adjustRotation(true);
                    ClientPlayNetworking.send(new RotatePayload(true));
                }

                // Action bar: effective scale + rotation preview
                if (client.player != null) {
                    float grabDist   = PerspectiveLensGhostRenderer.getGrabDistance();
                    float targetDist = PerspectiveLensGhostRenderer.getCrosshairDistance(client, 3.0f);
                    float effective  = (targetDist / grabDist) * PerspectiveLensGhostRenderer.getScaleMultiplier();
                    String axisName  = AXIS_NAMES[currentAxis];
                    int rot          = PerspectiveLensGhostRenderer.getRotationSteps() * 45;
                    client.player.sendMessage(
                            Text.literal(String.format(
                                    "\u00a77Scale [\u00a7fC\u00a77: %s] \u00a7f%.2fx \u00a77[= / -]  \u00a77Rot: \u00a7f%d\u00b0\u00a77 [[ / ]]",
                                    axisName, effective, rot)),
                            true
                    );
                }
            }
        });

        PerspectiveLensGhostRenderer.register();

        // Entity renderers — HorsePlaneEntity extends HorseEntity so the horse renderer works at runtime.
        // Raw-type cast bypasses the generic mismatch between HorseEntity and HorsePlaneEntity.
        @SuppressWarnings({"unchecked", "rawtypes"})
        net.minecraft.client.render.entity.EntityRendererFactory rawFactory =
                ctx -> new HorseEntityRenderer(ctx);
        EntityRendererRegistry.register((net.minecraft.entity.EntityType) ModEntities.HORSE_PLANE, rawFactory);
    }
}
