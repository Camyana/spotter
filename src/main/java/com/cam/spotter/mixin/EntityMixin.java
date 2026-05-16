package com.cam.spotter.mixin;

import com.cam.spotter.client.ActivePing;
import com.cam.spotter.client.ClientPingHandler;
import com.cam.spotter.client.PingClientConfig;
import com.cam.spotter.network.HitType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /** RGB applied to the glowing-outline shader when a hostile mob is pinged. */
    private static final int SPOTTER$HOSTILE_OUTLINE_RGB = 0xFF3838;

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void spotter$colorForPingedEntities(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        int id = self.getId();
        for (ActivePing ping : ClientPingHandler.getActivePings()) {
            if (ping.hitType == HitType.ENTITY && ping.entityId == id) {
                if (PingClientConfig.SHOW_HOSTILE_INDICATOR.get() && self instanceof Enemy) {
                    cir.setReturnValue(SPOTTER$HOSTILE_OUTLINE_RGB);
                } else {
                    cir.setReturnValue(ping.color & 0xFFFFFF);
                }
                return;
            }
        }
    }
}
