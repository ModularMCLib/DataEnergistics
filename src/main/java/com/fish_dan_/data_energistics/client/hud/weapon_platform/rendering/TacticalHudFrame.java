package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

import net.minecraft.client.gui.GuiGraphics;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.joml.Vector2f;

/** A single sidebar contour, with separate parallel end accents used only by the compass. */
public final class TacticalHudFrame {

    private final ObjectList<Stroke> ends = new ObjectArrayList<>(2);
    private final ObjectList<ObjectList<Vector2f>> accents = new ObjectArrayList<>(2);
    private final Stroke middle;

    public TacticalHudFrame(float length) {
        this(length, 16, 24, 16);
    }

    /** The tails and bends retain their proportions while the straight middle follows the content. */
    public TacticalHudFrame(float length, float tail, float bend, float offset) {
        addEnds(end(0, tail, bend, offset, 0, 1), length);
        ObjectList<Vector2f> accent = end(tail / 2, tail, bend, offset, -offset, 0.875f);
        accents.add(accent);
        accents.add(mirror(accent, length));
        middle = new Stroke(ObjectList.of(new Vector2f(tail + bend, offset), new Vector2f(length - tail - bend, offset)));
    }

    /** End contours remain separate; compass ticks and text occupy the gap between them. */
    public void horizontal(GuiGraphics graphics, float x, float y, int color, float width) {
        if (width <= 0) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        for (Stroke stroke : ends) draw(graphics, stroke.horizontal(), color, width);
        for (ObjectList<Vector2f> accent : accents) draw(graphics, accent, color, width);
        graphics.pose().popPose();
    }

    public void vertical(GuiGraphics graphics, float x, float y, boolean right, int color, int width) {
        if (width <= 0) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        draw(graphics, right ? middle.right() : middle.left(), color, width);
        for (Stroke stroke : ends) draw(graphics, right ? stroke.right() : stroke.left(), color, width);
        graphics.pose().popPose();
    }

    private void addEnds(ObjectList<Vector2f> points, float length) {
        ends.add(new Stroke(points));
        ends.add(new Stroke(mirror(points, length)));
    }

    private static ObjectList<Vector2f> mirror(ObjectList<Vector2f> points, float length) {
        ObjectList<Vector2f> mirrored = new ObjectArrayList<>(points.size());
        for (Vector2f point : points) mirrored.add(new Vector2f(length - point.x, point.y));
        return mirrored;
    }

    private static ObjectList<Vector2f> end(float start, float tail, float bend, float offset, float normalOffset, float progress) {
        ObjectList<Vector2f> points = new ObjectArrayList<>(18);
        points.add(new Vector2f(start, normalOffset));
        points.add(new Vector2f(tail, normalOffset));
        for (int sample = 1; sample <= 16; sample++) {
            float t = sample / 16f * progress;
            // Offset the same curve along its normal; shifting the bend itself makes the contours cross.
            float slope = 6 * offset * t * (1 - t) / bend;
            float distance = normalOffset / (float) Math.sqrt(1 + slope * slope);
            points.add(new Vector2f(tail + bend * t - slope * distance, offset * smooth(t) + distance));
        }
        return points;
    }

    private static void draw(GuiGraphics graphics, ObjectList<Vector2f> points, int color, float width) {
        // LDLib expects a half-width, while HUD style widths describe the complete stroke.
        DrawerHelper.drawLines(graphics, points, color, color, width / 2);
    }

    private static float smooth(float value) {
        return value * value * (3 - 2 * value);
    }

    private record Stroke(ObjectList<Vector2f> horizontal, ObjectList<Vector2f> left, ObjectList<Vector2f> right) {

        private Stroke(ObjectList<Vector2f> horizontal) {
            this(horizontal, rotate(horizontal, false), rotate(horizontal, true));
        }

        private static ObjectList<Vector2f> rotate(ObjectList<Vector2f> points, boolean right) {
            ObjectList<Vector2f> rotated = new ObjectArrayList<>(points.size());
            // Transform coordinates before tessellation to preserve front-facing strips on both sides.
            for (Vector2f point : points) {
                float across = right ? point.y : -point.y;
                float along = point.x;
                rotated.add(new Vector2f(across, along));
            }
            return rotated;
        }
    }
}
