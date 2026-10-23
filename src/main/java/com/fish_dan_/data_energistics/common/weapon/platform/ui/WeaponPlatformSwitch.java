package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

/** Small native switch whose clicks stay within the control instead of opening or closing weapon details. */
final class WeaponPlatformSwitch extends Switch {

    WeaponPlatformSwitch() {
        addClass("platform-switch");
        markIcon.setDisplay(false);
        placeholder.setDisplay(false);
        addEventListener(UIEvents.MOUSE_ENTER, event -> addClass("hovered"));
        addEventListener(UIEvents.MOUSE_LEAVE, event -> removeClass("hovered"));
    }

    @Override
    protected void onSwitchClick(UIEvent event) {
        if (event.button == 0) super.onSwitchClick(event);
        event.stopPropagation();
    }
}
