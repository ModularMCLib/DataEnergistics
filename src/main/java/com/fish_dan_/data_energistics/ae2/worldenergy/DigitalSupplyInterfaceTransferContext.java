package com.fish_dan_.data_energistics.ae2.worldenergy;

import com.fish_dan_.data_energistics.api.registry.worldenergy.WorldEnergyTransferContext;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import org.jspecify.annotations.Nullable;

/** Adapts an AE grid's ordinary aggregate storage to the public two-phase transfer contract. */
public final class DigitalSupplyInterfaceTransferContext implements WorldEnergyTransferContext {

    private final @Nullable MEStorage storage;
    private final IActionSource source;

    public DigitalSupplyInterfaceTransferContext(@Nullable MEStorage storage, IActionSource source) {
        this.storage = storage;
        this.source = source;
    }

    @Override
    public long simulateNetworkExtract(AEKey key, long amount) {
        return transfer(key, amount, Actionable.SIMULATE, Operation.EXTRACT);
    }

    @Override
    public long commitNetworkExtract(AEKey key, long amount) {
        return transfer(key, amount, Actionable.MODULATE, Operation.EXTRACT);
    }

    @Override
    public long simulateNetworkInsert(AEKey key, long amount) {
        return transfer(key, amount, Actionable.SIMULATE, Operation.INSERT);
    }

    @Override
    public long commitNetworkInsert(AEKey key, long amount) {
        return transfer(key, amount, Actionable.MODULATE, Operation.INSERT);
    }

    private long transfer(AEKey key, long amount, Actionable mode, Operation operation) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Transfer amount must be non-negative");
        }
        if (amount == 0L || this.storage == null) {
            return 0L;
        }
        return operation == Operation.EXTRACT ? this.storage.extract(key, amount, mode, this.source) : this.storage.insert(key, amount, mode, this.source);
    }

    private enum Operation {
        INSERT,
        EXTRACT
    }
}
