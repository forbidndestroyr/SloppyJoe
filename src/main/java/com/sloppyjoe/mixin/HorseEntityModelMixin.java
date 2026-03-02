package com.sloppyjoe.mixin;

import com.sloppyjoe.client.HorsePlaneRenderState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.AbstractHorseEntityModel;
import net.minecraft.client.render.entity.state.LivingHorseEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spins the horse tail around the Z axis (propeller-style) when the entity is a
 * HorsePlaneEntity with a passenger.
 * roll = rotation around the Z axis = the horse's spine axis when viewed from behind,
 * so the tail sweeps through all four quadrants like a propeller blade.
 */
@Mixin(AbstractHorseEntityModel.class)
public class HorseEntityModelMixin {

    @Shadow private ModelPart tail;

    @Inject(
        method = "setAngles(Lnet/minecraft/client/render/entity/state/LivingHorseEntityRenderState;)V",
        at = @At("TAIL")
    )
    private void onSetAngles(LivingHorseEntityRenderState state, CallbackInfo ci) {
        if (!(state instanceof HorsePlaneRenderState spinState)) return;
        if (!spinState.sloppyjoe$isSpinning()) return;

        // Spin around Z axis (roll) for propeller-style rotation viewed from behind.
        // ~5 full rotations per second (200 ms period).
        float angle = (float)((System.currentTimeMillis() % 200L) / 200.0 * Math.PI * 2.0);
        this.tail.roll  = angle;
        this.tail.yaw   = 0f;
        this.tail.pitch = 0f;
    }
}
