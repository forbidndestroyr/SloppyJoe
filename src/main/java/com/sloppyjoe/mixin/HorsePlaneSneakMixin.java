package com.sloppyjoe.mixin;

import com.sloppyjoe.entity.HorsePlaneEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Prevents the sneak-to-dismount behaviour while riding a HorsePlaneEntity
 * in the air.  Shift becomes the BRAKE key mid-flight; players can still
 * dismount normally by landing first and then pressing Shift.
 *
 * require = 0 makes the injector non-fatal if the call-site signature ever
 * changes across Minecraft versions, so it degrades gracefully.
 */
@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class HorsePlaneSneakMixin {

    @Redirect(
            method = "tickMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;stopRiding()V"
            ),
            require = 0
    )
    private void sloppyjoe$preventAirborneHorseplaneDismount(Entity instance) {
        // Suppress the sneak-dismount only while the HorsePlane is airborne.
        // On the ground the vanilla dismount still fires so the player can exit.
        if (instance.getVehicle() instanceof HorsePlaneEntity plane && !plane.isOnGround()) {
            return;
        }
        instance.stopRiding();
    }
}
