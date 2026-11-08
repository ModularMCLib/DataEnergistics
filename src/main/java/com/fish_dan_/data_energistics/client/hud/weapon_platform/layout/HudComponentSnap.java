package com.fish_dan_.data_energistics.client.hud.weapon_platform.layout;

import org.jspecify.annotations.Nullable;

/** One nearby adjacency position; distances and bounds all use the owning HUD viewport's coordinates. */
public record HudComponentSnap(float x, float y, float distanceSquared) {

    /** Only adjacent placements inside the viewport and within the magnetic radius are eligible. */
    public static @Nullable HudComponentSnap nearby(Bounds moving, Bounds target, float gap, float radius, int viewportWidth, int viewportHeight) {
        HudComponentSnap nearest = null;
        for (int edge = 0; edge < 4; edge++) {
            float x = switch (edge) {
                case 0, 1 -> target.x;
                case 2 -> target.x - gap - moving.width;
                default -> target.x + target.width + gap;
            };
            float y = switch (edge) {
                case 0 -> target.y - gap - moving.height;
                case 1 -> target.y + target.height + gap;
                default -> target.y;
            };
            x = Math.round(x);
            y = Math.round(y);
            if (x < 0 || y < 0 || x + moving.width > viewportWidth || y + moving.height > viewportHeight) continue;
            float dx = moving.x - x, dy = moving.y - y;
            float distance = dx * dx + dy * dy;
            if (distance <= radius * radius && (nearest == null || distance < nearest.distanceSquared)) nearest = new HudComponentSnap(x, y, distance);
        }
        return nearest;
    }

    /** Rendered component bounds after its local zoom and viewport scaling, with positive dimensions. */
    public record Bounds(float x, float y, float width, float height) {}
}
