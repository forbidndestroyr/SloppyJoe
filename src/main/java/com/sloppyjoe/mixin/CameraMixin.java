package com.sloppyjoe.mixin;

import com.sloppyjoe.client.SloppyJoeModClient;
import com.sloppyjoe.client.cutscene.CutsceneManager;
import com.sloppyjoe.entity.HorsePlaneEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two camera effects in one mixin:
 *
 * 1. HORSE-PLANE ROLL — applies a Z-axis roll while the local player rides a
 *    HorsePlaneEntity in first-person, smoothed per-frame.
 *
 * 2. DOMAIN CINEMATIC CAMERA — overrides the camera position/rotation during
 *    domain-expansion cutscene phases so it orbits, dollies in, and zooms out
 *    around the caster's player model.  Target values come from CutsceneManager
 *    (updated each game tick); this mixin smooths them at render-frame rate.
 */
@Mixin(Camera.class)
public class CameraMixin {

    @Shadow private Quaternionf rotation;

    // Shadows of protected Camera setters — bodies are replaced at runtime
    @Shadow protected void setRotation(float yaw, float pitch) {}
    @Shadow protected void setPos(double x, double y, double z) {}

    // ---- Horse-plane roll ----
    @Unique private float sloppyjoe$smoothedRoll = 0f;

    // ---- Cinematic camera (smoothed per-frame) ----
    @Unique private float sloppyjoe$smoothOrbit  = 0f;
    @Unique private float sloppyjoe$smoothDist   = 3.5f;
    @Unique private float sloppyjoe$smoothHeight = 0f;
    @Unique private boolean sloppyjoe$cinematicInit = false;

    @Inject(method = "update", at = @At("TAIL"))
    private void sloppyjoe$applyCameraEffects(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // ----------------------------------------------------------------
        // 1. Domain cinematic camera
        // ----------------------------------------------------------------
        if (CutsceneManager.isCinematicCameraActive()
                && client != null && client.player != null) {

            // Snap smooth values to target on first frame of cinematic
            if (!sloppyjoe$cinematicInit) {
                sloppyjoe$smoothOrbit  = CutsceneManager.getCinematicOrbit();
                sloppyjoe$smoothDist   = CutsceneManager.getCinematicDist();
                sloppyjoe$smoothHeight = CutsceneManager.getCinematicHeightOff();
                sloppyjoe$cinematicInit = true;
            }

            // Per-frame lerp toward tick-updated targets
            float targetOrbit  = CutsceneManager.getCinematicOrbit();
            float targetDist   = CutsceneManager.getCinematicDist();
            float targetHeight = CutsceneManager.getCinematicHeightOff();

            // Orbit lerp: shortest arc (handle wrap-around)
            float orbitDiff = targetOrbit - sloppyjoe$smoothOrbit;
            while (orbitDiff >  Math.PI) orbitDiff -= 2f * (float) Math.PI;
            while (orbitDiff < -Math.PI) orbitDiff += 2f * (float) Math.PI;
            sloppyjoe$smoothOrbit  += orbitDiff * 0.12f;

            sloppyjoe$smoothDist   += (targetDist   - sloppyjoe$smoothDist)   * 0.14f;
            sloppyjoe$smoothHeight += (targetHeight  - sloppyjoe$smoothHeight) * 0.12f;

            // Player eye position
            double eyeX = client.player.getX();
            double eyeY = client.player.getEyeY();
            double eyeZ = client.player.getZ();

            // Camera position in world space (orbit around eye)
            double camX = eyeX + Math.sin(sloppyjoe$smoothOrbit) * sloppyjoe$smoothDist;
            double camZ = eyeZ + Math.cos(sloppyjoe$smoothOrbit) * sloppyjoe$smoothDist;
            double camY = eyeY + sloppyjoe$smoothHeight;

            // Direction from camera toward player eye (for yaw/pitch)
            float dx    = (float)(eyeX - camX);
            float dy    = (float)(eyeY - camY);
            float dz    = (float)(eyeZ - camZ);
            float hDist = (float) Math.sqrt(dx * dx + dz * dz);

            // Yaw: atan2(-dx, dz) matches Minecraft's camera convention
            float camYaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
            // Pitch: negative dy when camera above player → looking down → positive pitch
            float camPitch = (float) Math.toDegrees(Math.atan2(-dy, hDist));

            setPos(camX, camY, camZ);
            setRotation(camYaw, camPitch);

            // Cinematic is handled — skip horse-plane roll entirely
            return;
        } else {
            sloppyjoe$cinematicInit = false;  // reset so next cinematic snaps cleanly
        }

        // ----------------------------------------------------------------
        // 2. Horse-plane roll (only when not in a cinematic)
        // ----------------------------------------------------------------
        if (client == null || client.player == null) {
            sloppyjoe$smoothedRoll *= 0.85f;
            if (Math.abs(sloppyjoe$smoothedRoll) < 0.0005f) sloppyjoe$smoothedRoll = 0f;
            if (Math.abs(sloppyjoe$smoothedRoll) > 0.0005f) this.rotation.rotateZ(sloppyjoe$smoothedRoll);
            return;
        }

        float target = 0f;
        if (SloppyJoeModClient.isHorsePlaneRollEnabled()
                && client.options.getPerspective().isFirstPerson()
                && client.player.getVehicle() instanceof HorsePlaneEntity) {
            target = SloppyJoeModClient.getHorsePlaneRollTarget();
        }

        sloppyjoe$smoothedRoll += (target - sloppyjoe$smoothedRoll) * 0.10f;
        if (Math.abs(sloppyjoe$smoothedRoll) < 0.0005f) sloppyjoe$smoothedRoll = 0f;

        if (Math.abs(sloppyjoe$smoothedRoll) > 0.0005f) {
            this.rotation.rotateZ(sloppyjoe$smoothedRoll);
        }
    }
}
