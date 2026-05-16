package com.cam.spotter.client;

import com.cam.spotter.client.gui.PingConfigScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public class ClientSetup {
    public static void registerConfigScreen(ModContainer container) {
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (mc, parent) -> new PingConfigScreen(parent)
        );
    }
}
