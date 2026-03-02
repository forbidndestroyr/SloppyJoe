package com.sloppyjoe.mixin;

import com.sloppyjoe.client.HorsePlaneRenderState;
import com.sloppyjoe.entity.HorsePlaneEntity;
import net.minecraft.client.render.entity.AbstractHorseEntityRenderer;
import net.minecraft.client.render.entity.state.LivingHorseEntityRenderState;
import net.minecraft.entity.passive.AbstractHorseEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Populates the HorsePlaneRenderState.spinning flag each frame so the model mixin
 * can spin the tail without needing an entity-ID field on the render state.
 */
@Mixin(AbstractHorseEntityRenderer.class)
public class AbstractHorseEntityRendererMixin {

    @Inject(
        method = "updateRenderState(Lnet/minecraft/entity/passive/AbstractHorseEntity;Lnet/minecraft/client/render/entity/state/LivingHorseEntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void onUpdateRenderState(AbstractHorseEntity entity,
                                     LivingHorseEntityRenderState state,
                                     float tickDelta,
                                     CallbackInfo ci) {
        if (state instanceof HorsePlaneRenderState spinState) {
            spinState.sloppyjoe$setSpinning(
                entity instanceof HorsePlaneEntity && entity.hasPassengers()
            );
        }
    }
}
