package com.fish_dan_.data_energistics.menu.patternencoding;

import com.fish_dan_.data_energistics.api.crafting.matching.ProcessingMatchMode;

/**
 * Exposes per-slot matching rules of the encoded processing pattern to its amount sub-screen.
 */
public interface PatternOutputMatchMenu {

    /** Sparse input modes held by the editor; server validates recipe tags before encoding. */
    String data_energistics$getProcessingInputModes();

    /** Sparse output modes, independent from all input-slot indexes. */
    String data_energistics$getProcessingOutputModes();

    /** Current selected mode. Tag encoding requires a native declaration for the selected role. */
    ProcessingMatchMode data_energistics$getProcessingMatchMode(int inputIndex, int outputIndex);

    /** Sets a mode on a real item slot. Sends a client action from client menus; no world side effects. */
    void data_energistics$setProcessingMatchMode(int inputIndex, int outputIndex, ProcessingMatchMode mode);
}
