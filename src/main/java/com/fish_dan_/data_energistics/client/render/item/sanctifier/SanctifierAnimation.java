package com.fish_dan_.data_energistics.client.render.item.sanctifier;

/**
 * A cosmetic draw sequence. One instance belongs to one rendered hand and is advanced
 * once per unpaused client tick. It never changes an item's energy, use duration or attack timing.
 */
public final class SanctifierAnimation {

    public static final int DRAW_TICKS = 32;
    private static final float[] TIMES = {0, 0.15F, 0.4F, 0.72F, 0.9F, 1.08F, 1.2F};
    private static final float[] SWORD_Y = {0, 0, -18, -36, -36, -9, 0};
    private static final float[] SCABBARD_X = {0, 0, 0, 0, -8, -8, -8};
    private static final float[] SCABBARD_Y = {0, 0, 4.5F, 9, 9, 2.25F, -4};
    private static final float[] SCABBARD_Z = {0, 0, 0, 0, 1.2F, 1.2F, 1.2F};
    private static final float[] SCABBARD_ROLL = {0, 0, 0, 0, 0, -135, -180};

    private int previousDraw;
    private int draw;

    /**
     * Selection starts drawing even with an empty battery. Switching away resets the sequence.
     */
    public void tick(boolean held) {
        this.previousDraw = this.draw;
        if (!held) {
            this.previousDraw = 0;
            this.draw = 0;
        } else {
            if (this.draw < DRAW_TICKS) this.draw++;
        }
    }

    /**
     * Samples the two tick positions without advancing the sequence. Partial tick is in [0, 1].
     */
    public float draw(float partialTick) {
        unit(partialTick);
        return (this.previousDraw + (this.draw - this.previousDraw) * partialTick) / DRAW_TICKS;
    }

    /**
     * Samples the same 1.2-second key poses stored in the editable Blockbench source. Draw, swing
     * and charge are finite fractions in [0, 1]; age is a finite local visual clock in ticks.
     * The sword exits through the open mouth; the scabbard pivots only after 45 units of separation.
     */
    public static Pose sample(float draw, float swing, float charge, float age) {
        unit(draw);
        unit(swing);
        unit(charge);
        if (!Float.isFinite(age)) throw new IllegalArgumentException("Non-finite sanctifier animation age");
        float time = draw * 1.2F;
        float ready = ramp(draw, 0.90F, 1.0F);
        float slash = (float) Math.sin(swing * Math.PI);
        float anticipation = (float) Math.sin(Math.sqrt(swing) * Math.PI * 2);
        float breathing = (float) Math.sin(age * Math.PI / 30.0);
        return new Pose(
                channel(SWORD_Y, time),
                channel(SCABBARD_X, time), channel(SCABBARD_Y, time), channel(SCABBARD_Z, time),
                channel(SCABBARD_ROLL, time), ramp(time, 0, 0.12F) * 38,
                ramp(time, 0.62F, 1.02F),
                ready * (-55 * charge + 12 * slash),
                ready * (25 * slash),
                ready * (16 * charge + 50 * anticipation + 0.3F * breathing * (1 - slash)),
                ready * (2 * charge - 4 * slash));
    }

    private static float channel(float[] values, float time) {
        for (int i = 1; i < TIMES.length; i++) {
            if (time <= TIMES[i]) {
                float alpha = (time - TIMES[i - 1]) / (TIMES[i] - TIMES[i - 1]);
                return values[i - 1] + (values[i] - values[i - 1]) * alpha;
            }
        }
        return values[values.length - 1];
    }

    private static float ramp(float value, float start, float end) {
        return Math.clamp((value - start) / (end - start), 0, 1);
    }

    private static void unit(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException("Sanctifier animation fraction must be in [0, 1]: " + value);
        }
    }

    /**
     * Immutable model-unit translations and degree rotations, with an ignition fraction.
     */
    public record Pose(float swordY, float scabbardX, float scabbardY, float scabbardZ,
                       float scabbardRoll, float latchRoll, float ignition,
                       float swordPitch, float swordYaw, float swordRoll, float combatY) {
    }
}
