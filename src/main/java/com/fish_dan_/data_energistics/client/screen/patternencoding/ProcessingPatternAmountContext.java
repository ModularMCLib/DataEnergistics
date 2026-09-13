package com.fish_dan_.data_energistics.client.screen.patternencoding;

/** Identifies the processing slot whose amount sub-screen is currently open. */
public interface ProcessingPatternAmountContext {

    /** @return input slot index, or {@code -1} when the target is an output */
    int data_energistics$getProcessingInputAmountTarget();

    /** @return output slot index, or {@code -1} when the target is an input */
    int data_energistics$getProcessingOutputAmountTarget();
}
