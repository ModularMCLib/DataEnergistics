package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

import net.minecraft.client.gui.GuiGraphics;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.joml.Vector2f;

/** Single sidebar contours and staggered compass shoulders share the same stroke rendering. */
public final class TacticalHudFrame {

    private final ObjectList<Stroke> ends = new ObjectArrayList<>(2);
    private final ObjectList<ObjectList<Vector2f>> compassEnds = new ObjectArrayList<>(4);
    private final Stroke middle;

    public TacticalHudFrame(float length) {
        this(length, 16, 24, 16);
    }

    /** The tails and bends retain their proportions while the straight middle follows the content. */
    public TacticalHudFrame(float length, float tail, float bend, float offset) {
        addEnds(end(tail, bend, offset), length);
        addCompassEnds(compassShoulder(tail, bend, offset, false), length);
        addCompassEnds(compassShoulder(tail, bend, offset, true), length);
        middle = new Stroke(ObjectList.of(new Vector2f(tail + bend, offset), new Vector2f(length - tail - bend, offset)));
    }

    /** End contours remain separate; compass ticks and text occupy the gap between them. */
    public void horizontal(GuiGraphics graphics, float x, float y, int color, float width) {
        if (width <= 0) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        for (ObjectList<Vector2f> contour : compassEnds) draw(graphics, contour, color, width);
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

    private void addCompassEnds(ObjectList<Vector2f> points, float length) {
        compassEnds.add(points);
        compassEnds.add(mirror(points, length));
    }

    private static ObjectList<Vector2f> mirror(ObjectList<Vector2f> points, float length) {
        ObjectList<Vector2f> mirrored = new ObjectArrayList<>(points.size());
        for (Vector2f point : points) mirrored.add(new Vector2f(length - point.x, point.y));
        return mirrored;
    }

    private static ObjectList<Vector2f> end(float tail, float bend, float offset) {
        ObjectList<Vector2f> points = new ObjectArrayList<>(18);
        points.add(new Vector2f(0, 0));
        points.add(new Vector2f(tail, 0));
        for (int sample = 1; sample <= 16; sample++) {
            float t = sample / 16f;
            points.add(new Vector2f(tail + bend * t, offset * smooth(t)));
        }
        return points;
    }

    private static ObjectList<Vector2f> compassShoulder(float tail, float bend, float offset, boolean accent) {
        float innerTail = bend / 3;
        float shift = accent ? innerTail / 2 : 0;
        float lift = accent ? offset / 4 : 0;
        float start = accent ? 0.4f : 0;
        ObjectList<Vector2f> points = new ObjectArrayList<>(27);
        if (!accent) points.add(new Vector2f(0, 0));
        for (int sample = 0; sample <= 24; sample++) {
            float t = start + (1 - start) * sample / 24;
            points.add(new Vector2f(tail + shift + bend * t, offset * shoulderRise(t) - lift));
        }
        points.add(new Vector2f(tail + shift + bend + innerTail, offset - lift));
        return points;
    }

    /** Rounded joins at either end of a straight incline, matching the reference's long shoulder. */
    private static float shoulderRise(float progress) {
        float corner = 0.2f;
        if (progress < corner) return progress * progress / (2 * corner * (1 - corner));
        if (progress > 1 - corner) {
            float remaining = 1 - progress;
            return 1 - remaining * remaining / (2 * corner * (1 - corner));
        }
        return (progress - corner / 2) / (1 - corner);
    }

    private static void draw(GuiGraphics graphics, ObjectList<Vector2f> points, int color, float width) {
        // LDLib expects a half-width, while HUD style widths describe the complete stroke.
        DrawerHelper.drawLines(graphics, points, color, color, width / 2);
    }

    private static float smooth(float value) {
        return value * value * (3 - 2 * value);
    }

    private record Stroke(ObjectList<Vector2f> left, ObjectList<Vector2f> right) {

        private Stroke(ObjectList<Vector2f> horizontal) {
            this(rotate(horizontal, false), rotate(horizontal, true));
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
