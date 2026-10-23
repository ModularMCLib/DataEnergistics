package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModule;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModuleStyle;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformStyle;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Screen-space frames intentionally ignore occlusion; server targeting still checks line of sight. */
public final class TacticalTargetFrames extends UIElement {

    public TacticalTargetFrames() {
        style(value -> value.overflowVisible(true));
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        var client = context.mc;
        if (client.level == null || client.player == null) return;
        HudModuleStyle style = TacticalHudPreferences.get(HudModule.TARGETS);
        int width = client.getWindow().getGuiScaledWidth(), height = client.getWindow().getGuiScaledHeight();
        float annotationScale = style.scale() * Math.min(width / 854f, height / 480f);
        for (var target : PlatformClientState.snapshot.targets()) {
            var entity = client.level.getEntity(target.entity());
            if (entity == null || !entity.isAlive()) continue;
            Vec3 delta = entity.getPosition(context.partialTick).subtract(entity.position());
            var bounds = TargetFrameProjection.project(entity.getBoundingBox().move(delta).inflate(0.04), width, height);
            if (bounds == null) continue;
            int dx = Math.round(getPositionX()), dy = Math.round(getPositionY());
            int left = bounds.left() + dx, top = bounds.top() + dy, right = bounds.right() + dx, bottom = bounds.bottom() + dy;
            int color = 0xff000000 | target.color();
            boolean locked = PlatformClientState.snapshot.lock() == target.entity();
            int line = locked ? 2 : 1;
            var graphics = context.graphics;
            graphics.fill(left, top, Math.min(right, left + 8), top + line, color);
            graphics.fill(left, top, left + line, Math.min(bottom, top + 8), color);
            graphics.fill(Math.max(left, right - 8), top, right, top + line, color);
            graphics.fill(right - line, top, right, Math.min(bottom, top + 8), color);
            graphics.fill(left, bottom - line, Math.min(right, left + 8), bottom, color);
            graphics.fill(left, Math.max(top, bottom - 8), left + line, bottom, color);
            graphics.fill(Math.max(left, right - 8), bottom - line, right, bottom, color);
            graphics.fill(right - line, Math.max(top, bottom - 8), right, bottom, color);
            Component caption = style.shows(HudModuleStyle.NAME) ? entity.getDisplayName().copy() : Component.empty();
            if (style.shows(HudModuleStyle.DISTANCE)) caption = caption.copy().append(" " + Mth.floor(client.player.distanceTo(entity)) + "m");
            if (style.shows(HudModuleStyle.BINARY) && target.binary() > 0) caption = caption.copy().append(" [" + target.binary() + "/32]");
            if (locked) caption = caption.copy().append(" ◈");
            graphics.pose().pushPose();
            graphics.pose().translate(left, Math.max(1, top - 12 * annotationScale), 0);
            graphics.pose().scale(annotationScale, annotationScale, 1);
            int textWidth = client.font.width(caption);
            graphics.fill(-2, -1, textWidth + 2, 10, style.background());
            graphics.drawString(client.font, caption, 0, 0, WeaponPlatformStyle.TEXT, false);
            graphics.pose().popPose();
            if (style.shows(HudModuleStyle.HEALTH) && right > left) {
                graphics.fill(left, bottom + 2, right, bottom + 5, style.background());
                int length = Math.round((right - left) * Mth.clamp(target.health() / Math.max(1, target.maximum()), 0, 1));
                graphics.fill(left, bottom + 2, left + length, bottom + 4, color);
            }
        }
    }
}
