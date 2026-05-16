package com.cam.spotter.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public class PingClientConfig {
    public enum HologramPosition { NONE, ABOVE, LEFT, RIGHT }

    public static final ModConfigSpec.IntValue COLOR;
    public static final ModConfigSpec.BooleanValue SHOW_BEAM;
    public static final ModConfigSpec.EnumValue<HologramPosition> HOLOGRAM_POSITION;
    public static final ModConfigSpec.BooleanValue SHOW_TARGET_NAME;
    public static final ModConfigSpec.BooleanValue SHOW_PLAYER_HEAD;
    public static final ModConfigSpec.BooleanValue SHOW_OWNER_NAME;
    public static final ModConfigSpec.BooleanValue SHOW_DISTANCE;
    public static final ModConfigSpec.BooleanValue SHOW_HOSTILE_INDICATOR;
    public static final ModConfigSpec.DoubleValue TEXT_SCALE;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("display");
        COLOR = builder
                .comment("Color of YOUR pings, visible to everyone (RGB hex packed as int, 0xRRGGBB). Default 0x00FFFF (cyan).")
                .defineInRange("color", 0x00FFFF, 0, 0xFFFFFF);
        SHOW_BEAM = builder
                .comment("Show the 2D leader line connecting the label down to the pinged spot.")
                .define("show_beam", true);
        HOLOGRAM_POSITION = builder
                .comment("Position of the rotating block/item hologram relative to the label.")
                .defineEnum("hologram_position", HologramPosition.RIGHT);
        SHOW_TARGET_NAME = builder
                .comment("Show the target name line (e.g. 'Grass Block').")
                .define("show_target_name", true);
        SHOW_PLAYER_HEAD = builder
                .comment("Show the pinger's skin face icon next to their name.")
                .define("show_player_head", true);
        SHOW_OWNER_NAME = builder
                .comment("Show the pinger's name line.")
                .define("show_owner_name", true);
        SHOW_DISTANCE = builder
                .comment("Show the distance line (e.g. '5m').")
                .define("show_distance", true);
        SHOW_HOSTILE_INDICATOR = builder
                .comment("Highlight hostile mobs with a red border around the hologram panel instead of the ping color.")
                .define("show_hostile_indicator", true);
        TEXT_SCALE = builder
                .comment("Scale multiplier for the on-screen label (1.0 = default size).")
                .defineInRange("text_scale", 1.0, 0.5, 2.5);
        builder.pop();
        SPEC = builder.build();
    }
}
