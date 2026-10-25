package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

/** Reveals the authored heat strip from below, retaining its green-to-red gradient and individual bands. */
public final class GunHeatBar extends UIElement {

    private final UIElement reveal = new UIElement();
    private final UIElement fill = new UIElement();

    public GunHeatBar() {
        setAllowHitTest(false);
        addClass("platform-heat");
        reveal.setAllowHitTest(false);
        reveal.setOverflowVisible(false);
        fill.setAllowHitTest(false);
        fill.addClass("platform-heat-fill");
        reveal.addChild(fill);
        addChild(reveal);
        setHeat(0, false);
    }

    public void setHeat(double heat, boolean overheated) {
        int hidden = 18 - (int) Math.round(heat * 18 / 100);
        WeaponPlatformStyle.place(reveal, 0, hidden, 6, 18 - hidden);
        WeaponPlatformStyle.place(fill, 0, -hidden, 6, 18);
        if (overheated) addClass("overheated");
        else removeClass("overheated");
    }
}
