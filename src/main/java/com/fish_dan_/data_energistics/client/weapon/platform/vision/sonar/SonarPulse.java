package com.fish_dan_.data_energistics.client.weapon.platform.vision.sonar;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;

/** One expanding echo emitted from the player's position at the start of each four-second cycle. */
public final class SonarPulse {

    private static final float PERIOD = 80;
    private Vec3 origin = Vec3.ZERO;
    private double started = Double.NaN;
    private float age;

    public void update(double time, Vec3 player) {
        if (Double.isNaN(started) || time < started || time - started >= PERIOD) {
            origin = player;
            started = time;
        }
        age = (float) (time - started);
    }

    public void apply(ShaderInstance shader, Vec3 player) {
        shader.safeGetUniform("SonarOrigin").set((float) (origin.x - player.x), (float) (origin.y - player.y), (float) (origin.z - player.z));
        shader.safeGetUniform("SonarAge").set(age);
    }

    public void clear() {
        started = Double.NaN;
    }
}
