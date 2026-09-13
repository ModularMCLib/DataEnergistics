package com.fish_dan_.data_energistics.ae2.sanctum.inventory;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Preserves the return-bar slot indices and appends the physical/mapped stock slots for machine extraction. */
@NullMarked
public record InterfaceInventoryAccess(GenericInternalInventory returns, GenericInternalInventory stock)
        implements GenericInternalInventory {

    private GenericInternalInventory inventory(int slot) {
        if (slot < 0 || slot >= size()) throw new IndexOutOfBoundsException(slot);
        return slot < returns.size() ? returns : stock;
    }

    private int index(int slot) {
        return slot < returns.size() ? slot : slot - returns.size();
    }

    @Override
    public int size() {
        return returns.size() + stock.size();
    }

    @Override
    public @Nullable GenericStack getStack(int slot) {
        return inventory(slot).getStack(index(slot));
    }

    @Override
    public @Nullable AEKey getKey(int slot) {
        return inventory(slot).getKey(index(slot));
    }

    @Override
    public long getAmount(int slot) {
        return inventory(slot).getAmount(index(slot));
    }

    @Override
    public long getMaxAmount(AEKey key) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getCapacity(AEKeyType type) {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean canInsert() {
        return returns.canInsert();
    }

    @Override
    public boolean canExtract() {
        return stock.canExtract();
    }

    @Override
    public boolean isSupportedType(AEKeyType type) {
        return returns.isSupportedType(type);
    }

    @Override
    public boolean isAllowedIn(int slot, AEKey key) {
        return slot < returns.size() && inventory(slot).isAllowedIn(slot, key);
    }

    @Override
    public void setStack(int slot, @Nullable GenericStack value) {
        if (slot >= returns.size()) throw new UnsupportedOperationException("Stock views must be changed by insert/extract");
        inventory(slot).setStack(slot, value);
    }

    @Override
    public long insert(int slot, AEKey key, long amount, Actionable mode) {
        return slot < returns.size() ? inventory(slot).insert(slot, key, amount, mode) : 0;
    }

    @Override
    public long extract(int slot, AEKey key, long amount, Actionable mode) {
        return slot >= returns.size() ? inventory(slot).extract(index(slot), key, amount, mode) : 0;
    }

    @Override
    public void beginBatch() {
        returns.beginBatch();
        stock.beginBatch();
    }

    @Override
    public void endBatch() {
        returns.endBatch();
        stock.endBatch();
    }

    @Override
    public void endBatchSuppressed() {
        returns.endBatchSuppressed();
        stock.endBatchSuppressed();
    }

    @Override
    public void onChange() {
        returns.onChange();
        stock.onChange();
    }
}
