package com.fish_dan_.data_energistics.api.registry.adaptive;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.YesNo;

/**
 * Synchronized settings exposed to toolbar factories without exposing the internal menu implementation.
 * Use only on the client thread while its screen is open. Getters have no side effects and return non-null
 * values. Send/set methods submit menu actions; the server validates the current provider and resulting state.
 */
public interface AdaptivePatternProviderToolbarMenu {

    /**
     * Current ordinary AE2 blocking mode.
     */
    YesNo getBlockingMode();

    /**
     * Current AE2 crafting-lock setting, independent of the current lock reason.
     */
    LockCraftingMode getLockCraftingMode();

    /**
     * Whether the provider is listed in the pattern access terminal.
     */
    YesNo getShowInAccessTerminal();

    /**
     * Current zero-based page.
     */
    int getPageIndex();

    /**
     * Current number of pages, at least one.
     */
    int getTotalPages();

    /**
     * Requests a page; the server clamps it to the current slot capacity.
     */
    void sendSetPage(int pageIndex);

    /**
     * Synchronized input-filter state.
     */
    boolean isAdvancedAeFilteredImportEnabled();

    /**
     * Requests input-filter state; ignored when the provider does not declare that control.
     */
    void sendSetAdvancedAeFilteredImport(boolean enabled);

    /**
     * Synchronized target-storage extraction state.
     */
    boolean isResonatingPullEnabled();

    /**
     * Requests extraction state; ignored when the provider does not declare that control.
     */
    void sendSetResonatingPullEnabled(boolean enabled);

    /**
     * Whether the installed upgrades currently include the redstone tuning card.
     */
    boolean hasRedstoneTuningCard();

    /**
     * Synchronized redstone tuning mode ordinal.
     */
    int getRedstoneTuningMode();

    /**
     * Requests a redstone mode; the server validates the ordinal before applying it to the provider.
     */
    void setRedstoneTuningMode(int ordinal);
}
