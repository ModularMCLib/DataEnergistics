package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import dev.vfyjxf.taffy.style.TaffyPosition;

/** Uses the existing Trinity/backpack palette for both the platform window and tactical HUD. */
public final class WeaponPlatformStyle {

    public static final int TEXT = 0xff343548;
    public static final int MUTED = 0xff696d88;
    public static final int SURFACE = 0xffcbccd4;
    public static final int SECTION = 0xff9a9fb4;
    public static final int EDGE = 0xfff2f2f2;

    private WeaponPlatformStyle() {}

    public static GuiTextureGroup surface(int color) {
        return GuiTextureGroup.of(new ColorRectTexture(color), new ColorBorderTexture(-1, EDGE));
    }

    /** Two one-pixel outlines separated by one surface pixel, entirely inside the element's bounds. */
    public static GuiTextureGroup doubleBorder(int color) {
        return GuiTextureGroup.of(new ColorRectTexture(color), new ColorBorderTexture(-3, EDGE),
                new ColorBorderTexture(-2, color), new ColorBorderTexture(-1, EDGE));
    }

    /** Reproduces the reference's recessed panels without extra layout children or a baked window texture. */
    public static void panel(UIElement parent, int x, int y, int width, int height, int fill) {
        UIElement rim = new UIElement();
        place(rim, x, y, width, height);
        rim.setAllowHitTest(false);
        rim.style(style -> style.backgroundTexture(doubleBorder(fill)));
        parent.addChild(rim);
    }

    public static void place(UIElement element, int x, int y, int width, int height) {
        element.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height));
    }
}
