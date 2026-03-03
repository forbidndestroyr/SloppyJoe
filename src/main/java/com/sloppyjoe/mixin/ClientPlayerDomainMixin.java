package com.sloppyjoe.mixin;

import com.sloppyjoe.client.cutscene.CutsceneManager;
import com.sloppyjoe.client.cutscene.HollowPurpleCutscene;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Locks player movement while a domain cutscene or Hollow Purple cutscene is playing.
 */
@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerDomainMixin {

    @Inject(method = "tickMovement", at = @At("HEAD"), require = 0)
    private void sloppyjoe$freezeInCutscene(CallbackInfo ci) {
        if (!CutsceneManager.isInCutscene() && !HollowPurpleCutscene.isActive()) return;

        ClientPlayerEntity self = (ClientPlayerEntity)(Object) this;
        // Zero out velocity to prevent movement
        self.setVelocity(Vec3d.ZERO);
        self.sidewaysSpeed = 0f;
        self.forwardSpeed = 0f;
        self.upwardSpeed = 0f;
    }
}
