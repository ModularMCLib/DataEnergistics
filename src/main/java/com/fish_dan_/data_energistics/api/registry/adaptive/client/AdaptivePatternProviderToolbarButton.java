package com.fish_dan_.data_energistics.api.registry.adaptive.client;

import net.minecraft.client.gui.components.Button;

/**
 * One screen-owned toolbar widget and its state synchronization callback.
 * The callback runs on the client thread before layout, must not send packets, and may set visibility,
 * activity and presentation from the current menu. Both members must be non-null.
 */
public record AdaptivePatternProviderToolbarButton(Button button, Runnable update) {

    /**
     * Refreshes local state, then hides and disables actions absent from the current provider declaration.
     */
    public void synchronize(boolean declared) {
        button.visible = true;
        button.active = true;
        update.run();
        button.visible &= declared;
        button.active &= button.visible;
    }
}
