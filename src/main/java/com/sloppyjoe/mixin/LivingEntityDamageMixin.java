package com.sloppyjoe.mixin;

import com.sloppyjoe.domain.DomainManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes players inside a domain cutscene invincible.
 * Server-side only — no @Environment annotation (runs on logical server).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void sloppyjoe$blockDamageInCutscene(ServerWorld world, DamageSource source, float amount,
                                                   CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity)(Object) this;
        if (self instanceof ServerPlayerEntity player) {
            if (DomainManager.cutscenePlayers.contains(player.getUuid())) {
                cir.setReturnValue(false);
            }
        }
    }
}
