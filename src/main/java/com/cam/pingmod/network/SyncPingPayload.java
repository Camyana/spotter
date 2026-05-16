package com.cam.pingmod.network;

import com.cam.pingmod.PingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record SyncPingPayload(
        UUID owner,
        String ownerName,
        HitType hitType,
        Vec3 position,
        @Nullable BlockPos blockPos,
        int entityId,
        int color
) implements CustomPacketPayload {

    public static final Type<SyncPingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PingMod.MODID, "sync_ping")
    );

    public static final StreamCodec<FriendlyByteBuf, SyncPingPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.owner);
                buf.writeUtf(p.ownerName);
                buf.writeByte(p.hitType.toByte());
                buf.writeDouble(p.position.x);
                buf.writeDouble(p.position.y);
                buf.writeDouble(p.position.z);
                if (p.hitType == HitType.BLOCK) {
                    buf.writeBlockPos(p.blockPos);
                } else if (p.hitType == HitType.ENTITY) {
                    buf.writeVarInt(p.entityId);
                }
                buf.writeInt(p.color);
            },
            buf -> {
                UUID owner = buf.readUUID();
                String name = buf.readUtf();
                HitType type = HitType.fromByte(buf.readByte());
                Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                BlockPos bp = type == HitType.BLOCK ? buf.readBlockPos() : null;
                int eid = type == HitType.ENTITY ? buf.readVarInt() : -1;
                int color = buf.readInt();
                return new SyncPingPayload(owner, name, type, pos, bp, eid, color);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
