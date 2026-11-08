package com.fish_dan_.data_energistics.client.hud.weapon_platform.layout;

/** Fits a component into a positive GUI viewport while retaining its normalized travel position. */
public record HudViewportPlacement(int x, int y, int width, int height, float scale) {

    public static HudViewportPlacement resolve(int viewportWidth, int viewportHeight, int width, int height,
                                               float zoom, double x, double y) {
        float scale = Math.min(zoom * Math.min(viewportWidth / 854f, viewportHeight / 480f),
                Math.min((float) viewportWidth / width, (float) viewportHeight / height));
        return new HudViewportPlacement((int) ((viewportWidth - width * scale) * x),
                (int) ((viewportHeight - height * scale) * y), width, height, scale);
    }
}
