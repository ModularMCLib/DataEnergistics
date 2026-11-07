package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.floats.FloatConsumer;

import java.util.Locale;

/** A server-backed percentage control keeps an in-progress edit until its matching snapshot arrives. */
public final class PlatformPercentageSlider extends UIElement {

    private final Label label = new Label();
    private final Scroller.Horizontal slider = new Scroller.Horizontal();
    private final String translation;
    private final float displayScale;
    private float selected;
    private float pending = -1;

    public PlatformPercentageSlider(String translation, float minimum, float maximum, float displayScale, FloatConsumer changed) {
        this.translation = translation;
        this.displayScale = displayScale;
        addClass("platform-percentage-control");
        label.addClasses("platform-label", "platform-percentage-label");
        label.setAllowHitTest(false);
        slider.addClass("platform-percentage-slider");
        slider.headButton.setDisplay(false);
        slider.tailButton.setDisplay(false);
        slider.scrollContainer.getStyle().setInline(PropertyRegistry.BACKGROUND, null);
        slider.scrollBar.getButtonStyle().setInline(PropertyRegistry.BASE_BACKGROUND, null);
        slider.scrollBar.getButtonStyle().setInline(PropertyRegistry.HOVER_BACKGROUND, null);
        slider.scrollBar.getButtonStyle().setInline(PropertyRegistry.PRESSED_BACKGROUND, null);
        slider.setOverflowVisible(true);
        slider.scrollContainer.setOverflowVisible(true);
        slider.scrollBar.layout(layout -> layout.top(-2).minWidth(15).maxWidth(15).height(10).minHeight(10).maxHeight(10).paddingAll(0));
        UIElement rail = new UIElement();
        rail.addClass("platform-percentage-rail");
        for (String layer : new String[] { "rim", "center", "shadow" }) {
            UIElement part = new UIElement();
            part.addClass("platform-percentage-rail-" + layer);
            part.setAllowHitTest(false);
            rail.addChild(part);
        }
        rail.setAllowHitTest(false);
        slider.scrollContainer.addChildAt(rail, 0);
        slider.scrollBar.removeEventListener(UIEvents.MOUSE_DOWN, slider.scrollBar.getBubbleListeners(UIEvents.MOUSE_DOWN).getLast());
        slider.scrollContainer.addEventListener(UIEvents.LAYOUT_CHANGED, event -> {
            float width = slider.scrollContainer.getContentWidth();
            if (width > 0) Style.importantPipeline(slider.getScrollerStyle(), style -> style.scrollBarSize(1500f / width));
        });
        slider.scrollContainer.removeEventListener(UIEvents.MOUSE_WHEEL, slider.scrollContainer.getBubbleListeners(UIEvents.MOUSE_WHEEL).getLast());
        slider.scrollContainer.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (slider.isActiveInHierarchy()) slider.scrollByWheel(event.deltaX != 0 ? event.deltaX : event.deltaY);
            event.stopPropagation();
        });
        slider.setRange(minimum, maximum);
        slider.scrollerStyle(style -> style.scrollDelta(0.01f));
        slider.setOnValueChanged(value -> {
            if (value == selected) return;
            selected = value;
            pending = value;
            updateLabel();
            changed.accept(value);
        });
        style(style -> style.tooltips(Component.translatable(translation + ".description")));
        addChildren(label, slider);
    }

    public void bind(float value, boolean editable) {
        setActive(editable);
        if (!editable) pending = -1;
        if (slider.isDragging() || pending >= 0 && pending != value) return;
        pending = -1;
        selected = value;
        slider.setValue(value, false);
        updateLabel();
    }

    private void updateLabel() {
        label.setValue(Component.translatable(translation, String.format(Locale.ROOT, "%.1f", selected * displayScale)));
    }
}
