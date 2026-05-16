package com.cam.spotter.client;

import com.cam.spotter.Spotter;
import com.cam.spotter.network.HitType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = Spotter.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class PingDirectionOverlay {
    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(Spotter.MODID, "direction_arrows");

    private static final int ARROW_W = 12;
    private static final int ARROW_H = 14;
    private static final int MARGIN = 14;
    private static final int TEXT_PAD = 6;
    private static final int VERTICAL_STEP = 36;

    // On-screen widget layout: [label][hologram] side-by-side, leader line under it.
    private static final int LABEL_LINE_H = 10;
    private static final int LABEL_HEIGHT = 28;
    private static final int LABEL_BG_PAD_X = 5;
    private static final int LABEL_BG_PAD_Y = 4;
    private static final int SKIN_FACE = 8;
    private static final int SKIN_TEX_SIZE = 64;

    /** RGB used for panels/leader when a ping is closing (target despawned). */
    private static final int CLOSING_COLOR = 0x707782;
    /** Padding inside the hologram's backdrop panel, between panel edge and hologram. */
    private static final int HOLO_BG_PAD = 4;
    /**
     * The rendered cube projects up to ~1.57× its edge length on screen when both
     * the X tilt (30°) and the animated Y rotation pass through 45°. Multiply the
     * usable interior by this factor to find a cube size whose projected bounds
     * still fit inside the box, with a small safety margin.
     */
    private static final float HOLO_FIT_FACTOR = 0.62f;
    private static final int HOLO_MIN_INNER = 12;
    /** Default size of the holo panel when no label exists to anchor against. */
    private static final int HOLO_BOX_SIZE = 36;
    /** Gap between the label's panel edge and the hologram's panel edge. */
    private static final int HOLO_LABEL_GAP = 4;

    // 2D leader line drawn in screen space from the bottom of the hologram down
    // to the projected anchor. Length scales with distance.
    private static final int BEAM_CORE_WIDTH = 2;
    private static final int BEAM_GLOW_WIDTH = 4;
    private static final int LEADER_LEN_BASE = 18;
    private static final int LEADER_LEN_MAX = 130;
    private static final double LEADER_LEN_PER_BLOCK = 0.55;
    private static final int FULL_LIGHT = 0xF000F0;

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER_ID, PingDirectionOverlay::render);
    }

    public static void render(GuiGraphics gui, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        List<ActivePing> pings = ClientPingHandler.getActivePings();
        if (pings.isEmpty()) return;

        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        LocalPlayer player = mc.player;
        Vec3 playerPos = player.getEyePosition(partialTick);
        Vec3 lookVec = player.getViewVector(partialTick).normalize();
        Camera camera = mc.gameRenderer.getMainCamera();

        // Effective rendering FOV captured from ViewportEvent.ComputeFov — includes
        // sprint/zoom/speed-effect multipliers. Using the raw options.fov() setting
        // here drifts whenever those modifiers are active, which makes on-screen
        // labels slide off their anchors as the player moves.
        double fov = FovTracker.getEffectiveFov();
        double cosHalfFov = Math.cos(Math.toRadians(fov / 2));

        int screenW = gui.guiWidth();
        int screenH = gui.guiHeight();

        Font font = mc.font;
        int leftCount = 0;
        int rightCount = 0;

        boolean showBeam = PingClientConfig.SHOW_BEAM.get();
        PingClientConfig.HologramPosition holoPos = PingClientConfig.HOLOGRAM_POSITION.get();
        boolean showTargetName = PingClientConfig.SHOW_TARGET_NAME.get();
        boolean showPlayerHead = PingClientConfig.SHOW_PLAYER_HEAD.get();
        boolean showOwnerName = PingClientConfig.SHOW_OWNER_NAME.get();
        boolean showDistance = PingClientConfig.SHOW_DISTANCE.get();
        float textScale = (float) PingClientConfig.TEXT_SCALE.get().doubleValue();
        boolean hasAnyLabelLine = showTargetName || showOwnerName || showDistance;

        for (ActivePing ping : pings) {
            float alpha = ping.getAlpha(partialTick);
            if (alpha <= 0) continue;

            Vec3 anchor = getAnchorPos(mc.level, ping, partialTick);
            Vec3 toPing = anchor.subtract(playerPos);
            if (toPing.lengthSqr() < 0.01) continue;
            Vec3 toPingNorm = toPing.normalize();

            double dot = lookVec.dot(toPingNorm);
            int alphaByte = Math.max(80, (int) (alpha * 255)) & 0xFF;

            // On-screen treatment: project the anchor (the actual pinged spot) and
            // build the widget in screen space above it. Label sits centered on the
            // leader line; hologram is placed relative to the label per config.
            if (dot > 0) {
                ProjectedPoint pAnchor = tryProject(camera, anchor, screenW, screenH, fov);
                if (pAnchor != null && pAnchor.onScreen(screenW, screenH)) {
                    double distance = playerPos.distanceTo(anchor);
                    int ax = (int) Math.round(pAnchor.x);
                    int ay = (int) Math.round(pAnchor.y);

                    int leaderLength = Math.min(
                            LEADER_LEN_MAX,
                            LEADER_LEN_BASE + (int) (distance * LEADER_LEN_PER_BLOCK));

                    // Build sample strings from the ping + level.
                    String targetName = getTargetName(mc.level, ping).getString();
                    String distStr = (int) Math.round(distance) + "m";
                    ResourceLocation skinTex = (showOwnerName && showPlayerHead)
                            ? getPlayerSkin(ping.owner) : null;
                    // Closing pings (target despawned) render in muted grey to
                    // signal the ping is on its way out.
                    int displayColor = ping.closing ? CLOSING_COLOR : ping.color;

                    LabelMetrics lm = hasAnyLabelLine
                            ? computeLabelMetrics(font, targetName, ping.ownerName, distStr,
                                    showTargetName, showOwnerName, showPlayerHead, showDistance,
                                    skinTex, textScale)
                            : null;

                    int labelBottomY = ay - leaderLength;
                    int labelTopY = lm != null ? labelBottomY - lm.scaledTotalH : labelBottomY;
                    int labelCenterY = (labelTopY + labelBottomY) / 2;
                    int labelBgLeft = lm != null ? ax - lm.scaledTotalW / 2 : ax;
                    int labelBgRight = lm != null ? ax + lm.scaledTotalW / 2 : ax;

                    if (showBeam) {
                        int beamTopY = lm != null ? labelBottomY : ay - leaderLength;
                        draw2DBeamVertical(gui, ax, beamTopY, ay, displayColor, alphaByte);
                    }

                    if (holoPos != PingClientConfig.HologramPosition.NONE) {
                        // Box matches the label's height when there's a label so they
                        // read as visually paired; falls back to HOLO_BOX_SIZE alone.
                        int boxSize = lm != null ? lm.scaledTotalH : HOLO_BOX_SIZE;
                        int innerSize = holoInnerSize(boxSize);
                        int half = boxSize / 2;
                        int hcx, hcy;
                        switch (holoPos) {
                            case RIGHT:
                                hcx = (lm != null ? labelBgRight : ax) + HOLO_LABEL_GAP + half;
                                hcy = (lm != null ? labelCenterY : ay - leaderLength - half);
                                break;
                            case LEFT:
                                hcx = (lm != null ? labelBgLeft : ax) - HOLO_LABEL_GAP - half;
                                hcy = (lm != null ? labelCenterY : ay - leaderLength - half);
                                break;
                            case ABOVE:
                                hcx = ax;
                                hcy = (lm != null ? labelTopY : ay - leaderLength)
                                        - HOLO_LABEL_GAP - half;
                                break;
                            default:
                                hcx = ax;
                                hcy = ay - leaderLength - half;
                                break;
                        }
                        drawWidgetPanel(gui, hcx - half, hcy - half, hcx + half, hcy + half,
                                displayColor, alphaByte);
                        renderHudHologramForPing(gui, mc, ping, hcx, hcy, innerSize,
                                gameTimeWithPartial(mc, partialTick), partialTick);
                    }

                    if (lm != null) {
                        renderLabelContent(gui, font, ax, labelCenterY, alphaByte, displayColor,
                                textScale, targetName, ping.ownerName, distStr, skinTex,
                                showTargetName, showOwnerName, showPlayerHead, showDistance, lm);
                    }

                    continue;
                }
            }

            // Off-screen (or label disabled): direction arrow at the screen edge.
            if (dot > cosHalfFov) continue;

            Vec3 cross = lookVec.cross(toPingNorm);
            boolean onRight = cross.y < 0;

            int slot = onRight ? rightCount++ : leftCount++;
            int arrowY = screenH / 2 - ARROW_H / 2 + (slot - 1) * VERTICAL_STEP;
            int restingX = onRight ? screenW - MARGIN - ARROW_W : MARGIN;
            float intro = ping.getIntroProgress(partialTick, 10.0f);
            float slideEase = easeOutElastic(intro);
            int slideOffset = (int) ((1.0f - slideEase) * 120);
            double idleWobble = Math.sin(((partialTick + System.currentTimeMillis() / 50.0)) * 0.15) * 2.0;
            int arrowX = onRight ? restingX + slideOffset + (int) idleWobble : restingX - slideOffset - (int) idleWobble;

            int arrowColor = (alphaByte << 24) | (ping.color & 0xFFFFFF);
            int textColor = (alphaByte << 24) | 0xFFFFFF;
            int ownerTextColor = (alphaByte << 24) | (ping.color & 0xFFFFFF);
            int bgColor = (Math.min(alphaByte, 210)) << 24;

            if (onRight) {
                drawRightArrow(gui, arrowX, arrowY, ARROW_W, ARROW_H, arrowColor);
            } else {
                drawLeftArrow(gui, arrowX, arrowY, ARROW_W, ARROW_H, arrowColor);
            }

            Component nameLine = getTargetName(mc.level, ping);
            String name = nameLine.getString();
            String owner = "◆ " + ping.ownerName;
            double distance = anchor.distanceTo(playerPos);
            String dist = (int) Math.round(distance) + "m";
            int nameWidth = font.width(name);
            int ownerWidth = font.width(owner);
            int distWidth = font.width(dist);
            int textBlockW = Math.max(Math.max(nameWidth, ownerWidth), distWidth);
            int textBlockH = LABEL_HEIGHT;

            int textX = onRight
                    ? arrowX - TEXT_PAD - textBlockW
                    : arrowX + ARROW_W + TEXT_PAD;
            int textY = arrowY + (ARROW_H / 2) - (textBlockH / 2);

            int bgPad = 2;
            gui.fill(textX - bgPad, textY - bgPad,
                    textX + textBlockW + bgPad, textY + textBlockH + bgPad, bgColor);

            int distAlpha = (alphaByte * 3) / 4;
            int distColor = (distAlpha << 24) | 0xBBBBBB;

            gui.drawString(font, name, textX, textY, textColor, true);
            gui.drawString(font, owner, textX, textY + LABEL_LINE_H, ownerTextColor, true);
            gui.drawString(font, dist, textX, textY + LABEL_LINE_H * 2, distColor, true);
        }
    }

    /** Geometry pre-computation for the on-screen label, accounting for visible lines and text scale. */
    private record LabelMetrics(int unscaledTextW, int unscaledTotalH, int scaledTotalW, int scaledTotalH,
                                int nameW, int ownerLineW, int distW,
                                @Nullable ResourceLocation skinTex, boolean useHeadIcon) {}

    private static LabelMetrics computeLabelMetrics(Font font, String targetName, String ownerName, String distStr,
                                                     boolean showTargetName, boolean showOwnerName,
                                                     boolean showPlayerHead, boolean showDistance,
                                                     @Nullable ResourceLocation skinTex, float textScale) {
        boolean useHeadIcon = showOwnerName && showPlayerHead;
        int nameW = showTargetName ? font.width(targetName) : 0;
        int ownerLineW = 0;
        if (showOwnerName) {
            if (skinTex != null) {
                ownerLineW = SKIN_FACE + 2 + font.width(ownerName);
            } else if (useHeadIcon) {
                ownerLineW = font.width("◆ " + ownerName);
            } else {
                ownerLineW = font.width(ownerName);
            }
        }
        int distW = showDistance ? font.width(distStr) : 0;

        int textW = Math.max(Math.max(nameW, ownerLineW), distW);
        int nLines = (showTargetName ? 1 : 0) + (showOwnerName ? 1 : 0) + (showDistance ? 1 : 0);
        int textH = Math.max(0, nLines * LABEL_LINE_H - 2);

        int scaledTextW = Math.round(textW * textScale);
        int scaledTotalW = scaledTextW + (LABEL_BG_PAD_X * 2);
        int scaledTextH = Math.round(textH * textScale);
        int scaledTotalH = scaledTextH + (LABEL_BG_PAD_Y * 2);

        return new LabelMetrics(textW, textH, scaledTotalW, scaledTotalH,
                nameW, ownerLineW, distW, skinTex, useHeadIcon);
    }

    /**
     * Renders the label centered on ({@code cx}, {@code cy}) using primitive inputs.
     * Used by both the live overlay and the in-settings preview.
     */
    private static void renderLabelContent(GuiGraphics gui, Font font, int cx, int cy,
                                           int alphaByte, int color, float textScale,
                                           String targetName, String ownerName, String distStr,
                                           @Nullable ResourceLocation skinTex,
                                           boolean showTargetName, boolean showOwnerName,
                                           boolean showPlayerHead, boolean showDistance,
                                           LabelMetrics lm) {
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(textScale, textScale, 1);
        pose.translate(-cx, -cy, 0);

        int textW = lm.unscaledTextW();
        int textH = lm.unscaledTotalH();
        int textHalfW = textW / 2;
        int textHalfH = textH / 2;

        int bgLeft = cx - textHalfW - LABEL_BG_PAD_X;
        int bgRight = cx + (textW - textHalfW) + LABEL_BG_PAD_X;
        int bgTop = cy - textHalfH - LABEL_BG_PAD_Y;
        int bgBottom = cy + (textH - textHalfH) + LABEL_BG_PAD_Y;

        int textColor = (alphaByte << 24) | 0xFFFFFF;
        int ownerColor = (alphaByte << 24) | (color & 0xFFFFFF);
        int distAlpha = (alphaByte * 3) / 4;
        int distColor = (distAlpha << 24) | 0xBBBBBB;

        drawWidgetPanel(gui, bgLeft, bgTop, bgRight, bgBottom, color, alphaByte);

        int lineY = bgTop + LABEL_BG_PAD_Y;
        if (showTargetName) {
            gui.drawString(font, targetName, cx - lm.nameW() / 2, lineY, textColor, true);
            lineY += LABEL_LINE_H;
        }
        if (showOwnerName) {
            int ownerLineW = lm.ownerLineW();
            int startX = cx - ownerLineW / 2;
            if (skinTex != null) {
                gui.setColor(1f, 1f, 1f, alphaByte / 255f);
                gui.blit(skinTex, startX, lineY - 1, SKIN_FACE, SKIN_FACE,
                        8f, 8f, 8, 8, SKIN_TEX_SIZE, SKIN_TEX_SIZE);
                gui.blit(skinTex, startX, lineY - 1, SKIN_FACE, SKIN_FACE,
                        40f, 8f, 8, 8, SKIN_TEX_SIZE, SKIN_TEX_SIZE);
                gui.setColor(1f, 1f, 1f, 1f);
                gui.drawString(font, ownerName, startX + SKIN_FACE + 2, lineY, ownerColor, true);
            } else if (lm.useHeadIcon()) {
                gui.drawString(font, "◆ " + ownerName, startX, lineY, ownerColor, true);
            } else {
                gui.drawString(font, ownerName, startX, lineY, ownerColor, true);
            }
            lineY += LABEL_LINE_H;
        }
        if (showDistance) {
            int distActualW = font.width(distStr);
            gui.drawString(font, distStr, cx - distActualW / 2, lineY, distColor, true);
        }

        pose.popPose();
    }

    /**
     * Project a world position to GUI-space screen coordinates. Returns null if the
     * point is behind (or extremely close to) the camera.
     */
    @Nullable
    private static ProjectedPoint tryProject(Camera camera, Vec3 worldPos, int screenW, int screenH, double fov) {
        Vec3 cameraPos = camera.getPosition();
        Vec3 relative = worldPos.subtract(cameraPos);

        Vector3f forward = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();

        double depth = relative.x * forward.x + relative.y * forward.y + relative.z * forward.z;
        if (depth <= 0.05) return null;

        double rightOffset = -(relative.x * left.x + relative.y * left.y + relative.z * left.z);
        double upOffset = relative.x * up.x + relative.y * up.y + relative.z * up.z;

        double tanHalfFovY = Math.tan(Math.toRadians(fov) / 2.0);
        double aspect = (double) screenW / screenH;

        double ndcX = rightOffset / (depth * tanHalfFovY * aspect);
        double ndcY = upOffset / (depth * tanHalfFovY);

        double screenX = (ndcX + 1.0) * 0.5 * screenW;
        double screenY = (1.0 - ndcY) * 0.5 * screenH;
        return new ProjectedPoint(screenX, screenY);
    }

    private record ProjectedPoint(double x, double y) {
        boolean onScreen(int w, int h) {
            return x >= 0 && x <= w && y >= 0 && y <= h;
        }
    }

    @Nullable
    private static ResourceLocation getPlayerSkin(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;
        net.minecraft.client.multiplayer.PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
        if (info == null) return null;
        return info.getSkin().texture();
    }

    private static float easeOutElastic(float t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        float c4 = (float) (2 * Math.PI / 3);
        return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1);
    }

    private static void drawRightArrow(GuiGraphics gui, int x, int y, int w, int h, int color) {
        double mid = (h - 1) / 2.0;
        for (int row = 0; row < h; row++) {
            double normDist = Math.abs(row - mid) / mid;
            int rightEdge = (int) ((w - 1) * (1 - normDist));
            if (rightEdge < 0) rightEdge = 0;
            gui.fill(x, y + row, x + rightEdge + 1, y + row + 1, color);
        }
    }

    private static void drawLeftArrow(GuiGraphics gui, int x, int y, int w, int h, int color) {
        double mid = (h - 1) / 2.0;
        for (int row = 0; row < h; row++) {
            double normDist = Math.abs(row - mid) / mid;
            int leftEdge = (int) ((w - 1) * normDist);
            if (leftEdge < 0) leftEdge = 0;
            gui.fill(x + leftEdge, y + row, x + w, y + row + 1, color);
        }
    }

    /**
     * Cube edge length for a hologram rendered inside a panel of {@code boxSize}.
     * Accounts for the 3D rotation's projected diagonal so the cube never clips
     * the panel border as it spins through 45° (where its on-screen bounding box
     * is largest).
     */
    private static int holoInnerSize(int boxSize) {
        int usable = Math.max(0, boxSize - HOLO_BG_PAD * 2);
        return Math.max(HOLO_MIN_INNER, Math.round(usable * HOLO_FIT_FACTOR));
    }

    /** Smooth game-time-with-partial-tick value for rotation animations. */
    private static float gameTimeWithPartial(Minecraft mc, float partialTick) {
        if (mc.level == null) return 0f;
        return (mc.level.getGameTime() + partialTick);
    }

    /** Renders a rotating 3D block hologram in HUD space at {@code (cx, cy)}. */
    private static void renderHologramBlock(GuiGraphics gui, Minecraft mc, BlockState state,
                                            int cx, int cy, int size, float rotY) {
        if (state.getRenderShape() == RenderShape.INVISIBLE) return;
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(cx, cy, 150f);
        pose.scale(size, -size, size);
        pose.mulPose(Axis.XP.rotationDegrees(30f));
        pose.mulPose(Axis.YP.rotationDegrees(rotY + 45f));
        pose.translate(-0.5f, -0.5f, -0.5f);
        MultiBufferSource.BufferSource bs = gui.bufferSource();
        mc.getBlockRenderer().renderSingleBlock(state, pose, bs, FULL_LIGHT, OverlayTexture.NO_OVERLAY);
        gui.flush();
        pose.popPose();
    }

    /** Renders a rotating item hologram in HUD space at {@code (cx, cy)}. */
    private static void renderHologramItem(GuiGraphics gui, Minecraft mc, ItemStack stack,
                                           int cx, int cy, int size, float rotY) {
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(cx, cy, 150f);
        pose.scale(size, -size, size);
        pose.mulPose(Axis.YP.rotationDegrees(rotY));
        MultiBufferSource.BufferSource bs = gui.bufferSource();
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                FULL_LIGHT, OverlayTexture.NO_OVERLAY, pose, bs, mc.level, 0);
        gui.flush();
        pose.popPose();
    }

    /** Picks the right hologram source for a real ping (block, item, or living entity). */
    private static void renderHudHologramForPing(GuiGraphics gui, Minecraft mc, ActivePing ping,
                                                 int cx, int cy, int size,
                                                 float gameTime, float partialTick) {
        if (mc.level == null) return;
        float rotY = (gameTime * 1.5f) % 360f;
        if (ping.hitType == HitType.BLOCK && ping.blockPos != null) {
            renderHologramBlock(gui, mc, mc.level.getBlockState(ping.blockPos), cx, cy, size, rotY);
        } else if (ping.hitType == HitType.ENTITY) {
            Entity ent = mc.level.getEntity(ping.entityId);
            if (ent instanceof ItemEntity item) {
                renderHologramItem(gui, mc, item.getItem(), cx, cy, size, rotY);
            } else if (ent instanceof Player player) {
                ResourceLocation skin = getPlayerSkin(player.getUUID());
                if (skin != null) {
                    renderPlayerFaceIcon(gui, skin, cx, cy, size);
                }
            } else if (ent instanceof LivingEntity living) {
                renderHologramLivingEntity(gui, living, cx, cy, size, gameTime);
            }
        }
    }

    /**
     * Renders a 3D rotating preview of a living entity inside the hologram box.
     * Uses vanilla's inventory-style entity renderer. Sized so the head and
     * upper body sit nicely inside the box.
     */
    private static void renderHologramLivingEntity(GuiGraphics gui, LivingEntity entity,
                                                   int cx, int cy, int size, float gameTime) {
        int half = size / 2;
        int x1 = cx - half;
        int y1 = cy - half;
        int x2 = cx + half;
        int y2 = cy + half;
        // Scale: pixels per world unit. Tuned so a typical 2-block mob occupies
        // roughly the full box height.
        int scale = Math.max(6, (int) (size * 0.85f));
        // Slow head-track sway so the entity looks alive instead of frozen.
        float mouseSx = cx + (float) (Math.sin(gameTime * 0.04) * 18);
        float mouseSy = cy - half - 8;
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                gui, x1, y1, x2, y2,
                scale, 0.15f,
                mouseSx, mouseSy,
                entity
        );
    }

    /** Fills the hologram box with the player's skin face (face + hat layer). */
    private static void renderPlayerFaceIcon(GuiGraphics gui, ResourceLocation skinTex,
                                              int cx, int cy, int size) {
        int faceSize = Math.max(8, size - 2);
        int x = cx - faceSize / 2;
        int y = cy - faceSize / 2;
        gui.blit(skinTex, x, y, faceSize, faceSize,
                8f, 8f, 8, 8, SKIN_TEX_SIZE, SKIN_TEX_SIZE);
        gui.blit(skinTex, x, y, faceSize, faceSize,
                40f, 8f, 8, 8, SKIN_TEX_SIZE, SKIN_TEX_SIZE);
    }

    /**
     * Renders a sample widget centered on ({@code cx}, {@code cy}) using the current
     * client configuration. Called from the settings screen so the user can see their
     * adjustments live.
     */
    public static void renderPreview(GuiGraphics gui, Font font, Minecraft mc,
                                     int cx, int cy, float partialTick) {
        if (mc.player == null) return;
        int color = PingClientConfig.COLOR.get() & 0xFFFFFF;
        int alphaByte = 255;
        UUID ownerUuid = mc.player.getUUID();
        String ownerName = mc.player.getName().getString();
        String targetName = "Cobblestone";
        String distStr = "12m";

        boolean showBeam = PingClientConfig.SHOW_BEAM.get();
        PingClientConfig.HologramPosition holoPos = PingClientConfig.HOLOGRAM_POSITION.get();
        boolean showTargetName = PingClientConfig.SHOW_TARGET_NAME.get();
        boolean showPlayerHead = PingClientConfig.SHOW_PLAYER_HEAD.get();
        boolean showOwnerName = PingClientConfig.SHOW_OWNER_NAME.get();
        boolean showDistance = PingClientConfig.SHOW_DISTANCE.get();
        float textScale = (float) PingClientConfig.TEXT_SCALE.get().doubleValue();
        boolean hasAnyLabel = showTargetName || showOwnerName || showDistance;

        ResourceLocation skinTex = (showOwnerName && showPlayerHead) ? getPlayerSkin(ownerUuid) : null;

        LabelMetrics lm = hasAnyLabel
                ? computeLabelMetrics(font, targetName, ownerName, distStr,
                        showTargetName, showOwnerName, showPlayerHead, showDistance,
                        skinTex, textScale)
                : null;

        // Layout: label centered on (cx, cy).
        int labelCenterY = cy;
        int labelHalfH = lm != null ? lm.scaledTotalH / 2 : 0;
        int labelTopY = labelCenterY - labelHalfH;
        int labelBottomY = labelCenterY + labelHalfH;
        int labelBgLeft = lm != null ? cx - lm.scaledTotalW / 2 : cx;
        int labelBgRight = lm != null ? cx + lm.scaledTotalW / 2 : cx;

        // Short stub leader below the widget for the preview.
        if (showBeam && lm != null) {
            int beamLen = 22;
            draw2DBeamVertical(gui, cx, labelBottomY, labelBottomY + beamLen, color, alphaByte);
        }

        if (holoPos != PingClientConfig.HologramPosition.NONE) {
            int boxSize = lm != null ? lm.scaledTotalH : HOLO_BOX_SIZE;
            int innerSize = holoInnerSize(boxSize);
            int half = boxSize / 2;
            int hcx, hcy;
            switch (holoPos) {
                case RIGHT:
                    hcx = labelBgRight + HOLO_LABEL_GAP + half;
                    hcy = labelCenterY;
                    break;
                case LEFT:
                    hcx = labelBgLeft - HOLO_LABEL_GAP - half;
                    hcy = labelCenterY;
                    break;
                case ABOVE:
                    hcx = cx;
                    hcy = (lm != null ? labelTopY : labelCenterY) - HOLO_LABEL_GAP - half;
                    break;
                default:
                    hcx = cx;
                    hcy = labelCenterY;
                    break;
            }
            drawWidgetPanel(gui, hcx - half, hcy - half, hcx + half, hcy + half, color, alphaByte);
            float gameTime = mc.level != null
                    ? (mc.level.getGameTime() + partialTick)
                    : (System.currentTimeMillis() / 50f);
            float rotY = (gameTime * 1.5f) % 360f;
            renderHologramBlock(gui, mc, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(),
                    hcx, hcy, innerSize, rotY);
        }

        if (lm != null) {
            renderLabelContent(gui, font, cx, labelCenterY, alphaByte, color, textScale,
                    targetName, ownerName, distStr, skinTex,
                    showTargetName, showOwnerName, showPlayerHead, showDistance, lm);
        }
    }

    /**
     * Shared widget panel — dark translucent backdrop, chamfered corners, ping-color
     * border, soft drop shadow. Used by both the label and the hologram so the two
     * panels match visually.
     */
    private static void drawWidgetPanel(GuiGraphics gui, int left, int top, int right, int bottom,
                                        int rgb, int alphaByte) {
        int color = rgb & 0xFFFFFF;
        int bgAlpha = Math.min(alphaByte, 195);
        int bgColor = (bgAlpha << 24) | 0x101418;
        int bgInnerColor = (bgAlpha << 24) | 0x1A2030;
        int borderColor = (alphaByte << 24) | color;
        int borderShadowColor = (Math.min(alphaByte, 110) << 24) | color;
        int shadowColor = (Math.min(alphaByte, 90)) << 24;

        // Drop shadow halo just outside the panel.
        gui.fill(left, top - 1, right, top, shadowColor);
        gui.fill(left, bottom, right, bottom + 1, shadowColor);
        gui.fill(left - 1, top, left, bottom, shadowColor);
        gui.fill(right, top, right + 1, bottom, shadowColor);

        // Backdrop with chamfered corners (4 corner pixels stay transparent).
        gui.fill(left + 1, top,         right - 1, top + 1,    bgColor);
        gui.fill(left,     top + 1,     right,     bottom - 1, bgColor);
        gui.fill(left + 1, bottom - 1,  right - 1, bottom,     bgColor);

        // Top-strip accent for a subtle gradient feel.
        gui.fill(left + 1, top + 1, right - 1, top + 3, bgInnerColor);

        // Border along the edges.
        gui.fill(left + 1, top,         right - 1, top + 1,    borderColor);
        gui.fill(left + 1, bottom - 1,  right - 1, bottom,     borderColor);
        gui.fill(left,     top + 1,     left + 1,  bottom - 1, borderColor);
        gui.fill(right - 1, top + 1,    right,     bottom - 1, borderColor);

        // Dimmer "notch fill" so the chamfered corners read as part of the shape.
        gui.fill(left + 1,  top + 1,    left + 2,  top + 2,    borderShadowColor);
        gui.fill(right - 2, top + 1,    right - 1, top + 2,    borderShadowColor);
        gui.fill(left + 1,  bottom - 2, left + 2,  bottom - 1, borderShadowColor);
        gui.fill(right - 2, bottom - 2, right - 1, bottom - 1, borderShadowColor);
    }

    /**
     * Draws a vertical 2D leader line at column {@code x} from {@code topY} down to
     * {@code botY}. Renders a wider faded glow underneath a brighter narrow core,
     * both in the ping color.
     */
    private static void draw2DBeamVertical(GuiGraphics gui, int x, int topY, int botY,
                                           int rgb, int alphaByte) {
        if (botY <= topY) return;
        int color = rgb & 0xFFFFFF;
        int coreColor = (alphaByte << 24) | color;
        int glowColor = ((alphaByte / 3) << 24) | color;

        int glowHalf = BEAM_GLOW_WIDTH / 2;
        gui.fill(x - glowHalf, topY, x - glowHalf + BEAM_GLOW_WIDTH, botY, glowColor);
        int coreHalf = BEAM_CORE_WIDTH / 2;
        gui.fill(x - coreHalf, topY, x - coreHalf + BEAM_CORE_WIDTH, botY, coreColor);
    }

    private static Vec3 getAnchorPos(ClientLevel level, ActivePing ping, float partialTick) {
        if (ping.hitType == HitType.ENTITY) {
            Entity e = level.getEntity(ping.entityId);
            if (e != null) {
                // Anchor at the top of the entity's bounding box (plus a small
                // gap) so the leader line ends just above the entity instead of
                // passing through its body/sprite.
                Vec3 pos = e.getPosition(partialTick);
                return new Vec3(pos.x, pos.y + e.getBbHeight() + 0.15, pos.z);
            }
        }
        if (ping.hitType == HitType.BLOCK && ping.blockPos != null) {
            BlockPos bp = ping.blockPos;
            return new Vec3(bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5);
        }
        return ping.position;
    }

    private static Component getTargetName(Level level, ActivePing ping) {
        if (ping.hitType == HitType.BLOCK && ping.blockPos != null) {
            return level.getBlockState(ping.blockPos).getBlock().getName();
        }
        if (ping.hitType == HitType.ENTITY) {
            Entity e = level.getEntity(ping.entityId);
            if (e == null) return Component.literal("Entity");
            if (e instanceof ItemEntity item) return item.getItem().getHoverName();
            return e.getName();
        }
        return Component.literal("Location");
    }
}
