package com.cam.pingmod.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.function.DoubleConsumer;

public class FloatSlider extends AbstractSliderButton {
    private final String label;
    private final double min, max;
    private final int textColor;
    private final DoubleConsumer onChange;
    private final String unit;

    public FloatSlider(int x, int y, int w, int h, String label, double min, double max,
                       double initialValue, String unit, int textColor, DoubleConsumer onChange) {
        super(x, y, w, h, Component.empty(),
                (Math.max(min, Math.min(max, initialValue)) - min) / (max - min));
        this.label = label;
        this.min = min;
        this.max = max;
        this.unit = unit;
        this.textColor = textColor;
        this.onChange = onChange;
        updateMessage();
    }

    private double currentValue() {
        return min + this.value * (max - min);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label + ": " + String.format("%.2f", currentValue()) + unit)
                .withStyle(s -> s.withColor(TextColor.fromRgb(textColor))));
    }

    @Override
    protected void applyValue() {
        onChange.accept(currentValue());
    }
}
