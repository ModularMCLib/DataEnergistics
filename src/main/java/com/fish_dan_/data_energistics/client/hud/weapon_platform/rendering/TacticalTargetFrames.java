package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModule;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModuleStyle;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Frames remain visible through walls, with an occluded shade of the same relation color. */
public final class TacticalTargetFrames extends UIElement {

    private final TacticalTargetBatch batch = new TacticalTargetBatch();

    public TacticalTargetFrames() {
        style(value -> value.overflowVisible(true));
        addEventListener(UIEvents.REMOVED, event -> batch.close());
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        var client = context.mc;
        if (client.level == null || client.player == null) return;
        HudModuleStyle style = TacticalHudPreferences.get(HudModule.TARGETS);
        int width = client.getWindow().getGuiScaledWidth(), height = client.getWindow().getGuiScaledHeight();
        float annotationScale = style.scale() * Math.min(width / 854f, height / 480f);
        var snapshot = PlatformClientState.snapshot;
        var graphics = context.graphics;
        graphics.flush();
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
            if (line > 0) {
                batch.fill(context, left, top, left + horizontal, top + line, color);
                batch.fill(context, left, top, left + line, top + vertical, color);
                batch.fill(context, right - horizontal, top, right, top + line, color);
                batch.fill(context, right - line, top, right, top + vertical, color);
                batch.fill(context, left, bottom - line, left + horizontal, bottom, color);
                batch.fill(context, left, bottom - vertical, left + line, bottom, color);
                batch.fill(context, right - horizontal, bottom - line, right, bottom, color);
                batch.fill(context, right - line, bottom - vertical, right, bottom, color);
                if (locked) {
                    int lockLeft = Math.max(0, left - frameGap), lockTop = Math.max(0, top - frameGap);
                    int lockRight = Math.min(width, right + frameGap), lockBottom = Math.min(height, bottom + frameGap);
                    int lockColor = shaded(style.borderColor(), target.visible());
                    batch.fill(context, lockLeft, lockTop, lockRight, lockTop + line, lockColor);
                    batch.fill(context, lockLeft, lockBottom - line, lockRight, lockBottom, lockColor);
                    batch.fill(context, lockLeft, lockTop + line, lockLeft + line, lockBottom - line, lockColor);
                    batch.fill(context, lockRight - line, lockTop + line, lockRight, lockBottom - line, lockColor);
                }
            }
            Component caption = style.shows(HudModuleStyle.NAME) ? target.name() : Component.empty();
            if (style.shows(HudModuleStyle.BINARY) && target.binary() > 0) caption = caption.copy().append(" [" + target.binary() + "/32]");
            if (!caption.getString().isEmpty()) drawAnnotation(context, ObjectList.of(caption),
                    (left + right - client.font.width(caption) * annotationScale) / 2f,
                    top - frameGap - 12 * annotationScale, annotationScale, style.background(), annotationColor);

            var telemetry = new ObjectArrayList<Component>(2);
            if (style.shows(HudModuleStyle.DISTANCE)) telemetry.add(Component.literal(distance(client.player.getEyePosition().distanceTo(body.getCenter()), false)));
            if (style.shows(HudModuleStyle.SPEED)) telemetry.add(Component.literal(Math.round(target.velocity().length() * 200) / 10d + " m/s"));
            if (!telemetry.isEmpty()) {
                int telemetryWidth = 0;
                for (Component value : telemetry) telemetryWidth = Math.max(telemetryWidth, client.font.width(value));
                float telemetryX = right + frameGap + 4 * annotationScale;
                if (telemetryX + (telemetryWidth + 2) * annotationScale > width) telemetryX = left - frameGap - (telemetryWidth + 4) * annotationScale;
                drawAnnotation(context, telemetry, telemetryX, (top + bottom - telemetry.size() * 10 * annotationScale) / 2f,
                        annotationScale, style.background(), annotationColor);
            }

            boolean showAltitude = style.shows(HudModuleStyle.ALTITUDE), showMotion = style.shows(HudModuleStyle.MOTION), showAssignments = style.shows(HudModuleStyle.TARGET);
            int lowerLines = (showAltitude ? 1 : 0) + (showMotion ? 1 : 0) + (showAssignments ? 1 : 0);
            float lowerY = Math.min(bottom + frameGap + (style.shows(HudModuleStyle.HEALTH) ? 8 : 3) * annotationScale,
                    height - (lowerLines * 12 + 1) * annotationScale);
            if (showAltitude) {
                Component altitude = Component.literal(distance(body.minY - client.player.getY(), true));
                drawAnnotation(context, ObjectList.of(altitude), (left + right - client.font.width(altitude) * annotationScale) / 2f,
                        lowerY, annotationScale, style.background(), annotationColor);
                lowerY += 12 * annotationScale;
            }
            if (showMotion) {
                drawMotion(context, body.getCenter(), target.velocity(), (left + right) / 2f,
                        Mth.clamp(lowerY + 4 * annotationScale, 4 * annotationScale, height - 4 * annotationScale), annotationScale, annotationColor);
                lowerY += 12 * annotationScale;
            }
            if (showAssignments) {
                var weapons = Component.empty();
                for (var gun : snapshot.guns()) {
                    if (gun.target() != target.entity() || !gun.settings().enabled()) continue;
                    if (!weapons.getSiblings().isEmpty()) weapons.append(" · ");
                    weapons.append("#" + (gun.slot() + 1));
                }
                if (!weapons.getSiblings().isEmpty()) {
                    Component assignment = Component.translatable("hud.data_energistics.weapon_platform.target_weapons", weapons);
                    drawAnnotation(context, ObjectList.of(assignment), (left + right - client.font.width(assignment) * annotationScale) / 2f,
                            lowerY, annotationScale, style.background(), annotationColor);
                }
            }
            if (style.shows(HudModuleStyle.HEALTH) && right > left) {
                batch.fill(context, left, bottom + 2, right, bottom + 5, style.background());
                int length = Math.round((right - left) * Mth.clamp(target.health() / Math.max(1, target.maximum()), 0, 1));
                batch.fill(context, left, bottom + 2, left + length, bottom + 4, color);
            }
            batch.draw(context);
        }
        graphics.flush();
    }

    private void drawAnnotation(GUIContext context, ObjectList<Component> lines, float x, float y, float scale, int background, int color) {
        int width = context.mc.getWindow().getGuiScaledWidth(), height = context.mc.getWindow().getGuiScaledHeight();
        int textWidth = 0;
        for (Component text : lines) textWidth = Math.max(textWidth, context.mc.font.width(text));
        float availableWidth = width / scale - 4;
        int maximumWidth = Math.max(1, (int) availableWidth);
        boolean clipped = textWidth > maximumWidth;
        textWidth = Math.min(textWidth, maximumWidth);
        float left = Math.max(2 * scale, Math.min(x, width - (textWidth + 2) * scale));
        float top = Math.max(scale, Math.min(y, height - (lines.size() * 10 + 1) * scale));
        var graphics = context.graphics;
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        graphics.pose().scale(scale, scale, 1);
        batch.fill(context, -2, -1, textWidth + 2, lines.size() * 10, background);
        var prepared = new ObjectArrayList<FormattedCharSequence>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            var text = clipped ? Language.getInstance().getVisualOrder(context.mc.font.substrByWidth(line, textWidth)) : line.getVisualOrderText();
            prepared.add(text);
        }
        batch.annotate(graphics.pose().last().pose(), prepared, textWidth, color);
        graphics.pose().popPose();
    }

    private static String distance(double meters, boolean signed) {
        boolean kilometers = Math.abs(meters) >= 1000;
        double value = Math.round((kilometers ? meters / 1000 : meters) * 10) / 10d;
        return (signed && value >= 0 ? "+" : "") + value + (kilometers ? " km" : " m");
    }

    private void drawMotion(GUIContext context, Vec3 origin, Vec3 velocity, float x, float y, float scale, int color) {
        var projected = TacticalProjection.project(origin, origin.add(velocity.scale(20)),
                context.mc.getWindow().getGuiScaledWidth(), context.mc.getWindow().getGuiScaledHeight());
        var graphics = context.graphics;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        if (projected != null && projected.to().distanceSquared(projected.from()) > 0.01f) {
            float angle = (float) Math.atan2(projected.to().y - projected.from().y, projected.to().x - projected.from().x);
            graphics.pose().mulPose(Axis.ZP.rotation(angle));
            batch.fill(context, -8, -1, -2, 0, color);
        }
        batch.fill(context, -2, -2, 2, -1, color);
        batch.fill(context, -2, 1, 2, 2, color);
        batch.fill(context, -3, -1, -2, 1, color);
        batch.fill(context, 2, -1, 3, 1, color);
        graphics.pose().popPose();
    }

    private static int shaded(int color, boolean visible) {
        if (visible) return color;
        return color & 0xff000000 | ((color >> 16 & 255) * 3 / 5 << 16) | ((color >> 8 & 255) * 3 / 5 << 8) | (color & 255) * 3 / 5;
    }
}
