package com.cam.pingmod.mixin;

import com.cam.pingmod.client.ActivePing;
import com.cam.pingmod.client.ClientPingHandler;
import com.cam.pingmod.client.PingClientConfig;
import com.cam.pingmod.network.HitType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /** RGB applied to the glowing-outline shader when a hostile mob is pinged. */
    private static final int PINGMOD$HOSTILE_OUTLINE_RGB = 0xFF3838;

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void pingmod$colorForPingedEntities(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        int id = self.getId();
        for (ActivePing ping : ClientPingHandler.getActivePings()) {
            if (ping.hitType == HitType.ENTITY && ping.entityId == id) {
                if (PingClientConfig.SHOW_HOSTILE_INDICATOR.get() && self instanceof Enemy) {
                    cir.setReturnValue(PINGMOD$HOSTILE_OUTLINE_RGB);
                } else {
                    cir.setReturnValue(ping.color & 0xFFFFFF);
                }
                return;
            }
        }
    }
}
