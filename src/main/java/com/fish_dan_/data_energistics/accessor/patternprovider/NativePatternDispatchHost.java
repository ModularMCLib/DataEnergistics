package com.fish_dan_.data_energistics.accessor.patternprovider;

/**
 * Marks a third-party pattern-provider host whose native {@code pushPattern} contract must remain authoritative.
 *
 * <p>
 * Marked hosts are dispatched as one conservative craft at a time. Data Energistics must not infer targeted
 * capacity, batch ownership, or pattern-wrapper matching from the shared AE2 {@code PatternProviderLogic} class.
 * </p>
 */
public interface NativePatternDispatchHost {}
