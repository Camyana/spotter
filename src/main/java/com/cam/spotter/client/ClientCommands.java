package com.cam.spotter.client;

import com.cam.spotter.Spotter;
import com.cam.spotter.client.gui.PingConfigScreen;
import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = Spotter.MODID, value = Dist.CLIENT)
public class ClientCommands {
    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("pingsettings")
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.tell(() -> mc.setScreen(new PingConfigScreen(null)));
                            return Command.SINGLE_SUCCESS;
                        })
        );
    }
}
