package com.sloppyjoe.mixin;

import com.sloppyjoe.hollow.HollowPurpleManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels XP gain while Gojo's Blindfold is equipped.
 * Targets PlayerEntity so it covers all XP sources, but only cancels
 * on the server side (non-client world) and only for players in the
 * xpBlocked set managed by HollowPurpleManager.
 */
@Mixin(PlayerEntity.class)
public class ServerPlayerXpMixin {

    @Inject(method = "addExperience", at = @At("HEAD"), cancellable = true, require = 0)
    private void blockXpWhileBlindFoldEquipped(int experience, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (self.getWorld().isClient()) return;
        if (self instanceof ServerPlayerEntity sp) {
            if (HollowPurpleManager.isXpBlocked(sp.getUuid())) {
                ci.cancel();
            }
        }
    }
}
