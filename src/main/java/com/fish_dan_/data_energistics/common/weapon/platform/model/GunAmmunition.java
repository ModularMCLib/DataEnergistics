package com.fish_dan_.data_energistics.common.weapon.platform.model;

import com.fish_dan_.data_energistics.integration.ae.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DataKey;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.EchoKey;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.StellarFluxKey;

import appeng.api.stacks.AEKey;

import org.jspecify.annotations.Nullable;

public enum GunAmmunition {
    AE(null, 0, 0x82F3EC),
    BINARY(DataKey.of(), 1, 0x79F494),
    DATA_FLOW(DataFlowKey.of(), 100, 0x45DFF8),
    STELLAR(StellarFluxKey.of(), 100, 0xFFC44F),
    ECHO(EchoKey.of(), 5, 0xBA83FA);

    public final @Nullable AEKey key;
    public final long amount;
    public final int color;

    GunAmmunition(@Nullable AEKey key, long amount, int color) {
        this.key = key;
        this.amount = amount;
        this.color = color;
    }
}
