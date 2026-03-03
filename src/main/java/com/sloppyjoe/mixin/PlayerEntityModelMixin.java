package com.sloppyjoe.mixin;

import com.sloppyjoe.client.cutscene.CutsceneManager;
import com.sloppyjoe.client.cutscene.HollowPurpleCutscene;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into BipedEntityModel.setAngles() to apply custom pose overrides
 * for the local player during domain cutscenes and Hollow Purple.
 *
 * Domain — snaps the head to face forward during the close-up cinematic phases.
 * Hollow Purple — animates both arms converging toward the caster's front.
 */
@Environment(EnvType.CLIENT)
@Mixin(BipedEntityModel.class)
public class PlayerEntityModelMixin {

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void applyCustomPose(BipedEntityRenderState state, CallbackInfo ci) {
        // Only affect the local player
        if (!(state instanceof PlayerEntityRenderState playerState)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        if (playerState.id != client.player.getId()) return;

        @SuppressWarnings("rawtypes")
        BipedEntityModel self = (BipedEntityModel)(Object)this;

        // ---- Domain cutscene: snap head forward ----
        if (CutsceneManager.isActive()) {
            CutsceneManager.Phase phase = CutsceneManager.getPhase();
            switch (phase) {
                case CUTSCENE_CLOSEUP, CUTSCENE_HAND_UP,
                     CUTSCENE_LIFT, CUTSCENE_EYES,
                     CUTSCENE_ZOOM_OUT, CUTSCENE_VOICE -> {
                    self.head.yaw   = 0f;
                    self.head.pitch = 0f;
                }
                default -> {}
            }
        }

        // ---- Hollow Purple: animate arms converging ----
        if (HollowPurpleCutscene.isActive()) {
            HollowPurpleCutscene.Phase hp = HollowPurpleCutscene.getPhase();
            float p = HollowPurpleCutscene.getPhaseProgress();
            float e = easeInOut(p);

            final float SPREAD_YAW   = (float)(Math.PI / 5.0);
            final float RAISE_PITCH  = -(float)(Math.PI / 4.0);
            final float THRUST_PITCH = -(float)(Math.PI / 2.0);

            switch (hp) {
                case HP_CHARGE -> {
                    // Arms spread outward as charge builds
                    self.leftArm.yaw   =  SPREAD_YAW * e;
                    self.rightArm.yaw  = -SPREAD_YAW * e;
                    self.leftArm.pitch  = RAISE_PITCH * e * 0.4f;
                    self.rightArm.pitch = RAISE_PITCH * e * 0.4f;
                }
                case HP_BLUE -> {
                    // Left arm: raise forward; right arm: slight outward extension
                    self.leftArm.pitch  = lerp(RAISE_PITCH * 0.4f, RAISE_PITCH * 1.2f, e);
                    self.leftArm.yaw    =  SPREAD_YAW;
                    self.rightArm.yaw   = -SPREAD_YAW;
                    self.rightArm.pitch = RAISE_PITCH * 0.3f;
                }
                case HP_RED -> {
                    // Right arm: raise forward; left arm mirrors
                    self.rightArm.pitch = lerp(RAISE_PITCH * 0.3f, RAISE_PITCH * 1.2f, e);
                    self.rightArm.yaw   = -SPREAD_YAW;
                    self.leftArm.yaw    =  SPREAD_YAW;
                    self.leftArm.pitch  = RAISE_PITCH * 1.2f;
                }
                case HP_COMBINE -> {
                    // Both arms sweep inward and forward, meeting at centre
                    float yaw   = SPREAD_YAW * (1f - e);
                    float pitch = lerp(RAISE_PITCH * 1.2f, RAISE_PITCH * 1.5f, e);
                    self.leftArm.yaw    =  yaw;
                    self.rightArm.yaw   = -yaw;
                    self.leftArm.pitch  = pitch;
                    self.rightArm.pitch = pitch;
                }
                case HP_PURPLE -> {
                    // Arms nearly together, extended fully forward
                    self.leftArm.yaw    =  0.08f;
                    self.rightArm.yaw   = -0.08f;
                    self.leftArm.pitch  = RAISE_PITCH * 1.7f;
                    self.rightArm.pitch = RAISE_PITCH * 1.7f;
                }
                case HP_FIRE -> {
                    // Thrust straight forward
                    self.leftArm.yaw    =  0.04f;
                    self.rightArm.yaw   = -0.04f;
                    self.leftArm.pitch  = THRUST_PITCH;
                    self.rightArm.pitch = THRUST_PITCH;
                }
                default -> {}
            }
        }
    }

    private static float easeInOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0f, Math.min(1f, t));
    }
}
