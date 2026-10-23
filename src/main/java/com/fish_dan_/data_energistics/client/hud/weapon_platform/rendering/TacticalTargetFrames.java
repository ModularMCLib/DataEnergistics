package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModule;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModuleStyle;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Frames remain visible through walls, with an occluded shade of the same relation color. */
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
        var snapshot = PlatformClientState.snapshot;
        for (int targetIndex = snapshot.targets().size() - 1; targetIndex >= 0; targetIndex--) {
            var target = snapshot.targets().get(targetIndex);
            var entity = client.level.getEntity(target.entity());
            if (entity != null && !entity.isAlive()) continue;
            AABB body = new AABB(target.minimum(), target.maximumPosition());
            if (entity != null) {
                Vec3 delta = entity.getPosition(context.partialTick).subtract(entity.position());
                body = entity.getBoundingBox().move(delta);
            }
            var bounds = TacticalProjection.project(body.inflate(0.04), width, height);
            if (bounds == null) continue;
            int dx = Math.round(getPositionX()), dy = Math.round(getPositionY());
            int left = bounds.left() + dx, top = bounds.top() + dy, right = bounds.right() + dx, bottom = bounds.bottom() + dy;
            int horizontalPadding = Math.max(0, (Math.max(2, Math.round(6 * annotationScale)) - (right - left) + 1) / 2);
            int verticalPadding = Math.max(0, (Math.max(4, Math.round(10 * annotationScale)) - (bottom - top) + 1) / 2);
            left = Math.max(0, left - horizontalPadding);
            right = Math.min(width - 1, right + horizontalPadding);
            top = Math.max(0, top - verticalPadding);
            bottom = Math.min(height - 1, bottom + verticalPadding);
            if (right <= left || bottom <= top) continue;
            int color = shaded(style.relationColors() ? 0xff000000 | target.color() : style.borderColor(), target.visible());
            int annotationColor = style.relationColors() ? color : shaded(style.textColor(), target.visible());
            boolean locked = snapshot.locks().contains(target.entity());
            int line = Math.min(Math.round(style.borderWidth() * annotationScale), Math.min(right - left, bottom - top) / 2);
            if (style.borderWidth() > 0) line = Math.max(1, line);
            int frameGap = locked && line > 0 ? Math.max(line + 1, Math.round((style.shows(HudModuleStyle.HEALTH) ? 7 : 4) * annotationScale)) : 0;
            int horizontal = Math.min(Math.max(line, Math.round(8 * annotationScale)), (right - left) / 2);
            int vertical = Math.min(Math.max(line, Math.round(8 * annotationScale)), (bottom - top) / 2);
            var graphics = context.graphics;
            if (line > 0) {
                graphics.fill(left, top, left + horizontal, top + line, color);
                graphics.fill(left, top, left + line, top + vertical, color);
                graphics.fill(right - horizontal, top, right, top + line, color);
                graphics.fill(right - line, top, right, top + vertical, color);
                graphics.fill(left, bottom - line, left + horizontal, bottom, color);
                graphics.fill(left, bottom - vertical, left + line, bottom, color);
                graphics.fill(right - horizontal, bottom - line, right, bottom, color);
                graphics.fill(right - line, bottom - vertical, right, bottom, color);
                if (locked) {
                    int lockLeft = Math.max(0, left - frameGap), lockTop = Math.max(0, top - frameGap);
                    int lockRight = Math.min(width, right + frameGap), lockBottom = Math.min(height, bottom + frameGap);
                    int lockColor = shaded(style.borderColor(), target.visible());
                    graphics.fill(lockLeft, lockTop, lockRight, lockTop + line, lockColor);
                    graphics.fill(lockLeft, lockBottom - line, lockRight, lockBottom, lockColor);
                    graphics.fill(lockLeft, lockTop + line, lockLeft + line, lockBottom - line, lockColor);
                    graphics.fill(lockRight - line, lockTop + line, lockRight, lockBottom - line, lockColor);
                }
            }
            Component caption = style.shows(HudModuleStyle.NAME) ? target.name().copy() : Component.empty();
            if (style.shows(HudModuleStyle.DISTANCE)) caption = caption.copy().append(" " + Mth.floor(client.player.getEyePosition().distanceTo(body.getCenter())) + "m");
            if (style.shows(HudModuleStyle.BINARY) && target.binary() > 0) caption = caption.copy().append(" [" + target.binary() + "/32]");
            if (locked) caption = caption.copy().append(" ◈");
            graphics.pose().pushPose();
            graphics.pose().translate(left, Math.max(1, top - frameGap - 12 * annotationScale), 0);
            graphics.pose().scale(annotationScale, annotationScale, 1);
            int textWidth = client.font.width(caption);
            graphics.fill(-2, -1, textWidth + 2, 10, style.background());
            graphics.drawString(client.font, caption, 0, 0, annotationColor, false);
            graphics.pose().popPose();
            if (style.shows(HudModuleStyle.TARGET)) {
                MutableComponent weapons = Component.empty();
                for (var gun : PlatformClientState.snapshot.guns()) {
                    if (gun.target() != target.entity() || !gun.settings().enabled()) continue;
                    if (!weapons.getSiblings().isEmpty()) weapons.append(" · ");
                    weapons.append(Component.literal("#" + (gun.slot() + 1) + " ")).append(gun.item().getHoverName());
                }
                if (!weapons.getSiblings().isEmpty()) {
                    Component assignment = Component.translatable("hud.data_energistics.weapon_platform.target_weapons", weapons);
                    int wrapWidth = Math.max(1, (int) (Math.min(width * 0.6f, 240 * annotationScale) / annotationScale));
                    var lines = client.font.split(assignment, wrapWidth);
                    float labelX = Math.max(2, Math.min(left, width - wrapWidth * annotationScale - 2));
                    float labelY = Math.min(bottom + frameGap + 7 * annotationScale, height - lines.size() * 10 * annotationScale - 2);
                    graphics.pose().pushPose();
                    graphics.pose().translate(labelX, labelY, 0);
                    graphics.pose().scale(annotationScale, annotationScale, 1);
                    for (int index = 0; index < lines.size(); index++) {
                        var lineText = lines.get(index);
                        graphics.fill(-2, index * 10 - 1, client.font.width(lineText) + 2, index * 10 + 9, style.background());
                        graphics.drawString(client.font, lineText, 0, index * 10, annotationColor, false);
                    }
                    graphics.pose().popPose();
                }
            }
            if (style.shows(HudModuleStyle.HEALTH) && right > left) {
                graphics.fill(left, bottom + 2, right, bottom + 5, style.background());
                int length = Math.round((right - left) * Mth.clamp(target.health() / Math.max(1, target.maximum()), 0, 1));
                graphics.fill(left, bottom + 2, left + length, bottom + 4, color);
            }
        }
    }

    private static int shaded(int color, boolean visible) {
        if (visible) return color;
        return color & 0xff000000 | ((color >> 16 & 255) * 3 / 5 << 16) | ((color >> 8 & 255) * 3 / 5 << 8) | (color & 255) * 3 / 5;
    }
}
