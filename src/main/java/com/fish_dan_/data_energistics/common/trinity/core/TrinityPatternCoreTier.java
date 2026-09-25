package com.fish_dan_.data_energistics.common.trinity.core;

/**
 * Defines the fixed physical capacities of the three Trinity pattern processing core tiers.
 *
 * <p>
 * The capacities are complete nine-slot UI rows.
 * </p>
 */
public enum TrinityPatternCoreTier {

    STANDARD(72),
    EXTENDED(144),
    OVERLIMIT(576);

    private final int patternCapacity;

    TrinityPatternCoreTier(int patternCapacity) {
        this.patternCapacity = patternCapacity;
    }

    /**
     * Returns the current capacity, always divisible by nine.
     */
    public int patternCapacity() {
        return this.patternCapacity;
    }

    /**
     * Returns whether the supplied capacity belongs to one current physical core tier.
     */
    public static boolean supportsPatternCapacity(int capacity) {
        for (TrinityPatternCoreTier tier : values()) {
            if (tier.patternCapacity == capacity) {
                return true;
            }
        }
        return false;
    }
}
