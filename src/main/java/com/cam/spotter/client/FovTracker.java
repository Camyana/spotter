package com.cam.spotter.client;

import com.cam.spotter.Spotter;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.slf4j.Logger;

/**
 * Captures the effective rendering FOV each frame so our HUD projection can
 * match the actual world projection. The vanilla {@code getFov} method on
 * {@code GameRenderer} is private; this event fires with the post-modifier
 * value (sprint, speed effects, zoom, etc.) just before the projection matrix
 * is built, which is exactly what we need.
 *
 * <p>Filtered to {@code usedConfiguredFov()} calls so we ignore internal
 * panoramic / screenshot FOV requests that would overwrite our cache with
 * the wrong value.</p>
 */
@EventBusSubscriber(modid = Spotter.MODID, value = Dist.CLIENT)
public final class FovTracker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static double lastFov = 70.0;
    private static int debugLogCount = 0;

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return;
        double newFov = event.getFOV();
        if (Math.abs(newFov - lastFov) > 0.01 && debugLogCount < 8) {
            LOGGER.info("[Spotter FovTracker] FOV {} -> {} (configured={})",
                    String.format("%.2f", lastFov),
                    String.format("%.2f", newFov),
                    event.usedConfiguredFov());
            debugLogCount++;
        }
        lastFov = newFov;
    }

    public static double getEffectiveFov() {
        return lastFov;
    }

    private FovTracker() {}
}
