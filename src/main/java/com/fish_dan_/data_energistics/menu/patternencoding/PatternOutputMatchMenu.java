package com.fish_dan_.data_energistics.menu.patternencoding;

/**
 * Exposes per-slot matching rules of the encoded processing pattern to its amount sub-screen.
 */
public interface PatternOutputMatchMenu {

    /** Returns all processing input/output slots currently marked for same-item matching. */
    int data_energistics$getProcessingSameItemMask();

    /** Returns whether the selected processing slot accepts the same registered item regardless of components. */
    boolean data_energistics$isProcessingSameItem(int inputIndex, int outputIndex);

    /** Updates one per-pattern processing slot and sends the client action when called client-side. */
    void data_energistics$setProcessingSameItem(int inputIndex, int outputIndex, boolean enabled);

    default boolean data_energistics$isProcessingOutputSameItem() {
        return data_energistics$isProcessingSameItem(-1, 0);
    }

    default void data_energistics$setProcessingOutputSameItem(boolean enabled) {
        data_energistics$setProcessingSameItem(-1, 0, enabled);
    }
}
