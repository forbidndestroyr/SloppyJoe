package com.sloppyjoe.mixin;

import com.sloppyjoe.client.SloppyJoeModClient;
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
 * Applies a Z-axis roll to the camera's rotation quaternion while the local player
 * is riding a HorsePlaneEntity in first-person view.
 * Because this modifies the Camera directly, the roll affects ALL rendering
 * (terrain, entities, particles) — unlike a MatrixStack push which only affects
 * the entity/overlay layer.
 */
@Mixin(Camera.class)
public class CameraMixin {

    @Shadow private Quaternionf rotation;

    /**
     * Per-instance smoothed roll value.
     * Updated every frame (inside the camera update call) so the interpolation
     * runs at render framerate rather than game-tick rate, giving a perfectly
     * smooth result regardless of TPS or FPS.
     */
    @Unique
    private float sloppyjoe$smoothedRoll = 0f;

    @Inject(method = "update", at = @At("TAIL"))
    private void applyHorsePlaneRoll(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            // Fade out smoothly even after dismounting
            sloppyjoe$smoothedRoll *= 0.85f;
            if (Math.abs(sloppyjoe$smoothedRoll) < 0.0005f) sloppyjoe$smoothedRoll = 0f;
            if (Math.abs(sloppyjoe$smoothedRoll) > 0.0005f) this.rotation.rotateZ(sloppyjoe$smoothedRoll);
            return;
        }

        // Determine the target: use the tick-computed value when roll is enabled
        // and the player is riding a HorsePlane in first-person; otherwise fade to 0.
        float target = 0f;
        if (SloppyJoeModClient.isHorsePlaneRollEnabled()
                && client.options.getPerspective().isFirstPerson()
                && client.player.getVehicle() instanceof HorsePlaneEntity) {
            target = SloppyJoeModClient.getHorsePlaneRollTarget();
        }

        // Lerp per-frame toward target (10 % per frame ≈ smooth at 60 fps).
        sloppyjoe$smoothedRoll += (target - sloppyjoe$smoothedRoll) * 0.10f;
        if (Math.abs(sloppyjoe$smoothedRoll) < 0.0005f) sloppyjoe$smoothedRoll = 0f;

        if (Math.abs(sloppyjoe$smoothedRoll) > 0.0005f) {
            // rotateZ modifies the quaternion in-place; applies on top of normal camera rotation.
            this.rotation.rotateZ(sloppyjoe$smoothedRoll);
        }
    }
}
