package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

/** Small native switch whose clicks stay within the control instead of opening or closing weapon details. */
final class WeaponPlatformSwitch extends Switch {

    WeaponPlatformSwitch() {
        layout(style -> style.width(13).height(7).paddingAll(1));
        switchStyle(style -> style.baseTexture(WeaponPlatformStyle.surface(WeaponPlatformStyle.MUTED))
                .pressedTexture(WeaponPlatformStyle.surface(WeaponPlatformStyle.TEXT))
                .unmarkTexture(new ColorRectTexture(WeaponPlatformStyle.SECTION))
                .markTexture(new ColorRectTexture(WeaponPlatformStyle.EDGE)));
        markIcon.setAllowHitTest(false);
        placeholder.setAllowHitTest(false);
    }

    @Override
    protected void onSwitchClick(UIEvent event) {
        if (event.button == 0) super.onSwitchClick(event);
        event.stopPropagation();
    }
}
