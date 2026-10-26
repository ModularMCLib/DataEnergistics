package com.fish_dan_.data_energistics.client.widget;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;

import net.minecraft.network.chat.Component;

/** Uses AE2's native eight-pixel clear icon for an explicit non-item input action. */
public final class NonItemEjectButton extends IconButton {

    public NonItemEjectButton(Runnable action) {
        this(action, "screen.data_energistics.machine.eject_non_items");
    }

    public NonItemEjectButton(Runnable action, String tooltipKey) {
        super(button -> action.run());
        this.setSize(8, 8);
        this.setHalfSize(true);
        this.setDisableBackground(true);
        this.setMessage(Component.translatable(tooltipKey));
    }

    @Override
    protected Icon getIcon() {
        return Icon.S_CLEAR;
    }
}
