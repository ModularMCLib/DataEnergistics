package com.fish_dan_.data_energistics.integration.ae.ae2.gui.bridge;

import appeng.client.gui.widgets.ConfirmableTextField;

import org.jspecify.annotations.NullMarked;

/**
 * Exposes the native text field so the crafting amount screen can accept all 19 digits of {@link Long#MAX_VALUE}.
 */
@NullMarked
public interface NumberEntryWidgetAccessor {

    /**
     * @return AE2's owned numeric input field
     */
    ConfirmableTextField dataEnergistics$textField();
}
