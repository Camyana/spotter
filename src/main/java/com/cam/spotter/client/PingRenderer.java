package com.cam.spotter.client;

import com.cam.spotter.Spotter;
import com.cam.spotter.network.HitType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = Spotter.MODID, value = Dist.CLIENT)
public class PingRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static int lastLoggedCount = -1;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        List<ActivePing> pings = ClientPingHandler.getActivePings();
        if (pings.size() != lastLoggedCount) {
            LOGGER.info("[Spotter] Renderer: now have {} active pings", pings.size());
            lastLoggedCount = pings.size();
        }
        if (pings.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ClientLevel level = mc.level;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        boolean any = false;
        for (ActivePing ping : pings) {
            float alpha = ping.getAlpha(partialTick);
            if (alpha <= 0) continue;
            // Entity pings are visualised through vanilla MC's glowing-outline shader
            // (forced on via MinecraftMixin#shouldEntityAppearGlowing, coloured via
            // EntityMixin#getTeamColor) — no need for a separate cuboid wireframe.
            if (ping.hitType == HitType.BLOCK && ping.blockPos != null) {
                renderBlockOutline(level, ping, alpha, pose, buffers, camPos, partialTick);
                any = true;
            }
        }
        if (any) {
            buffers.endBatch(RenderType.lines());
        }
        buffers.endBatch();
    }

    /** Anchor position in the world for a ping — entity center, block center+top, or raw position. */
    public static Vec3 anchorOf(ClientLevel level, ActivePing ping, float partialTick) {
        if (ping.hitType == HitType.ENTITY) {
            Entity e = level.getEntity(ping.entityId);
            if (e != null) return e.getPosition(partialTick);
        }
        if (ping.hitType == HitType.BLOCK && ping.blockPos != null) {
            BlockPos bp = ping.blockPos;
            return new Vec3(bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5);
        }
        return ping.position;
    }

    private static void renderBlockOutline(ClientLevel level, ActivePing ping, float alpha,
                                           PoseStack pose, MultiBufferSource buffers,
                                           Vec3 camPos, float partialTick) {
        BlockPos bp = ping.blockPos;
        if (bp == null) return;
        BlockState state = level.getBlockState(bp);
        VoxelShape shape = state.getShape(level, bp);
        AABB bounds = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();

        int rgb = ping.color & 0xFFFFFF;
        float rf = ((rgb >> 16) & 0xFF) / 255.0f;
        float gf = ((rgb >> 8) & 0xFF) / 255.0f;
        float bf = (rgb & 0xFF) / 255.0f;
        float a = alpha * Math.max(0.0f, easeOutCubic(ping.getIntroProgress(partialTick, 5.0f)));

        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(bp.getX() - camPos.x, bp.getY() - camPos.y, bp.getZ() - camPos.z);
        LevelRenderer.renderLineBox(pose, consumer,
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                rf, gf, bf, a);
        pose.popPose();
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }
}
