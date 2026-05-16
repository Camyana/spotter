package com.cam.pingmod.client;

import com.cam.pingmod.client.gui.PingConfigScreen;
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
