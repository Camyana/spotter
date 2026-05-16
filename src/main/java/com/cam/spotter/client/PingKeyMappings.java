package com.cam.spotter.client;

import com.cam.spotter.Spotter;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Spotter.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class PingKeyMappings {
    public static final KeyMapping PLACE_PING = new KeyMapping(
            "key.spotter.place_ping",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            "key.categories.spotter"
    );

    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.spotter.open_settings",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            "key.categories.spotter"
    );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(PLACE_PING);
        event.register(OPEN_SETTINGS);
    }
}
