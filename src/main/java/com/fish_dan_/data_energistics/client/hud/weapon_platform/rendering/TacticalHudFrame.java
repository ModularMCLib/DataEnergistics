package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

import net.minecraft.client.gui.GuiGraphics;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.joml.Vector2f;

/** A straight middle section with fixed, smoothly offset end sections, reusable in either orientation. */
public final class TacticalHudFrame {

    private final ObjectList<Vector2f> points = new ObjectArrayList<>(38);
    private final ObjectList<Vector2f> leftPoints = new ObjectArrayList<>(38);
    private final ObjectList<Vector2f> rightPoints = new ObjectArrayList<>(38);

    public TacticalHudFrame(float length) {
        this(length, 16, 24, 16);
    }

    /** The tails and bends retain their proportions while the straight middle follows the content. */
    public TacticalHudFrame(float length, float tail, float bend, float offset) {
        points.add(new Vector2f(0, 0));
        points.add(new Vector2f(tail, 0));
        for (int sample = 1; sample <= 16; sample++) {
            float t = sample / 16f;
            points.add(new Vector2f(tail + bend * t, offset * smooth(t)));
        }
        points.add(new Vector2f(length - tail - bend, offset));
        for (int sample = 1; sample <= 16; sample++) {
            float t = sample / 16f;
            points.add(new Vector2f(length - tail - bend + bend * t, offset * (1 - smooth(t))));
        }
        points.add(new Vector2f(length, 0));
        // Build the mirrored path before tessellation so both strips retain their front-facing winding.
        for (Vector2f point : points) {
            leftPoints.add(new Vector2f(-point.y, point.x));
            rightPoints.add(new Vector2f(point.y, point.x));
        }
    }

    public void horizontal(GuiGraphics graphics, float x, float y, int color, int width) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        draw(graphics, points, color, width);
        graphics.pose().popPose();
    }

    public void vertical(GuiGraphics graphics, float x, float y, boolean right, int color, int width) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        draw(graphics, right ? rightPoints : leftPoints, color, width);
        graphics.pose().popPose();
    }

    private static void draw(GuiGraphics graphics, ObjectList<Vector2f> points, int color, int width) {
        if (width > 0) DrawerHelper.drawLines(graphics, points, color, color, width);
    }

    private static float smooth(float value) {
        return value * value * (3 - 2 * value);
    }
}
