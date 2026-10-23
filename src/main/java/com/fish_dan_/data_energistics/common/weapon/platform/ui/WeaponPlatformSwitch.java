package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.resources.ResourceLocation;

/** Small native switch whose clicks stay within the control instead of opening or closing weapon details. */
final class WeaponPlatformSwitch extends Switch {

    private static final ResourceLocation CHECKBOX = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/checkbox.png");
    private static final SpriteTexture OFF = SpriteTexture.of(CHECKBOX).setSprite(0, 28, 22, 12);
    private static final SpriteTexture OFF_HOVER = SpriteTexture.of(CHECKBOX).setSprite(22, 28, 22, 12);
    private static final SpriteTexture ON = SpriteTexture.of(CHECKBOX).setSprite(0, 40, 22, 12);
    private static final SpriteTexture ON_HOVER = SpriteTexture.of(CHECKBOX).setSprite(22, 40, 22, 12);

    WeaponPlatformSwitch() {
        layout(style -> style.width(13).height(7).paddingAll(0));
        markIcon.setDisplay(false);
        placeholder.setDisplay(false);
        switchStyle(style -> style.baseTexture(OFF).pressedTexture(ON));
        addEventListener(UIEvents.MOUSE_ENTER, event -> switchStyle(style -> style.baseTexture(OFF_HOVER).pressedTexture(ON_HOVER)));
        addEventListener(UIEvents.MOUSE_LEAVE, event -> switchStyle(style -> style.baseTexture(OFF).pressedTexture(ON)));
    }

    @Override
    protected void onSwitchClick(UIEvent event) {
        if (event.button == 0) super.onSwitchClick(event);
        event.stopPropagation();
    }
}
