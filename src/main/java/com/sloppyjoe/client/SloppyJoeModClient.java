package com.sloppyjoe.client;

import com.sloppyjoe.client.cutscene.CutsceneManager;
import com.sloppyjoe.client.cutscene.HollowPurpleCutscene;
import com.sloppyjoe.client.renderer.DomainOrbEntityRenderer;
import com.sloppyjoe.client.renderer.HollowPurpleEntityRenderer;
import com.sloppyjoe.network.HollowPurpleCastPayload;
import com.sloppyjoe.network.HollowPurpleStartPayload;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import com.sloppyjoe.entity.DomainOrbEntity;
import com.sloppyjoe.entity.HorsePlaneEntity;
import com.sloppyjoe.entity.ModEntities;
import com.sloppyjoe.network.CancelGrabPayload;
import com.sloppyjoe.network.DomainActivatePayload;
import com.sloppyjoe.network.DomainCastRequestPayload;
import com.sloppyjoe.network.DomainCastStartPayload;
import com.sloppyjoe.network.DomainEnterPayload;
import com.sloppyjoe.network.DomainExpirePayload;
import com.sloppyjoe.network.GlassesEquipPayload;
import com.sloppyjoe.network.GrabSyncPayload;
import com.sloppyjoe.network.ReleaseSyncPayload;
import com.sloppyjoe.network.RotatePayload;
import com.sloppyjoe.network.ScaleAdjustPayload;
import com.sloppyjoe.update.AutoUpdater;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
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
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class SloppyJoeModClient implements ClientModInitializer {

    private static KeyBinding cancelGrabKey;
    private static KeyBinding scaleUpKey;
    private static KeyBinding scaleDownKey;
    private static KeyBinding cycleAxisKey;
    private static KeyBinding rotateLeftKey;
    private static KeyBinding rotateRightKey;
    private static KeyBinding toggleRollKey;
    private static KeyBinding domainExpandKey;
    private static KeyBinding hollowPurpleKey;

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

        // Domain expansion S2C receivers
        ClientPlayNetworking.registerGlobalReceiver(DomainCastStartPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    CutsceneManager.cutscenePlayers.add(payload.casterUuid());
                    // Show charge animation to the caster only
                    if (context.client().player == null) return;
                    if (context.client().player.getUuid().equals(payload.casterUuid())) {
                        CutsceneManager.startCastCharge(payload.domainType());
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(DomainActivatePayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    CutsceneManager.cutscenePlayers.addAll(payload.capturedUuids());
                    CutsceneManager.cutscenePlayers.add(payload.casterUuid());
                    if (context.client().player == null) return;
                    java.util.UUID local = context.client().player.getUuid();
                    if (local.equals(payload.casterUuid()) || payload.capturedUuids().contains(local)) {
                        CutsceneManager.onDomainActivate(payload.casterUuid(), payload.domainType());
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(DomainExpirePayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    CutsceneManager.cutscenePlayers.remove(payload.casterUuid());
                    CutsceneManager.onDomainExpire();
                }));

        ClientPlayNetworking.registerGlobalReceiver(DomainEnterPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    CutsceneManager.cutscenePlayers.add(payload.playerUuid());
                    if (context.client().player == null) return;
                    if (context.client().player.getUuid().equals(payload.playerUuid())) {
                        // Local player entered orb — trigger cutscene if not already active
                        if (!CutsceneManager.isActive()) {
                            // We don't know domain type here; server should send DomainActivatePayload separately
                        }
                    }
                }));

        // Hollow Purple cutscene trigger (S2C — sent only to the caster)
        ClientPlayNetworking.registerGlobalReceiver(HollowPurpleStartPayload.ID, (payload, context) ->
                context.client().execute(HollowPurpleCutscene::start));

        // Register feature renderers for player glasses and horse-plane saddle/goggles
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, entityRenderer, registrationHelper, context) -> {
            if (entityType == EntityType.PLAYER) {
                @SuppressWarnings("unchecked")
                FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> playerCtx =
                        (FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) (Object) entityRenderer;
                registrationHelper.register(new PerspectiveLensHeadRenderer(playerCtx));
                registrationHelper.register(new HorsePlanePilotRenderer(playerCtx));
                registrationHelper.register(new DomainEyeGlowRenderer(playerCtx));
                registrationHelper.register(new GojoBlindfoldRenderer(playerCtx));
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
        domainExpandKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sloppyjoe.domain_expand",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,              // V key
                "key.categories.sloppyjoe"
        ));
        hollowPurpleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sloppyjoe.hollow_purple",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,              // H key (rebindable in options)
                "key.categories.sloppyjoe"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Tick cutscene state machines
            CutsceneManager.tick(client);
            HollowPurpleCutscene.tick(client);

            // Domain expand keybind
            while (domainExpandKey.wasPressed()) {
                ClientPlayNetworking.send(new DomainCastRequestPayload());
            }

            // Hollow Purple keybind
            while (hollowPurpleKey.wasPressed()) {
                ClientPlayNetworking.send(new HollowPurpleCastPayload());
            }

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
        UnlimitedVoidRenderer.register();
        HollowPurpleRenderer.register();

        // Entity renderers — HorsePlaneEntity extends HorseEntity so the horse renderer works at runtime.
        // Raw-type cast bypasses the generic mismatch between HorseEntity and HorsePlaneEntity.
        @SuppressWarnings({"unchecked", "rawtypes"})
        net.minecraft.client.render.entity.EntityRendererFactory rawFactory =
                ctx -> new HorseEntityRenderer(ctx);
        EntityRendererRegistry.register((net.minecraft.entity.EntityType) ModEntities.HORSE_PLANE, rawFactory);

        // Domain orb renderer
        EntityRendererRegistry.register(ModEntities.DOMAIN_ORB,
                ctx -> new DomainOrbEntityRenderer(ctx));

        // Hollow Purple renderer
        EntityRendererRegistry.register(ModEntities.HOLLOW_PURPLE,
                ctx -> new HollowPurpleEntityRenderer(ctx));

        // World-space blue/red glowing cubes during Hollow Purple cutscene
        WorldRenderEvents.AFTER_ENTITIES.register(SloppyJoeModClient::renderHpWorldCubes);
    }

    // -----------------------------------------------------------------------
    // Hollow Purple — world-space glowing cube rendering
    // -----------------------------------------------------------------------

    private static final Identifier HP_CUBE_TEX =
            Identifier.of("minecraft", "textures/block/obsidian.png");

    private static void renderHpWorldCubes(WorldRenderContext context) {
        if (!HollowPurpleCutscene.isActive()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (!(context.consumers() instanceof VertexConsumerProvider.Immediate consumers)) return;

        HollowPurpleCutscene.Phase phase = HollowPurpleCutscene.getPhase();
        float progress = HollowPurpleCutscene.getPhaseProgress();
        float timer    = HollowPurpleCutscene.getPhaseTimer()
                         + context.tickCounter().getTickDelta(false);

        // Determine visibility per phase
        boolean showBlue   = phase == HollowPurpleCutscene.Phase.HP_CHARGE
                          || phase == HollowPurpleCutscene.Phase.HP_BLUE
                          || phase == HollowPurpleCutscene.Phase.HP_RED
                          || phase == HollowPurpleCutscene.Phase.HP_COMBINE;
        boolean showRed    = phase == HollowPurpleCutscene.Phase.HP_CHARGE
                          || phase == HollowPurpleCutscene.Phase.HP_RED
                          || phase == HollowPurpleCutscene.Phase.HP_COMBINE;
        boolean showPurple = phase == HollowPurpleCutscene.Phase.HP_PURPLE;

        if (!showBlue && !showPurple) return;

        // Player orientation
        float yawRad = (float)Math.toRadians(client.player.getYaw());
        double rightX = Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);
        double fwdX   = -Math.sin(yawRad);
        double fwdZ   =  Math.cos(yawRad);

        // Player eye position
        double px = client.player.getX();
        double py = client.player.getY() + client.player.getEyeHeight(client.player.getPose());
        double pz = client.player.getZ();

        // Side offset interpolates to 0 as cubes converge during HP_COMBINE
        double sideOff = 1.1;
        double fwdOff  = 0.6;
        if (phase == HollowPurpleCutscene.Phase.HP_COMBINE) {
            float t = easeInOut(progress);
            sideOff = 1.1 * (1.0 - t);
            fwdOff  = 0.6 + 0.8 * t;
        }

        // Blue cube — left side
        double bx = px + rightX * (-sideOff) + fwdX * fwdOff;
        double by = py - 0.15;
        double bz = pz + rightZ * (-sideOff) + fwdZ * fwdOff;

        // Red cube — right side
        double rx2 = px + rightX * sideOff + fwdX * fwdOff;
        double ry2 = by;
        double rz2 = pz + rightZ * sideOff + fwdZ * fwdOff;

        // Purple merged position — forward of player
        double ppx = px + fwdX * (fwdOff + 0.4);
        double ppy = by;
        double ppz = pz + fwdZ * (fwdOff + 0.4);

        Vec3d cam = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();

        // Opacity ramps
        float blueA, redA;
        switch (phase) {
            case HP_CHARGE  -> { blueA = progress * 0.7f;  redA  = progress * 0.55f; }
            case HP_BLUE    -> { blueA = 0.9f;              redA  = progress * 0.5f;  }
            case HP_RED     -> { blueA = 0.9f;              redA  = 0.9f;             }
            case HP_COMBINE -> { blueA = 1.0f;              redA  = 1.0f;             }
            default         -> { blueA = 0f;                redA  = 0f;               }
        }
        float purpleA = showPurple ? progress : 0f;

        // Blue cube (outer glow + core)
        if (showBlue && blueA > 0f) {
            renderWorldCube(matrices, consumers, cam,
                    bx, by, bz, 0.42f, timer,
                    0, 60, 230, (int)(blueA * 195));
            renderWorldCube(matrices, consumers, cam,
                    bx, by, bz, 0.70f, timer * 0.65f,
                    20, 90, 255, (int)(blueA * 65));
        }

        // Red cube (outer glow + core)
        if (showRed && redA > 0f) {
            renderWorldCube(matrices, consumers, cam,
                    rx2, ry2, rz2, 0.42f, -timer * 1.1f,
                    210, 20, 200, (int)(redA * 195));
            renderWorldCube(matrices, consumers, cam,
                    rx2, ry2, rz2, 0.70f, -timer * 0.7f,
                    240, 0, 160, (int)(redA * 65));
        }

        // Purple merged cube
        if (showPurple && purpleA > 0f) {
            float sz = 0.42f + 0.22f * progress;
            renderWorldCube(matrices, consumers, cam,
                    ppx, ppy, ppz, sz, timer * 1.8f,
                    180, 0, 255, (int)(purpleA * 230));
            renderWorldCube(matrices, consumers, cam,
                    ppx, ppy, ppz, sz * 1.6f, timer * 0.9f,
                    110, 0, 180, (int)(purpleA * 80));
        }

        consumers.draw();
    }

    /**
     * Renders a glowing cube at world position (wx, wy, wz) using emissive translucent
     * rendering. All faces always full-bright regardless of in-world lighting.
     */
    private static void renderWorldCube(MatrixStack matrices,
                                        VertexConsumerProvider.Immediate consumers,
                                        Vec3d cam,
                                        double wx, double wy, double wz,
                                        float size, float rotAngle,
                                        int r, int g, int b, int a) {
        if (a <= 0) return;
        VertexConsumer vc = consumers.getBuffer(
                RenderLayer.getEntityTranslucentEmissive(HP_CUBE_TEX));

        matrices.push();
        matrices.translate(wx - cam.x, wy - cam.y, wz - cam.z);
        matrices.scale(size, size, size);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(rotAngle * 0.055f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(rotAngle * 0.032f));

        var entry  = matrices.peek();
        var mat    = entry.getPositionMatrix();
        int ov     = OverlayTexture.DEFAULT_UV;
        int light  = 15728880;
        float s = 1f;

        // Bottom
        vc.vertex(mat,-s,-s,-s).color(r,g,b,a).texture(0f,0f).overlay(ov).light(light).normal(entry,0f,-1f,0f);
        vc.vertex(mat, s,-s,-s).color(r,g,b,a).texture(1f,0f).overlay(ov).light(light).normal(entry,0f,-1f,0f);
        vc.vertex(mat, s,-s, s).color(r,g,b,a).texture(1f,1f).overlay(ov).light(light).normal(entry,0f,-1f,0f);
        vc.vertex(mat,-s,-s, s).color(r,g,b,a).texture(0f,1f).overlay(ov).light(light).normal(entry,0f,-1f,0f);
        // Top
        vc.vertex(mat,-s, s, s).color(r,g,b,a).texture(0f,0f).overlay(ov).light(light).normal(entry,0f,1f,0f);
        vc.vertex(mat, s, s, s).color(r,g,b,a).texture(1f,0f).overlay(ov).light(light).normal(entry,0f,1f,0f);
        vc.vertex(mat, s, s,-s).color(r,g,b,a).texture(1f,1f).overlay(ov).light(light).normal(entry,0f,1f,0f);
        vc.vertex(mat,-s, s,-s).color(r,g,b,a).texture(0f,1f).overlay(ov).light(light).normal(entry,0f,1f,0f);
        // North (Z-)
        vc.vertex(mat, s,-s,-s).color(r,g,b,a).texture(0f,1f).overlay(ov).light(light).normal(entry,0f,0f,-1f);
        vc.vertex(mat,-s,-s,-s).color(r,g,b,a).texture(1f,1f).overlay(ov).light(light).normal(entry,0f,0f,-1f);
        vc.vertex(mat,-s, s,-s).color(r,g,b,a).texture(1f,0f).overlay(ov).light(light).normal(entry,0f,0f,-1f);
        vc.vertex(mat, s, s,-s).color(r,g,b,a).texture(0f,0f).overlay(ov).light(light).normal(entry,0f,0f,-1f);
        // South (Z+)
        vc.vertex(mat,-s,-s, s).color(r,g,b,a).texture(0f,1f).overlay(ov).light(light).normal(entry,0f,0f,1f);
        vc.vertex(mat, s,-s, s).color(r,g,b,a).texture(1f,1f).overlay(ov).light(light).normal(entry,0f,0f,1f);
        vc.vertex(mat, s, s, s).color(r,g,b,a).texture(1f,0f).overlay(ov).light(light).normal(entry,0f,0f,1f);
        vc.vertex(mat,-s, s, s).color(r,g,b,a).texture(0f,0f).overlay(ov).light(light).normal(entry,0f,0f,1f);
        // West (X-)
        vc.vertex(mat,-s,-s,-s).color(r,g,b,a).texture(0f,1f).overlay(ov).light(light).normal(entry,-1f,0f,0f);
        vc.vertex(mat,-s,-s, s).color(r,g,b,a).texture(1f,1f).overlay(ov).light(light).normal(entry,-1f,0f,0f);
        vc.vertex(mat,-s, s, s).color(r,g,b,a).texture(1f,0f).overlay(ov).light(light).normal(entry,-1f,0f,0f);
        vc.vertex(mat,-s, s,-s).color(r,g,b,a).texture(0f,0f).overlay(ov).light(light).normal(entry,-1f,0f,0f);
        // East (X+)
        vc.vertex(mat, s,-s, s).color(r,g,b,a).texture(0f,1f).overlay(ov).light(light).normal(entry,1f,0f,0f);
        vc.vertex(mat, s,-s,-s).color(r,g,b,a).texture(1f,1f).overlay(ov).light(light).normal(entry,1f,0f,0f);
        vc.vertex(mat, s, s,-s).color(r,g,b,a).texture(1f,0f).overlay(ov).light(light).normal(entry,1f,0f,0f);
        vc.vertex(mat, s, s, s).color(r,g,b,a).texture(0f,0f).overlay(ov).light(light).normal(entry,1f,0f,0f);

        matrices.pop();
    }

    private static float easeInOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }
}
