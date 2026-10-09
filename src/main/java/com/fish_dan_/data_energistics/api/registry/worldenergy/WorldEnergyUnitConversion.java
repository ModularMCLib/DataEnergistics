package com.fish_dan_.data_energistics.api.registry.worldenergy;

/** Converts the fixed AE unit used by a resource definition to its native world unit. */
public interface WorldEnergyUnitConversion {

    /** Identity conversion for resources whose native unit is already an AE unit. */
    WorldEnergyUnitConversion IDENTITY = new WorldEnergyUnitConversion() {

        @Override
        public long toNative(long aeUnits) {
            return nonNegative(aeUnits);
        }

        @Override
        public long fromNative(long nativeUnits) {
            return nonNegative(nativeUnits);
        }
    };

    /** Converts a non-negative AE amount to a non-negative native amount. */
    long toNative(long aeUnits);

    /** Converts a non-negative native amount to a non-negative AE amount. */
    long fromNative(long nativeUnits);

    private static long nonNegative(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("World-energy amounts must be non-negative");
        }
        return amount;
    }
}
