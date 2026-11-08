package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

import net.minecraft.client.gui.GuiGraphics;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.joml.Vector2f;

/** Staggered double contours at either end, with an open compass center or a straight sidebar spine. */
public final class TacticalHudFrame {

    private final ObjectList<Stroke> ends = new ObjectArrayList<>(4);
    private final Stroke middle;

    public TacticalHudFrame(float length) {
        this(length, 16, 24, 16);
    }

    /** The tails and bends retain their proportions while the straight middle follows the content. */
    public TacticalHudFrame(float length, float tail, float bend, float offset) {
        addEnds(end(0, tail, bend, offset, 0), length);
        addEnds(end(tail / 2, tail + bend / 4, bend, offset, offset / 3), length);
        middle = new Stroke(ObjectList.of(new Vector2f(tail + bend, offset), new Vector2f(length - tail - bend, offset)));
    }

    /** End contours remain separate; compass ticks and text occupy the gap between them. */
    public void horizontal(GuiGraphics graphics, float x, float y, int color, int width) {
        if (width <= 0) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        for (Stroke stroke : ends) draw(graphics, stroke.horizontal(), color, width);
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
        ObjectList<Vector2f> mirrored = new ObjectArrayList<>(points.size());
        for (Vector2f point : points) mirrored.add(new Vector2f(length - point.x, point.y));
        ends.add(new Stroke(mirrored));
    }

    private static ObjectList<Vector2f> end(float start, float tail, float bend, float offset, float layer) {
        ObjectList<Vector2f> points = new ObjectArrayList<>(18);
        points.add(new Vector2f(start, layer));
        points.add(new Vector2f(tail, layer));
        for (int sample = 1; sample <= 16; sample++) {
            float t = sample / 16f;
            points.add(new Vector2f(tail + bend * t, layer + offset * smooth(t)));
        }
        return points;
    }

    private static void draw(GuiGraphics graphics, ObjectList<Vector2f> points, int color, int width) {
        DrawerHelper.drawLines(graphics, points, color, color, width);
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
