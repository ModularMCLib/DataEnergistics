package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Slider;

import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/** A server-backed percentage control keeps an in-progress edit until its matching snapshot arrives. */
public final class PlatformPercentageSlider extends UIElement {

    private final Label label = new Label();
    private final Slider.Horizontal slider = new Slider.Horizontal();
    private final String translation;
    private int selected;
    private int pending = -1;

    public PlatformPercentageSlider(String translation, int minimum, int maximum, IntConsumer changed) {
        this.translation = translation;
        addClass("platform-percentage-control");
        label.addClasses("platform-label", "platform-percentage-label");
        slider.addClass("platform-percentage-slider");
        slider.setRange(minimum, maximum);
        slider.sliderStyle(style -> style.sliderStep(1f / (maximum - minimum)));
        slider.setOnValueChanged(value -> {
            int percentage = Math.round(value);
            slider.setValue((float) percentage, false);
            if (percentage == selected) return;
            selected = percentage;
            pending = percentage;
            updateLabel();
            changed.accept(percentage);
        });
        style(style -> style.tooltips(Component.translatable(translation + ".description")));
        addChildren(label, slider);
    }

    public void bind(int percentage, boolean editable) {
        setActive(editable);
        if (!editable) pending = -1;
        if (slider.isDragging() || pending >= 0 && pending != percentage) return;
        pending = -1;
        selected = percentage;
        slider.setValue((float) percentage, false);
        updateLabel();
    }

    private void updateLabel() {
        label.setValue(Component.translatable(translation, selected));
    }
}
