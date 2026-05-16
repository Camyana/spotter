package com.cam.pingmod.client;

import com.cam.pingmod.PingMod;
import com.cam.pingmod.client.gui.PingConfigScreen;
import com.cam.pingmod.network.HitType;
import com.cam.pingmod.network.PlacePingPayload;
import com.cam.pingmod.network.SyncPingPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = PingMod.MODID, value = Dist.CLIENT)
public class ClientEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final long COOLDOWN_MS = 2000;
    private static final double ITEM_HITBOX_INFLATE = 0.3;

    private static long lastPingMs = 0;
    /**
     * True once we've fallen back to client-only mode on this connection. We
     * keep using the fallback for subsequent pings so the player gets
     * consistent local feedback, and we suppress repeated notifications.
     */
    private static boolean clientOnlyMode = false;
    private static boolean notifiedClientOnly = false;

    // Edge-detect rising key state so a held keybind only fires once per press.
    private static boolean placePingPressed = false;
    private static boolean openSettingsPressed = false;

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Each new connection starts fresh — re-test whether the server has the mod.
        clientOnlyMode = false;
        notifiedClientOnly = false;
        ClientPingHandler.clearAll();
    }

    public static float getCooldownProgress() {
        long elapsed = System.currentTimeMillis() - lastPingMs;
        if (elapsed >= COOLDOWN_MS) return 1.0f;
        return elapsed / (float) COOLDOWN_MS;
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ClientPingHandler.tickPings();

        // Place ping: fire only on the rising edge of the key state. Drain any
        // queued click events so they can't fire later (e.g. after a menu closes).
        boolean placeDown = PingKeyMappings.PLACE_PING.isDown();
        if (placeDown && !placePingPressed) {
            tryPlacePing(mc);
        }
        placePingPressed = placeDown;
        while (PingKeyMappings.PLACE_PING.consumeClick()) { /* drain */ }

        // Open settings: same edge-only behavior.
        boolean settingsDown = PingKeyMappings.OPEN_SETTINGS.isDown();
        if (settingsDown && !openSettingsPressed) {
            mc.setScreen(new PingConfigScreen(null));
        }
        openSettingsPressed = settingsDown;
        while (PingKeyMappings.OPEN_SETTINGS.consumeClick()) { /* drain */ }
    }

    private static void tryPlacePing(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastPingMs < COOLDOWN_MS) return;
        placePing(mc);
        lastPingMs = now;
    }

    private static void placePing(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 reachEnd = eye.add(look.scale(PingMod.MAX_PING_DISTANCE));

        ClipContext ctx = new ClipContext(eye, reachEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
        BlockHitResult blockHit = mc.level.clip(ctx);

        double maxDistSq = blockHit.getType() == HitResult.Type.MISS
                ? PingMod.MAX_PING_DISTANCE * PingMod.MAX_PING_DISTANCE
                : blockHit.getLocation().distanceToSqr(eye);

        AABB reachBox = player.getBoundingBox().expandTowards(look.scale(PingMod.MAX_PING_DISTANCE)).inflate(1.0);

        ItemEntity bestItem = null;
        double bestItemDistSq = maxDistSq;
        Vec3 bestItemHit = null;
        List<ItemEntity> items = mc.level.getEntitiesOfClass(ItemEntity.class, reachBox, e -> !e.isSpectator());
        for (ItemEntity item : items) {
            AABB inflated = item.getBoundingBox().inflate(ITEM_HITBOX_INFLATE);
            Optional<Vec3> hit = inflated.clip(eye, reachEnd);
            if (hit.isPresent()) {
                double distSq = eye.distanceToSqr(hit.get());
                if (distSq < bestItemDistSq) {
                    bestItemDistSq = distSq;
                    bestItem = item;
                    bestItemHit = hit.get();
                }
            }
        }

        int color = PingClientConfig.COLOR.get() & 0xFFFFFF;

        PlacePingPayload payload;
        if (bestItem != null) {
            payload = new PlacePingPayload(HitType.ENTITY, bestItemHit, null, bestItem.getId(), color);
        } else {
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    player, eye, reachEnd, reachBox,
                    e -> !e.isSpectator() && e.isPickable() && !(e instanceof ItemEntity),
                    maxDistSq
            );
            if (entityHit != null) {
                payload = new PlacePingPayload(
                        HitType.ENTITY,
                        entityHit.getLocation(),
                        null,
                        entityHit.getEntity().getId(),
                        color
                );
            } else if (blockHit.getType() == HitResult.Type.BLOCK) {
                payload = new PlacePingPayload(
                        HitType.BLOCK,
                        blockHit.getLocation(),
                        blockHit.getBlockPos(),
                        -1,
                        color
                );
            } else {
                payload = new PlacePingPayload(HitType.MISS, reachEnd, null, -1, color);
            }
        }

        dispatch(mc, payload);
    }

    /**
     * Routes the payload either to the server (normal play) or — if the server
     * doesn't have the mod — locally so the player still sees their own ping.
     * Once we detect the server lacks the mod, we stick to local-only for this
     * connection to avoid spamming the server with packets it doesn't accept.
     */
    private static void dispatch(Minecraft mc, PlacePingPayload payload) {
        if (clientOnlyMode) {
            renderLocalOnly(mc, payload);
            return;
        }
        try {
            PacketDistributor.sendToServer(payload);
        } catch (Throwable t) {
            LOGGER.warn("[PingMod] Server doesn't accept the ping channel — switching to client-only mode for this connection.", t);
            clientOnlyMode = true;
            notifyClientOnlyOnce(mc);
            renderLocalOnly(mc, payload);
        }
    }

    private static void renderLocalOnly(Minecraft mc, PlacePingPayload payload) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        SyncPingPayload sync = new SyncPingPayload(
                player.getUUID(),
                player.getName().getString(),
                payload.hitType(),
                payload.position(),
                payload.blockPos(),
                payload.entityId(),
                payload.color()
        );
        ClientPingHandler.addLocalPing(sync);
    }

    private static void notifyClientOnlyOnce(Minecraft mc) {
        if (notifiedClientOnly) return;
        notifiedClientOnly = true;
        LocalPlayer player = mc.player;
        if (player == null) return;
        player.displayClientMessage(
                Component.literal("[Ping Mod] Server doesn't have Ping Mod installed — your pings will only be visible to you.")
                        .withStyle(ChatFormatting.YELLOW),
                false
        );
    }
}
