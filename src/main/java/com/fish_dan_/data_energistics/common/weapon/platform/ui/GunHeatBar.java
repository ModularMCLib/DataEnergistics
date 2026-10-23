package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.Data_Energistics;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

/** Reveals the authored heat strip from below, retaining its green-to-red gradient and individual bands. */
public final class GunHeatBar extends UIElement {

    private final UIElement reveal = new UIElement();
    private final UIElement fill = new UIElement();

    public GunHeatBar() {
        setAllowHitTest(false);
        layout(style -> style.width(6).height(18));
        style(style -> style.backgroundTexture(SpriteTexture.of(Data_Energistics.id("textures/guis/states/progress.png"))));
        reveal.setAllowHitTest(false);
        reveal.setOverflowVisible(false);
        fill.setAllowHitTest(false);
        fill.style(style -> style.backgroundTexture(SpriteTexture.of(Data_Energistics.id("textures/guis/weapon_platform/overheated.png"))));
        reveal.addChild(fill);
        addChild(reveal);
        setHeat(0, false);
    }

    public void setHeat(double heat, boolean overheated) {
        int hidden = 18 - (int) Math.round(heat * 18 / 100);
        WeaponPlatformStyle.place(reveal, 0, hidden, 6, 18 - hidden);
        WeaponPlatformStyle.place(fill, 0, -hidden, 6, 18);
        int border = overheated ? 0xffff4d25 : WeaponPlatformStyle.EDGE;
        style(style -> style.overlayTexture(new ColorBorderTexture(-1, border)));
    }
}
