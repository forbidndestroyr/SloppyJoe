package com.sloppyjoe.mixin;

import com.sloppyjoe.client.HorsePlaneRenderState;
import net.minecraft.client.render.entity.state.LivingHorseEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds the HorsePlaneRenderState flag field to every LivingHorseEntityRenderState
 * (and its subclasses, including HorseEntityRenderState).
 * The field is set in AbstractHorseEntityRendererMixin.updateRenderState().
 */
@Mixin(LivingHorseEntityRenderState.class)
public class LivingHorseEntityRenderStateMixin implements HorsePlaneRenderState {

    @Unique
    private boolean sloppyjoe$spinning = false;

    @Override
    public boolean sloppyjoe$isSpinning() { return this.sloppyjoe$spinning; }

    @Override
    public void sloppyjoe$setSpinning(boolean spinning) { this.sloppyjoe$spinning = spinning; }
}
