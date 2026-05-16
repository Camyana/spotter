package com.cam.pingmod.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.function.IntConsumer;

public class ColorSlider extends AbstractSliderButton {
    private final String label;
    private final int textColor;
    private final IntConsumer onChange;

    public ColorSlider(int x, int y, int w, int h, String label, int textColor, int initialValue, IntConsumer onChange) {
        super(x, y, w, h, Component.empty(), Math.max(0, Math.min(255, initialValue)) / 255.0);
        this.label = label;
        this.textColor = textColor;
        this.onChange = onChange;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        int value = (int) Math.round(this.value * 255);
        setMessage(Component.literal(label + ": " + value)
                .withStyle(s -> s.withColor(TextColor.fromRgb(textColor))));
    }

    @Override
    protected void applyValue() {
        onChange.accept((int) Math.round(this.value * 255));
    }
}
