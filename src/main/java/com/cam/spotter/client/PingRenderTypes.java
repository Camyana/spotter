package com.cam.spotter.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class PingRenderTypes {
    public static final RenderType GHOST_BLOCK = RenderType.create(
            "spotter:ghost_block",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            1024,
            true, false,
            RenderType.CompositeState.builder()
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(true)
    );

    /**
     * Flat quad rendered with {@code POSITION_COLOR_SHADER} + translucent blending.
     * The translucent pass preserves the supplied body color (shader packs only add
     * a small bloom halo around it — they don't atmospheric-tint it the way they do
     * the solid pass, where dark grays get fogged brown). Pair with a high alpha
     * (~220) and an explicit flush before drawing the text on top.
     */
    public static final RenderType TEXT_BACKDROP = RenderType.create(
            "spotter:text_backdrop",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false)
    );

    public static final RenderType GHOST_BEAM = RenderType.create(
            "spotter:ghost_beam",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            1536,
            false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_BEACON_BEAM_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png"),
                            false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false)
    );

    private static final java.util.Map<ResourceLocation, RenderType> SKIN_TYPES = new java.util.concurrent.ConcurrentHashMap<>();

    public static RenderType skinSeeThrough(ResourceLocation tex) {
        return SKIN_TYPES.computeIfAbsent(tex, t -> RenderType.create(
                "spotter:skin_" + t.toString().replace(':', '_').replace('/', '_'),
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                256, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_TEXT_SEE_THROUGH_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(t, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false)
        ));
    }

    private PingRenderTypes() {}
}
