package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import dev.vfyjxf.taffy.style.TaffyPosition;

/** Runtime HUD defaults and dynamic geometry; the platform stylesheet owns the static theme. */
public final class WeaponPlatformStyle {

    public static final int TEXT = 0xff343548;
    public static final int EDGE = 0xfff2f2f2;
    /** Palette from states/top_surface/selection/text_field/selected.png, including its authored alpha. */
    public static final int HUD_INFORMATION_BACKGROUND = 0x6693ffde;
    public static final int HUD_INFORMATION_EDGE = 0xff57e0bb;

    private WeaponPlatformStyle() {}

    public static void place(UIElement element, int x, int y, int width, int height) {
        element.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height));
    }
}
