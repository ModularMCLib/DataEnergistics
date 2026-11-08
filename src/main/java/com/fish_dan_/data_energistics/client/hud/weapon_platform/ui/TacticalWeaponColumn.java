package com.fish_dan_.data_energistics.client.hud.weapon_platform.ui;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering.TacticalHudFrame;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformStyle;
import com.fish_dan_.data_energistics.config.client.hud.tactical.HudWidgetStyle;
import com.fish_dan_.data_energistics.config.client.hud.tactical.WeaponHudSide;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import net.minecraft.network.chat.Component;

/** Rotated frame and editor affordances around one tightly packed list of occupied slots. */
final class TacticalWeaponColumn extends UIElement {

    static final int WIDTH = 176, ROW_HEIGHT = 32, CONTENT_TOP = 32;
    final UIElement list = new UIElement();
    final Label handle = new Label();
    final WeaponHudSide side;
    private final Label empty = new Label();
    private TacticalHudFrame frame = new TacticalHudFrame(96);
    private HudWidgetStyle appearance;
    private int insertion = -1;
    private int contentHeight;

    TacticalWeaponColumn(WeaponHudSide side) {
        this.side = side;
        appearance = HudWidgetStyle.defaults(side.widget);
        list.setAllowHitTest(false);
        handle.addClasses("platform-hud-label", "platform-hud-column-handle");
        handle.setValue(Component.translatable(side.widget.translationKey()));
        WeaponPlatformStyle.place(handle, 28, 6, 124, 12);
        empty.addClasses("platform-hud-label", "platform-hud-column-empty");
        empty.setAllowHitTest(false);
        empty.setValue(Component.translatable("hud.data_energistics.weapon_platform.drop_weapon"));
        WeaponPlatformStyle.place(empty, 24, CONTENT_TOP + 8, 128, 14);
        addChildren(list, handle, empty);
    }

    int configure(int count, HudWidgetStyle style, boolean interactive) {
        int height = CONTENT_TOP * 2 + Math.max(1, count) * ROW_HEIGHT;
        if (height != contentHeight) {
            contentHeight = height;
            frame = new TacticalHudFrame(height);
        }
        appearance = style;
        handle.setDisplay(interactive);
        handle.textStyle(value -> value.textColor(style.textColor()).textShadow(true));
        empty.setDisplay(interactive && count == 0);
        empty.textStyle(value -> value.textColor(style.textColor()).textShadow(true));
        setAllowHitTest(interactive);
        WeaponPlatformStyle.place(list, side == WeaponHudSide.LEFT ? 20 : 4, CONTENT_TOP, WIDTH - 24, Math.max(1, count) * ROW_HEIGHT);
        return height;
    }

    void insertion(int index) {
        insertion = index;
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        float x = getPositionX(), y = getPositionY();
        boolean right = side == WeaponHudSide.RIGHT;
        frame.vertical(context.graphics, x + (right ? WIDTH - 24 : 24), y, right, appearance.lineColor(), appearance.lineWidth());
        if (insertion >= 0) {
            int left = Math.round(x) + 20;
            int top = Math.round(y) + CONTENT_TOP + insertion * ROW_HEIGHT;
            context.graphics.fill(left, top - 1, Math.round(x) + WIDTH - 20, top + 1, appearance.lineColor());
            context.graphics.fill(left - 3, top - 3, left, top + 3, appearance.lineColor());
        }
    }
}
