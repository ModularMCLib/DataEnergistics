package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;

/** Native wheel scrolling without scrollbar elements in the platform's component tree. */
public class WeaponPlatformScrollView extends ScrollerView {

    public WeaponPlatformScrollView() {
        addClass("platform-scroll");
        scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL).verticalScrollDisplay(ScrollDisplay.NEVER)
                .horizontalScrollDisplay(ScrollDisplay.NEVER).adaptiveHeight(false).adaptiveWidth(false));
        // LDLib2 retains scroll values and wheel callbacks independently of the mounted controls.
        verticalContainer.removeChild(verticalScroller);
        removeChild(horizontalScroller);
        viewPort.layout(style -> style.paddingAll(0));
        viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
    }
}
