package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;

/** A labeled platform switch shared by the equipment menu and tactical HUD. */
public final class WeaponPlatformToggleRow extends Button {

    public final Switch toggle = new WeaponPlatformSwitch();
    public final Label label = new Label();

    public WeaponPlatformToggleRow(Component text, BooleanConsumer onChanged) {
        noText();
        addClass("platform-toggle-row");
        toggle.addClass("platform-toggle-control");
        toggle.setOnSwitchChanged(onChanged);
        label.setValue(text);
        label.addClass("platform-label");
        label.addClass("platform-toggle-name");
        label.setAllowHitTest(false);
        setOnClick(event -> {
            if (event.button == 0) toggle.setOn(!toggle.isOn(), true);
        });
        addChildren(toggle, label);
    }

    @Override
    public WeaponPlatformToggleRow setActive(boolean active) {
        super.setActive(active);
        toggle.setActive(active);
        return this;
    }
}
