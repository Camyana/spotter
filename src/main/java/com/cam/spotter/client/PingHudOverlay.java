package com.cam.spotter.client;

import com.cam.spotter.Spotter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = Spotter.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class PingHudOverlay {
    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(Spotter.MODID, "cooldown_indicator");
    private static final ResourceLocation RING_TEX =
            ResourceLocation.fromNamespaceAndPath(Spotter.MODID, "textures/gui/circle.png");
    private static final ResourceLocation PROGRESS_TEX =
            ResourceLocation.fromNamespaceAndPath(Spotter.MODID, "textures/gui/progress.png");

    private static final int TEX_SIZE = 16;
    private static final int FRAMES = 32;
    private static final int SHEET_W = TEX_SIZE * FRAMES;
    private static final int DISPLAY_SIZE = 14;
    private static final int OFFSET_X = 14;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER_ID, PingHudOverlay::render);
    }

    public static void render(GuiGraphics gui, DeltaTracker delta) {
        float progress = ClientEvents.getCooldownProgress();
        if (progress >= 1.0f) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        int cx = gui.guiWidth() / 2 + OFFSET_X;
        int cy = gui.guiHeight() / 2;
        int x = cx - DISPLAY_SIZE / 2;
        int y = cy - DISPLAY_SIZE / 2;

        int rgb = PingClientConfig.COLOR.get() & 0xFFFFFF;
        float rf = ((rgb >> 16) & 0xFF) / 255.0f;
        float gf = ((rgb >> 8) & 0xFF) / 255.0f;
        float bf = (rgb & 0xFF) / 255.0f;

        gui.setColor(0.7f, 0.7f, 0.7f, 0.25f);
        gui.blit(RING_TEX, x, y, DISPLAY_SIZE, DISPLAY_SIZE,
                0.0f, 0.0f, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);

        int frame = Math.min(FRAMES - 1, (int) (progress * FRAMES));
        if (frame > 0) {
            gui.setColor(rf, gf, bf, 0.5f);
            gui.blit(PROGRESS_TEX, x, y, DISPLAY_SIZE, DISPLAY_SIZE,
                    (float) (frame * TEX_SIZE), 0.0f,
                    TEX_SIZE, TEX_SIZE,
                    SHEET_W, TEX_SIZE);
        }

        gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
