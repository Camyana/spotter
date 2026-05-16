package com.cam.pingmod.client;

import com.cam.pingmod.network.HitType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ActivePing {
    public final UUID owner;
    public final String ownerName;
    public final HitType hitType;
    public final Vec3 position;
    @Nullable public final BlockPos blockPos;
    public final int entityId;
    public final int color;
    public int ticksRemaining;
    public final int totalTicks;
    /** Set to true when the target despawned (item picked up, mob killed, block broken). */
    public boolean closing;
    /** ticksRemaining at the moment closing was triggered, so the renderer can ease the close animation. */
    public int closingFromTicks;

    public ActivePing(UUID owner, String ownerName, HitType hitType, Vec3 position,
                      @Nullable BlockPos blockPos, int entityId, int color, int lifetime) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.hitType = hitType;
        this.position = position;
        this.blockPos = blockPos;
        this.entityId = entityId;
        this.color = color;
        this.ticksRemaining = lifetime;
        this.totalTicks = lifetime;
        this.closing = false;
        this.closingFromTicks = 0;
    }

    /** 0.0 → 1.0 progress through the close animation (only meaningful while {@link #closing}). */
    public float getCloseProgress(float partialTick) {
        if (!closing || closingFromTicks <= 0) return 0f;
        float elapsed = closingFromTicks - (ticksRemaining - partialTick);
        return Math.max(0f, Math.min(1f, elapsed / closingFromTicks));
    }

    public float getAlpha(float partialTick) {
        float life = ticksRemaining - partialTick;
        if (life <= 0) return 0.0f;
        float fadeOut = Math.min(1.0f, life / 20.0f);
        float elapsed = (totalTicks - ticksRemaining) + partialTick;
        float fadeIn = Math.min(1.0f, Math.max(0.0f, elapsed) / 4.0f);
        return Math.max(0.0f, Math.min(fadeIn, fadeOut));
    }

    public float getIntroProgress(float partialTick, float introTicks) {
        float elapsed = (totalTicks - ticksRemaining) + partialTick;
        if (elapsed >= introTicks) return 1.0f;
        if (elapsed <= 0) return 0.0f;
        return elapsed / introTicks;
    }
}
