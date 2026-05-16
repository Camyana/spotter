package com.cam.pingmod.mixin;

import com.cam.pingmod.client.ActivePing;
import com.cam.pingmod.client.ClientPingHandler;
import com.cam.pingmod.network.HitType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void pingmod$forceGlowOnPingedEntities(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        int id = entity.getId();
        for (ActivePing ping : ClientPingHandler.getActivePings()) {
            if (ping.hitType == HitType.ENTITY && ping.entityId == id) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
