package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

/** A capability heading owns its white divider and the bracket around its dependent controls. */
public final class PlatformOptionGroup extends UIElement {

    private final UIElement options = new UIElement();

    public PlatformOptionGroup(UIElement heading) {
        addClass("platform-option-group");
        heading.addClass("platform-option-heading");
        UIElement divider = new UIElement();
        divider.addClass("platform-option-divider");
        divider.setAllowHitTest(false);
        heading.addChild(divider);
        options.addClass("platform-option-body");
        for (String side : new String[] { "vertical", "top", "bottom" }) {
            UIElement edge = new UIElement();
            edge.addClass("platform-group-" + side);
            edge.setAllowHitTest(false);
            options.addChild(edge);
        }
        options.setDisplay(false);
        addChildren(heading, options);
    }

    public PlatformOptionGroup addOptions(UIElement... controls) {
        options.setDisplay(true);
        options.addChildren(controls);
        return this;
    }

    public void setOptionsVisible(boolean visible) {
        options.setDisplay(visible);
    }
}
