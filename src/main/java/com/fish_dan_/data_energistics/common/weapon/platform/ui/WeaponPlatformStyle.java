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
    public static final int SURFACE = 0xffe3e3ea;
    public static final int SECTION = 0xffa7adbf;
    public static final int EDGE = 0xfff2f2f2;

    private WeaponPlatformStyle() {}

    public static GuiTextureGroup surface(int color) {
        return GuiTextureGroup.of(new ColorRectTexture(color), new ColorBorderTexture(-1, EDGE));
    }

    /** Nested borders reproduce the reference's recessed panels without a baked window texture. */
    public static void panel(UIElement parent, int x, int y, int width, int height, int fill) {
        UIElement rim = new UIElement();
        place(rim, x, y, width, height);
        rim.style(style -> style.backgroundTexture(surface(SURFACE)));
        UIElement interior = new UIElement();
        place(interior, 2, 2, width - 4, height - 4);
        interior.style(style -> style.backgroundTexture(surface(fill)));
        rim.addChild(interior);
        parent.addChild(rim);
    }

    public static void place(UIElement element, int x, int y, int width, int height) {
        element.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height));
    }
}
