package com.cam.pingmod;

import com.cam.pingmod.network.PlacePingPayload;
import com.cam.pingmod.network.SyncPingPayload;
import com.cam.pingmod.server.PingServerConfig;
import com.cam.pingmod.server.ServerPingHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(PingMod.MODID)
public class PingMod {
    public static final String MODID = "pingmod";

    public static final int PING_LIFETIME_TICKS = 120;
    /**
     * Client-side raycast reach when scanning for a ping target. The server's
     * authoritative cap is {@link com.cam.pingmod.server.PingServerConfig#MAX_PING_DISTANCE},
     * which may be lower.
     */
    public static final double MAX_PING_DISTANCE = 256.0;

    public PingMod(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(PingMod::registerPayloads);

        container.registerConfig(ModConfig.Type.SERVER, PingServerConfig.SPEC);

        if (FMLEnvironment.dist.isClient()) {
            container.registerConfig(ModConfig.Type.CLIENT, com.cam.pingmod.client.PingClientConfig.SPEC);
            com.cam.pingmod.client.ClientSetup.registerConfigScreen(container);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();

        registrar.playToServer(
                PlacePingPayload.TYPE,
                PlacePingPayload.STREAM_CODEC,
                ServerPingHandler::handlePlacePing
        );

        registrar.playToClient(
                SyncPingPayload.TYPE,
                SyncPingPayload.STREAM_CODEC,
                PingMod::handleSyncOnClient
        );
    }

    private static void handleSyncOnClient(SyncPingPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            com.cam.pingmod.client.ClientPingHandler.handleSyncPing(payload, context);
        }
    }
}
