package com.cam.spotter.server;

import com.cam.spotter.network.PlacePingPayload;
import com.cam.spotter.network.SyncPingPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerPingHandler {
    private static final ConcurrentHashMap<UUID, Long> LAST_PING_TICK = new ConcurrentHashMap<>();

    public static void handlePlacePing(PlacePingPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sender)) return;

        double maxDistance = PingServerConfig.MAX_PING_DISTANCE.get();
        Vec3 pingPos = payload.position();
        if (sender.getEyePosition().distanceTo(pingPos) > maxDistance + 4.0) return;

        int cooldown = PingServerConfig.COOLDOWN_TICKS.get();
        long now = sender.serverLevel().getGameTime();
        if (cooldown > 0) {
            Long last = LAST_PING_TICK.get(sender.getUUID());
            if (last != null && now - last < cooldown) return;
        }
        LAST_PING_TICK.put(sender.getUUID(), now);

        ServerLevel level = sender.serverLevel();
        int configured = PingServerConfig.BROADCAST_RADIUS_CHUNKS.get();
        int broadcastRadius = configured < 0
                ? sender.getServer().getPlayerList().getViewDistance()
                : configured;
        int pingChunkX = ((int) Math.floor(pingPos.x)) >> 4;
        int pingChunkZ = ((int) Math.floor(pingPos.z)) >> 4;

        SyncPingPayload sync = new SyncPingPayload(
                sender.getUUID(),
                sender.getGameProfile().getName(),
                payload.hitType(),
                pingPos,
                payload.blockPos(),
                payload.entityId(),
                payload.color() & 0xFFFFFF
        );

        for (ServerPlayer player : level.players()) {
            int dx = Math.abs(player.chunkPosition().x - pingChunkX);
            int dz = Math.abs(player.chunkPosition().z - pingChunkZ);
            if (Math.max(dx, dz) <= broadcastRadius) {
                PacketDistributor.sendToPlayer(player, sync);
            }
        }
    }
}
