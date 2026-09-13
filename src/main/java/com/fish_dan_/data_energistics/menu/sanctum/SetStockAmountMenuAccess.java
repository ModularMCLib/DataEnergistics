package com.fish_dan_.data_energistics.menu.sanctum;

/** Internal bridge used by the stock amount screen to expose a per-slot unlimited setting. */
public interface SetStockAmountMenuAccess {

    boolean dataEnergistics$isUnlimited();

    void dataEnergistics$setUnlimited(boolean enabled);
}
