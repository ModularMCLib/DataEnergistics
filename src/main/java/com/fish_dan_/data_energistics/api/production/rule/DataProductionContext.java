package com.fish_dan_.data_energistics.api.production.rule;

/** Values captured before simulation or an actual attack, with damage filled after the hit. */
public record DataProductionContext(long vanillaExperience, double currentHealth, double effectiveDamage, boolean living) {

    public static final DataProductionContext NON_LIVING = new DataProductionContext(0, 0, 0, false);

    public DataProductionContext {
        if (vanillaExperience < 0 || !Double.isFinite(currentHealth) || currentHealth < 0 ||
                !Double.isFinite(effectiveDamage) || effectiveDamage < 0) {
            throw new IllegalArgumentException("Production context values must be finite and non-negative");
        }
    }

    /** Converts health/damage units using half-up rounding and rejects long overflow explicitly. */
    public static long roundAmount(double value) {
        if (!Double.isFinite(value) || value < 0 || value >= 0x1p63) {
            throw new ArithmeticException("Data-production amount is outside the non-negative long range: " + value);
        }
        return Math.round(value);
    }
}
