package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

import net.minecraft.client.gui.GuiGraphics;

import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.joml.Vector2f;

/** A straight middle section with fixed, smoothly offset end sections, reusable in either orientation. */
public final class TacticalHudFrame {

    private final ObjectList<Vector2f> points = new ObjectArrayList<>(38);

    public TacticalHudFrame(float length) {
        points.add(new Vector2f(0, 0));
        points.add(new Vector2f(16, 0));
        for (int sample = 1; sample <= 16; sample++) {
            float t = sample / 16f;
            points.add(new Vector2f(16 + 24 * t, 16 * smooth(t)));
        }
        points.add(new Vector2f(length - 40, 16));
        for (int sample = 1; sample <= 16; sample++) {
            float t = sample / 16f;
            points.add(new Vector2f(length - 40 + 24 * t, 16 * (1 - smooth(t))));
        }
        points.add(new Vector2f(length, 0));
    }

    public void horizontal(GuiGraphics graphics, float x, float y, int color, int width) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        draw(graphics, color, width);
        graphics.pose().popPose();
    }

    public void vertical(GuiGraphics graphics, float x, float y, boolean right, int color, int width) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(90));
        if (right) graphics.pose().scale(1, -1, 1);
        draw(graphics, color, width);
        graphics.pose().popPose();
    }

    private void draw(GuiGraphics graphics, int color, int width) {
        if (width > 0) DrawerHelper.drawLines(graphics, points, color, color, width);
    }

    private static float smooth(float value) {
        return value * value * (3 - 2 * value);
    }
}
