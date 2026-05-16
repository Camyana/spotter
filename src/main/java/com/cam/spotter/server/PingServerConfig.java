package com.cam.spotter.server;

import net.neoforged.neoforge.common.ModConfigSpec;

public class PingServerConfig {
    public static final ModConfigSpec.DoubleValue MAX_PING_DISTANCE;
    public static final ModConfigSpec.IntValue BROADCAST_RADIUS_CHUNKS;
    public static final ModConfigSpec.IntValue COOLDOWN_TICKS;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("server");
        MAX_PING_DISTANCE = builder
                .comment("Maximum distance, in blocks, from a player's eye to the ping target. Pings further than this are rejected.")
                .defineInRange("max_ping_distance", 256.0, 16.0, 4096.0);
        BROADCAST_RADIUS_CHUNKS = builder
                .comment("Chunk radius used to choose which players receive a ping. -1 means use the server's view distance.")
                .defineInRange("broadcast_radius_chunks", -1, -1, 64);
        COOLDOWN_TICKS = builder
                .comment("Minimum ticks between pings from the same player (20 ticks = 1 second). 0 disables the cooldown.")
                .defineInRange("cooldown_ticks", 10, 0, 600);
        builder.pop();
        SPEC = builder.build();
    }
}
