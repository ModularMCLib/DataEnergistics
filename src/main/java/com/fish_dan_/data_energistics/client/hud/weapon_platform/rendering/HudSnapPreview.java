package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.layout.HudComponentSnap.Bounds;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

/** Non-interactive corner markers show the exact adjacent placement before a component is released. */
public final class HudSnapPreview extends UIElement {

    private Bounds bounds = new Bounds(0, 0, 1, 1);
    private int color;

    public HudSnapPreview() {
        setAllowHitTest(false);
        setDisplay(false);
        style(style -> style.zIndex(20));
    }

    public void show(Bounds bounds, int color) {
        this.bounds = bounds;
        this.color = color;
        setDisplay(true);
    }

    public void clear() {
        setDisplay(false);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        int left = Math.round(bounds.x()), top = Math.round(bounds.y());
        int right = Math.round(bounds.x() + bounds.width()), bottom = Math.round(bounds.y() + bounds.height());
        var graphics = context.graphics;
        graphics.fill(left, top, left + 5, top + 1, color);
        graphics.fill(left, top, left + 1, top + 5, color);
        graphics.fill(right - 5, top, right, top + 1, color);
        graphics.fill(right - 1, top, right, top + 5, color);
        graphics.fill(left, bottom - 1, left + 5, bottom, color);
        graphics.fill(left, bottom - 5, left + 1, bottom, color);
        graphics.fill(right - 5, bottom - 1, right, bottom, color);
        graphics.fill(right - 1, bottom - 5, right, bottom, color);
    }
}
