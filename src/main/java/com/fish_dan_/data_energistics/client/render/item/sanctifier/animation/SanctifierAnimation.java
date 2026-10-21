package com.fish_dan_.data_energistics.client.render.item.sanctifier.animation;

/**
 * A cosmetic draw sequence. One instance belongs to one rendered hand and is advanced
 * once per unpaused client tick. It never changes an item's energy, use duration or attack timing.
 */
public final class SanctifierAnimation {

    public static final int DRAW_TICKS = 32;
    private static final float[] TIMES = { 0, 0.24F, 0.48F, 0.88F, 1.04F, 1.28F, 1.6F };
    private static final float[] SWORD_Y = { 0, 0, -1.2F, -1.2F, -1.2F, 0, 0 };
    private static final float[] SCABBARD_X = { 0, 0, 0, 0, -8, -8, -8 };
    private static final float[] SCABBARD_Y = { 0, 0, 0, 0, 0, -2, -4 };
    private static final float[] SCABBARD_Z = { 0, 0, 0, 0, 1.2F, 1.2F, 1.2F };
    private static final float[] SCABBARD_ROLL = { 0, 0, 0, 0, 0, 0, 0 };

    private int previousDraw;
    private int draw;

    /**
     * Selection draws even with an empty battery. Switching away reverses from the current pose,
     * so selecting the item again during sheathing does not restart or snap the sequence.
     */
    public void tick(boolean held) {
        this.previousDraw = this.draw;
        this.draw = Math.clamp(this.draw + (held ? 1 : -1), 0, DRAW_TICKS);
    }

    /** Keep the final interpolated frame alive until both tick positions have reached the holster. */
    boolean isSheathed() {
        return this.previousDraw == 0 && this.draw == 0;
    }

    boolean isDrawing() {
        return this.draw > this.previousDraw;
    }

    /**
     * Samples the two tick positions without advancing the sequence. Partial tick is in [0, 1].
     */
    public float draw(float partialTick) {
        unit(partialTick);
        return (this.previousDraw + (this.draw - this.previousDraw) * partialTick) / DRAW_TICKS;
    }

    /**
     * Samples offsets and combat channels for the 1.6-second sequence; the client rig applies its hinge.
     * Draw, swing and charge are finite fractions in [0, 1]; age is a finite local visual clock in ticks.
     * The rear slot releases the blade about the throat support; it needs no full-length extraction.
     */
    public static Pose sample(float draw, float swing, float charge, float age) {
        unit(draw);
        unit(swing);
        unit(charge);
        if (!Float.isFinite(age)) throw new IllegalArgumentException("Non-finite sanctifier animation age");
        float time = draw * 1.6F;
        float ready = ramp(draw, 0.90F, 1.0F);
        float slash = (float) Math.sin(Math.sqrt(swing) * Math.PI);
        float twist = (float) Math.sin(swing * swing * Math.PI);
        float breathing = (float) Math.sin(age * Math.PI / 30.0);
        return new Pose(
                channel(SWORD_Y, time),
                channel(SCABBARD_X, time), channel(SCABBARD_Y, time), channel(SCABBARD_Z, time),
                channel(SCABBARD_ROLL, time), ramp(draw, 0.18F, 0.28F) * 38,
                ramp(draw, 0.57F, 0.92F),
                ready * (-55 * charge - 80 * slash),
                ready * (-20 * twist),
                ready * (16 * charge - 20 * slash + 0.3F * breathing * (1 - slash)),
                ready * (2 * charge + 3.2F * (float) Math.sin(Math.sqrt(swing) * Math.PI * 2)));
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
        float fraction = Math.clamp((value - start) / (end - start), 0, 1);
        return fraction * fraction * (3 - 2 * fraction);
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
                       float swordPitch, float swordYaw, float swordRoll, float combatY) {}
}
