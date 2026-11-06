package com.fish_dan_.data_energistics.util.client.vision;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import lombok.Getter;

/** One expanding echo emitted from the player's position at the start of each four-second cycle. */
@OnlyIn(Dist.CLIENT)
public final class SonarPulse {

    public static final int RADIUS = 48;
    private static final float SPEED = 1.2f;
    private static final float PERIOD = 80;
    @Getter
    private Vec3 origin = Vec3.ZERO;
    private double started = Double.NaN;
    private float age;

    public void update(double time, Vec3 player) {
        updateState(time, player);
    }

    public boolean updateAndReport(double time, Vec3 player) {
        return updateState(time, player);
    }

    private boolean updateState(double time, Vec3 player) {
        boolean startedScan = Double.isNaN(started) || time < started || time - started >= PERIOD;
        if (startedScan) {
            origin = player;
            started = time;
        }
        age = (float) (time - started);
        return startedScan;
    }

    public double radiusSquared() {
        double radius = Math.min(RADIUS, age * SPEED);
        return radius * radius;
    }

    public void apply(ShaderInstance shader, Vec3 player) {
        shader.safeGetUniform("SonarOrigin").set((float) (origin.x - player.x), (float) (origin.y - player.y), (float) (origin.z - player.z));
        shader.safeGetUniform("SonarAge").set(age);
    }

    public void clear() {
        started = Double.NaN;
    }
}
