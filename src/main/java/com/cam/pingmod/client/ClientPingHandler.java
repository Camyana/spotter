package com.cam.pingmod.client;

import com.cam.pingmod.PingMod;
import com.cam.pingmod.network.HitType;
import com.cam.pingmod.network.SyncPingPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClientPingHandler {
    private static final List<ActivePing> ACTIVE_PINGS = new ArrayList<>();

    public static void handleSyncPing(SyncPingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> addLocalPing(payload));
    }

    /**
     * Inserts a ping straight into the active list — used both by the network
     * handler (via {@link #handleSyncPing}) and by the client-only fallback when
     * the server doesn't have the mod installed.
     */
    public static void addLocalPing(SyncPingPayload payload) {
        ACTIVE_PINGS.removeIf(p -> p.owner.equals(payload.owner()));
        ACTIVE_PINGS.add(new ActivePing(
                payload.owner(),
                payload.ownerName(),
                payload.hitType(),
                payload.position(),
                payload.blockPos(),
                payload.entityId(),
                payload.color(),
                PingMod.PING_LIFETIME_TICKS
        ));
        playPingSound(payload.owner());
    }

    /** Clears all active pings — called when leaving a world to avoid stale state on reconnect. */
    public static void clearAll() {
        ACTIVE_PINGS.clear();
    }

    private static void playPingSound(UUID owner) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        SoundManager sm = mc.getSoundManager();
        float pitch = ownerPitch(owner);
        sm.play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch, 0.45f));
        sm.playDelayed(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), pitch, 0.35f), 1);
    }

    private static float ownerPitch(UUID owner) {
        int hash = owner.hashCode();
        float normalized = ((hash & 0xFFFF) / 65535.0f);
        return 1.4f + normalized * 0.6f;
    }

    /** Lifetime (in ticks) the ping fades out across once its target despawns. */
    private static final int CLOSE_ANIM_TICKS = 16;
    /**
     * Ticks of grace at the start of a ping's life before we test whether the
     * target has gone missing — covers the case where the entity isn't on the
     * client yet (chunk just loaded, etc.).
     */
    private static final int CLOSE_CHECK_GRACE_TICKS = 4;

    public static void tickPings() {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;

        ACTIVE_PINGS.removeIf(ping -> {
            if (!ping.closing && level != null
                    && (ping.totalTicks - ping.ticksRemaining) >= CLOSE_CHECK_GRACE_TICKS
                    && isTargetMissing(level, ping)) {
                ping.closing = true;
                ping.closingFromTicks = CLOSE_ANIM_TICKS;
                ping.ticksRemaining = Math.min(ping.ticksRemaining, CLOSE_ANIM_TICKS);
                playCloseSound();
            }
            ping.ticksRemaining--;
            return ping.ticksRemaining <= 0;
        });
    }

    /** True if the entity referenced by the ping is no longer in the world. */
    private static boolean isTargetMissing(Level level, ActivePing ping) {
        if (ping.hitType == HitType.ENTITY) {
            return level.getEntity(ping.entityId) == null;
        }
        return false;
    }

    private static void playCloseSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.45f, 0.2f));
    }

    public static List<ActivePing> getActivePings() {
        return ACTIVE_PINGS;
    }
}
