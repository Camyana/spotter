package com.cam.pingmod.network;

import com.cam.pingmod.PingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public record PlacePingPayload(
        HitType hitType,
        Vec3 position,
        @Nullable BlockPos blockPos,
        int entityId,
        int color
) implements CustomPacketPayload {

    public static final Type<PlacePingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PingMod.MODID, "place_ping")
    );

    public static final StreamCodec<FriendlyByteBuf, PlacePingPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
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
                HitType type = HitType.fromByte(buf.readByte());
                Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                BlockPos bp = type == HitType.BLOCK ? buf.readBlockPos() : null;
                int eid = type == HitType.ENTITY ? buf.readVarInt() : -1;
                int color = buf.readInt();
                return new PlacePingPayload(type, pos, bp, eid, color);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
