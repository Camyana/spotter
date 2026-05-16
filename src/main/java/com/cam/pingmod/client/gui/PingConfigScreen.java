package com.cam.pingmod.client.gui;

import com.cam.pingmod.client.PingClientConfig;
import com.cam.pingmod.client.PingDirectionOverlay;
import com.cam.pingmod.client.PingKeyMappings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class PingConfigScreen extends Screen {
    private static final int DEFAULT_COLOR = 0x00FFFF;

    private static final int RED_TINT = 0xFF6B6B;
    private static final int GREEN_TINT = 0x6BE36B;
    private static final int BLUE_TINT = 0x6BB7FF;
    private static final int NEUTRAL_TINT = 0xFFE6ECF4;

    private static final int PANEL_W = 280;
    private static final int CONTENT_W = 240;
    private static final int PANEL_PAD = 16;

    private static final int PANEL_SHADOW = 0xC0000000;
    private static final int PANEL_BG = 0xFF242E3E;
    private static final int PANEL_BG_TOP = 0xFF324056;
    private static final int PANEL_BORDER = 0xFF4A5C72;
    private static final int PANEL_BORDER_HIGHLIGHT = 0xFF8AAEC8;
    private static final int TITLE_ACCENT = 0xFF6BB7FF;
    private static final int SECTION_LABEL_COLOR = 0xFFFFFFFF;
    private static final int DIVIDER_COLOR = 0xA0708BA8;
    private static final int HINT_COLOR = 0xFFB0BCCC;

    private static final int TITLE_AREA_H = 26;
    private static final int PREVIEW_AREA_H = 90;
    private static final int SWATCH_H = 28;
    private static final int SLIDER_ROW = 22;
    private static final int CHECKBOX_ROW = 20;
    private static final int BUTTON_ROW = 22;
    private static final int CYCLE_ROW = 22;
    private static final int SECTION_LABEL_H = 14;
    private static final int SECTION_GAP = 10;

    @Nullable
    private final Screen parent;

    private int red, green, blue;
    @Nullable
    private KeyMapping rebindingKey;

    private int panelLeft, panelTop, panelBottom;
    private int previewSectionY;
    private int swatchTop;
    private int colorSectionY;
    private int displaySectionY;
    private int labelSectionY;
    private int controlsSectionY;

    private int contentTopY;
    private int contentBottomY;
    private int maxScroll;
    private double scrollOffset;
    /** Widgets that live inside the scrollable content area. */
    private final List<AbstractWidget> scrollableWidgets = new ArrayList<>();

    public PingConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.pingmod.config.title"));
        this.parent = parent;
        int color = PingClientConfig.COLOR.get();
        this.red = (color >> 16) & 0xFF;
        this.green = (color >> 8) & 0xFF;
        this.blue = color & 0xFF;
    }

    /** Add a widget that lives inside the scrollable content viewport. */
    private <T extends AbstractWidget> T addScrollableWidget(T widget) {
        scrollableWidgets.add(widget);
        addRenderableWidget(widget);
        return widget;
    }

    @Override
    protected void init() {
        scrollableWidgets.clear();
        int cx = this.width / 2;
        int contentLeft = cx - CONTENT_W / 2;

        // Section heights.
        int previewH = SECTION_LABEL_H + PREVIEW_AREA_H;
        int colorH = SECTION_LABEL_H + SWATCH_H + SECTION_GAP + SLIDER_ROW * 3;
        int displayH = SECTION_LABEL_H + CHECKBOX_ROW + CYCLE_ROW + SLIDER_ROW + CHECKBOX_ROW;
        int labelLinesH = SECTION_LABEL_H + CHECKBOX_ROW * 4;
        int controlsH = SECTION_LABEL_H + BUTTON_ROW * 2;
        int contentHeight = previewH + SECTION_GAP + colorH + SECTION_GAP + displayH + SECTION_GAP
                          + labelLinesH + SECTION_GAP + controlsH;
        int desiredPanelH = PANEL_PAD * 2 + TITLE_AREA_H + contentHeight;
        int bottomBarHeight = 32;

        // Cap panel height so it always fits on screen with room for the bottom bar.
        int maxPanelH = this.height - bottomBarHeight - 24;
        int panelH = Math.min(desiredPanelH, maxPanelH);

        this.panelTop = Math.max(12, (this.height - panelH - bottomBarHeight) / 2);
        this.panelBottom = panelTop + panelH;
        this.panelLeft = cx - PANEL_W / 2;

        // Scrollable viewport inside the panel.
        this.contentTopY = panelTop + TITLE_AREA_H + PANEL_PAD;
        this.contentBottomY = panelBottom - PANEL_PAD;
        int availableContentH = contentBottomY - contentTopY;
        this.maxScroll = Math.max(0, contentHeight - availableContentH);
        this.scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        int y = contentTopY - (int) scrollOffset;

        // === PREVIEW SECTION ===
        this.previewSectionY = y;
        y += SECTION_LABEL_H + PREVIEW_AREA_H + SECTION_GAP;

        // === COLOR SECTION ===
        this.colorSectionY = y;
        y += SECTION_LABEL_H;
        this.swatchTop = y;
        y += SWATCH_H + SECTION_GAP - SECTION_GAP; // hold spot; sliders follow
        // Adjust: swatch sits in its own block; sliders below it.
        y = colorSectionY + SECTION_LABEL_H + SWATCH_H + SECTION_GAP;
        addScrollableWidget(new ColorSlider(contentLeft, y, CONTENT_W, 18, "R", RED_TINT, red, v -> {
            red = v;
            saveColor();
        }));
        y += SLIDER_ROW;
        addScrollableWidget(new ColorSlider(contentLeft, y, CONTENT_W, 18, "G", GREEN_TINT, green, v -> {
            green = v;
            saveColor();
        }));
        y += SLIDER_ROW;
        addScrollableWidget(new ColorSlider(contentLeft, y, CONTENT_W, 18, "B", BLUE_TINT, blue, v -> {
            blue = v;
            saveColor();
        }));
        y += SLIDER_ROW + SECTION_GAP;

        // === DISPLAY SECTION ===
        this.displaySectionY = y;
        y += SECTION_LABEL_H;
        addScrollableWidget(Checkbox.builder(Component.translatable("screen.pingmod.config.show_beam"), font)
                .pos(contentLeft, y)
                .selected(PingClientConfig.SHOW_BEAM.get())
                .tooltip(Tooltip.create(Component.translatable("screen.pingmod.config.show_beam.tooltip")))
                .onValueChange((cb, v) -> {
                    PingClientConfig.SHOW_BEAM.set(v);
                    PingClientConfig.SPEC.save();
                })
                .build());
        y += CHECKBOX_ROW;

        addScrollableWidget(CycleButton
                .<PingClientConfig.HologramPosition>builder(p -> Component.translatable(
                        "screen.pingmod.config.hologram_position." + p.name().toLowerCase()))
                .withValues(PingClientConfig.HologramPosition.values())
                .withInitialValue(PingClientConfig.HOLOGRAM_POSITION.get())
                .withTooltip(p -> Tooltip.create(Component.translatable("screen.pingmod.config.hologram_position.tooltip")))
                .create(contentLeft, y, CONTENT_W, 20,
                        Component.translatable("screen.pingmod.config.hologram_position"),
                        (b, v) -> {
                            PingClientConfig.HOLOGRAM_POSITION.set(v);
                            PingClientConfig.SPEC.save();
                        }));
        y += CYCLE_ROW;

        addScrollableWidget(new FloatSlider(contentLeft, y, CONTENT_W, 18,
                "Text Scale", 0.5, 2.5,
                PingClientConfig.TEXT_SCALE.get(), "x", NEUTRAL_TINT,
                v -> {
                    PingClientConfig.TEXT_SCALE.set(v);
                    PingClientConfig.SPEC.save();
                }));
        y += SLIDER_ROW;

        addScrollableWidget(Checkbox.builder(Component.translatable("screen.pingmod.config.show_hostile_indicator"), font)
                .pos(contentLeft, y)
                .selected(PingClientConfig.SHOW_HOSTILE_INDICATOR.get())
                .tooltip(Tooltip.create(Component.translatable("screen.pingmod.config.show_hostile_indicator.tooltip")))
                .onValueChange((cb, v) -> {
                    PingClientConfig.SHOW_HOSTILE_INDICATOR.set(v);
                    PingClientConfig.SPEC.save();
                })
                .build());
        y += CHECKBOX_ROW + SECTION_GAP;

        // === LABEL LINES SECTION ===
        this.labelSectionY = y;
        y += SECTION_LABEL_H;
        addScrollableWidget(Checkbox.builder(Component.translatable("screen.pingmod.config.show_target_name"), font)
                .pos(contentLeft, y)
                .selected(PingClientConfig.SHOW_TARGET_NAME.get())
                .tooltip(Tooltip.create(Component.translatable("screen.pingmod.config.show_target_name.tooltip")))
                .onValueChange((cb, v) -> {
                    PingClientConfig.SHOW_TARGET_NAME.set(v);
                    PingClientConfig.SPEC.save();
                })
                .build());
        y += CHECKBOX_ROW;
        addScrollableWidget(Checkbox.builder(Component.translatable("screen.pingmod.config.show_owner_name"), font)
                .pos(contentLeft, y)
                .selected(PingClientConfig.SHOW_OWNER_NAME.get())
                .tooltip(Tooltip.create(Component.translatable("screen.pingmod.config.show_owner_name.tooltip")))
                .onValueChange((cb, v) -> {
                    PingClientConfig.SHOW_OWNER_NAME.set(v);
                    PingClientConfig.SPEC.save();
                })
                .build());
        y += CHECKBOX_ROW;
        addScrollableWidget(Checkbox.builder(Component.translatable("screen.pingmod.config.show_player_head"), font)
                .pos(contentLeft, y)
                .selected(PingClientConfig.SHOW_PLAYER_HEAD.get())
                .tooltip(Tooltip.create(Component.translatable("screen.pingmod.config.show_player_head.tooltip")))
                .onValueChange((cb, v) -> {
                    PingClientConfig.SHOW_PLAYER_HEAD.set(v);
                    PingClientConfig.SPEC.save();
                })
                .build());
        y += CHECKBOX_ROW;
        addScrollableWidget(Checkbox.builder(Component.translatable("screen.pingmod.config.show_distance"), font)
                .pos(contentLeft, y)
                .selected(PingClientConfig.SHOW_DISTANCE.get())
                .tooltip(Tooltip.create(Component.translatable("screen.pingmod.config.show_distance.tooltip")))
                .onValueChange((cb, v) -> {
                    PingClientConfig.SHOW_DISTANCE.set(v);
                    PingClientConfig.SPEC.save();
                })
                .build());
        y += CHECKBOX_ROW + SECTION_GAP;

        // === CONTROLS SECTION ===
        this.controlsSectionY = y;
        y += SECTION_LABEL_H;
        addScrollableWidget(Button.builder(
                keybindLabel(PingKeyMappings.PLACE_PING),
                b -> startRebind(PingKeyMappings.PLACE_PING)
        ).bounds(contentLeft, y, CONTENT_W, 20).build());
        y += BUTTON_ROW;
        addScrollableWidget(Button.builder(
                keybindLabel(PingKeyMappings.OPEN_SETTINGS),
                b -> startRebind(PingKeyMappings.OPEN_SETTINGS)
        ).bounds(contentLeft, y, CONTENT_W, 20).build());

        applyScrollVisibility();

        int bottomY = panelBottom + 8;
        int btnW = 100;
        int gap = 8;
        addRenderableWidget(Button.builder(
                Component.translatable("screen.pingmod.config.reset"),
                b -> resetColor()
        ).bounds(cx - btnW - gap / 2, bottomY, btnW, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                b -> onClose()
        ).bounds(cx + gap / 2, bottomY, btnW, 20).build());
    }

    private Component keybindLabel(KeyMapping km) {
        if (km == rebindingKey) {
            return Component.translatable("screen.pingmod.config.rebinding")
                    .copy()
                    .withStyle(ChatFormatting.YELLOW);
        }
        return Component.translatable(km.getName()).copy()
                .append(": ")
                .append(km.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.AQUA));
    }

    private void startRebind(KeyMapping km) {
        rebindingKey = km;
        rebuildWidgets();
    }

    private void saveColor() {
        int color = ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
        PingClientConfig.COLOR.set(color);
        PingClientConfig.SPEC.save();
    }

    private void resetColor() {
        red = (DEFAULT_COLOR >> 16) & 0xFF;
        green = (DEFAULT_COLOR >> 8) & 0xFF;
        blue = DEFAULT_COLOR & 0xFF;
        saveColor();
        rebuildWidgets();
    }

    /** Hide scrollable widgets that are fully off the top/bottom of the viewport. */
    private void applyScrollVisibility() {
        for (AbstractWidget w : scrollableWidgets) {
            int top = w.getY();
            int bot = top + w.getHeight();
            w.visible = bot > contentTopY && top < contentBottomY;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollDeltaY) {
        if (maxScroll > 0
                && mouseX >= panelLeft && mouseX <= panelLeft + PANEL_W
                && mouseY >= contentTopY && mouseY <= contentBottomY) {
            double next = scrollOffset - scrollDeltaY * 12;
            scrollOffset = Math.max(0, Math.min(maxScroll, next));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollDeltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (rebindingKey != null) {
            InputConstants.Key key = (keyCode == GLFW.GLFW_KEY_ESCAPE)
                    ? InputConstants.UNKNOWN
                    : InputConstants.getKey(keyCode, scanCode);
            rebindingKey.setKey(key);
            KeyMapping.resetMapping();
            if (this.minecraft != null) {
                this.minecraft.options.save();
            }
            rebindingKey = null;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, mods);
    }

    @Override
    public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(gui, mouseX, mouseY, partialTick);

        drawPanel(gui, panelLeft, panelTop, panelLeft + PANEL_W, panelBottom);

        gui.fill(panelLeft + 1, panelTop + 1, panelLeft + PANEL_W - 1, panelTop + 2, TITLE_ACCENT);

        gui.drawCenteredString(font, this.title.copy().withStyle(ChatFormatting.BOLD),
                this.width / 2, panelTop + 8, 0xFFFFFFFF);
        gui.fill(panelLeft + 12, panelTop + TITLE_AREA_H - 4,
                panelLeft + PANEL_W - 12, panelTop + TITLE_AREA_H - 3, TITLE_ACCENT);

        // Scrollable decorative content lives inside the viewport scissor.
        gui.enableScissor(panelLeft + 1, contentTopY, panelLeft + PANEL_W - 1, contentBottomY);

        // Color swatch.
        int cx = this.width / 2;
        int swatchW = 130;
        int swatchHeight = SWATCH_H - 4;
        int sx = cx - swatchW / 2;
        int sy = swatchTop;
        int color = 0xFF000000 | (red << 16) | (green << 8) | blue;
        gui.fill(sx - 2, sy - 2, sx + swatchW + 2, sy + swatchHeight + 2, 0xFF000000);
        gui.fill(sx - 1, sy - 1, sx + swatchW + 1, sy + swatchHeight + 1, PANEL_BORDER_HIGHLIGHT);
        gui.fill(sx, sy, sx + swatchW, sy + swatchHeight, color);
        String hex = String.format("#%02X%02X%02X", red, green, blue);
        int hexColor = luminance(red, green, blue) > 0.55f ? 0xFF101010 : 0xFFFFFFFF;
        gui.drawCenteredString(font, Component.literal(hex), cx,
                sy + (swatchHeight - font.lineHeight) / 2 + 1, hexColor);

        drawSectionLabel(gui, font, panelLeft + 20, previewSectionY,
                Component.translatable("screen.pingmod.config.section.preview"));
        drawSectionLabel(gui, font, panelLeft + 20, colorSectionY,
                Component.translatable("screen.pingmod.config.section.color"));
        drawSectionLabel(gui, font, panelLeft + 20, displaySectionY,
                Component.translatable("screen.pingmod.config.section.display"));
        drawSectionLabel(gui, font, panelLeft + 20, labelSectionY,
                Component.translatable("screen.pingmod.config.section.label"));
        drawSectionLabel(gui, font, panelLeft + 20, controlsSectionY,
                Component.translatable("screen.pingmod.config.section.controls"));

        // Live preview — renders a sample ping widget using current config values.
        int previewCx = panelLeft + PANEL_W / 2;
        int previewCy = previewSectionY + SECTION_LABEL_H + PREVIEW_AREA_H / 2 - 6;
        PingDirectionOverlay.renderPreview(gui, font, Minecraft.getInstance(),
                previewCx, previewCy, partialTick);

        gui.disableScissor();

        gui.drawCenteredString(
                font,
                Component.translatable("screen.pingmod.config.server_hint"),
                this.width / 2,
                Math.min(this.height - 12, panelBottom + 38),
                HINT_COLOR
        );
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);

        // Scrollable widgets inside the viewport — scissor first so they get
        // clipped at the panel boundary as they scroll.
        gui.enableScissor(panelLeft + 1, contentTopY, panelLeft + PANEL_W - 1, contentBottomY);
        for (AbstractWidget w : scrollableWidgets) {
            if (w.visible) w.render(gui, mouseX, mouseY, partialTick);
        }
        gui.disableScissor();

        // Fixed widgets (bottom Reset/Done buttons + anything else not flagged scrollable).
        for (Renderable r : this.renderables) {
            if (r instanceof AbstractWidget aw && scrollableWidgets.contains(aw)) continue;
            r.render(gui, mouseX, mouseY, partialTick);
        }

        // Scrollbar on the right edge of the viewport.
        if (maxScroll > 0) {
            drawScrollbar(gui);
        }
    }

    private void drawScrollbar(GuiGraphics gui) {
        int trackX = panelLeft + PANEL_W - 6;
        int trackTop = contentTopY;
        int trackBot = contentBottomY;
        int trackH = trackBot - trackTop;
        int totalContent = (contentBottomY - contentTopY) + maxScroll;
        int thumbH = Math.max(20, (int) (trackH * (double) (contentBottomY - contentTopY) / totalContent));
        int thumbY = trackTop + (int) ((trackH - thumbH) * (scrollOffset / (double) maxScroll));
        // Track.
        gui.fill(trackX, trackTop, trackX + 3, trackBot, 0x80000000);
        // Thumb.
        gui.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF8AAEC8);
    }

    private static void drawPanel(GuiGraphics gui, int left, int top, int right, int bottom) {
        int shadowOffset = 4;
        gui.fill(left + shadowOffset, top + shadowOffset,
                right + shadowOffset, bottom + shadowOffset, PANEL_SHADOW);

        int titleBarBottom = top + TITLE_AREA_H - 4;
        gui.fill(left, top, right, titleBarBottom, PANEL_BG_TOP);
        gui.fill(left, titleBarBottom, right, bottom, PANEL_BG);

        gui.fill(left, top, right, top + 1, PANEL_BORDER_HIGHLIGHT);
        gui.fill(left, bottom - 1, right, bottom, PANEL_BORDER);
        gui.fill(left, top, left + 1, bottom, PANEL_BORDER);
        gui.fill(right - 1, top, right, bottom, PANEL_BORDER);
        gui.fill(left + 1, top + 1, right - 1, top + 2, 0x40FFFFFF);
    }

    private static void drawSectionLabel(GuiGraphics gui, Font font, int x, int y, Component label) {
        Component styled = label.copy().withStyle(ChatFormatting.BOLD);
        gui.drawString(font, styled, x, y, SECTION_LABEL_COLOR, true);
        int textW = font.width(styled);
        int lineY = y + font.lineHeight / 2;
        gui.fill(x + textW + 8, lineY, x + textW + 8 + 140, lineY + 1, DIVIDER_COLOR);
    }

    private static float luminance(int r, int g, int b) {
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
