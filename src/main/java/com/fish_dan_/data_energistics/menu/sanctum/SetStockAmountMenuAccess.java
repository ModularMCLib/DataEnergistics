package com.fish_dan_.data_energistics.menu.sanctum;

/** Internal bridge used by the stock amount screen to expose a per-slot unlimited setting. */
public interface SetStockAmountMenuAccess {

    boolean dataEnergistics$isUnlimited();

    int dataEnergistics$getSlot();

    void dataEnergistics$setUnlimited(boolean enabled);

    int dataEnergistics$getPolicy();

    void dataEnergistics$setPolicy(int ordinal);

    long dataEnergistics$getInitialAmount();

    long dataEnergistics$getFiniteAmount();

    void dataEnergistics$confirmLong(long amount);
}
