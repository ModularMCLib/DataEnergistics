package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModule;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModuleStyle;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class TacticalCompass extends UIElement {

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        if (context.mc.player == null) return;
        float yaw = Mth.positiveModulo(Mth.lerp(context.partialTick, context.mc.player.yRotO, context.mc.player.getYRot()) + 180, 360);
        int x = Math.round(getPositionX()), y = Math.round(getPositionY()) + 13, center = Math.round(x + getSizeWidth() / 2);
        HudModuleStyle style = TacticalHudPreferences.get(HudModule.COMPASS);
        var graphics = context.graphics;
        for (int delta = -60; delta <= 60; delta++) {
            int bearing = Mth.positiveModulo(Math.round(yaw) + delta, 360);
            int tickX = center + delta * 2;
            if (style.shows(HudModuleStyle.HEALTH) && bearing % 5 == 0) graphics.fill(tickX, y + 10, tickX + 1, y + (bearing % 15 == 0 ? 17 : 14), 0xff94d3d0);
            if (bearing % 90 == 0 && style.shows(HudModuleStyle.NAME)) {
                String direction = switch (bearing) {
                    case 0 -> "north";
                    case 90 -> "east";
                    case 180 -> "south";
                    default -> "west";
                };
                graphics.drawCenteredString(context.mc.font, Component.translatable("hud.data_energistics.weapon_platform." + direction), tickX, y, 0xff8ff1da);
            } else if (bearing % 30 == 0 && style.shows(HudModuleStyle.DISTANCE)) graphics.drawCenteredString(context.mc.font, Integer.toString(bearing), tickX, y, 0xffb4cdd0);
        }
        graphics.fill(center - 1, y + 16, center + 2, y + 20, 0xfff1d77f);
        if (style.shows(HudModuleStyle.DISTANCE)) graphics.drawCenteredString(context.mc.font, Math.round(yaw) + "°", center, y + 23, 0xffe0f3f0);
    }
}
