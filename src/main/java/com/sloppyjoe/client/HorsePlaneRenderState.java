package com.sloppyjoe.client;

/**
 * Mixed into LivingHorseEntityRenderState via {@code LivingHorseEntityRenderStateMixin}.
 * Carries the "spinning" flag from the entity renderer to the model's setAngles() mixin
 * so the tail animation can identify HorsePlane entities without needing an entity-ID field
 * on the render state (which doesn't exist in 1.21.4).
 */
public interface HorsePlaneRenderState {
    boolean sloppyjoe$isSpinning();
    void sloppyjoe$setSpinning(boolean spinning);
}
