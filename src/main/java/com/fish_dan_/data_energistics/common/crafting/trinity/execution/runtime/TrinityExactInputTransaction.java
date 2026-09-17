package com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.inventory.TrinityExactWorkingInventory;

import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

import java.math.BigInteger;

/** Server-thread ownership of exact additional inputs until one provider commit either accepts or releases them. */
public final class TrinityExactInputTransaction implements AutoCloseable {

    private final TrinityExactWorkingInventory exact;
    private final ListCraftingInventory physical;
    private final Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> withdrawn = new Object2ObjectLinkedOpenHashMap<>();
    private boolean active = true;

    private TrinityExactInputTransaction(TrinityExactWorkingInventory exact, ListCraftingInventory physical) {
        this.exact = exact;
        this.physical = physical;
    }

    /** Validates the complete request before withdrawing; failure restores every completed withdrawal. */
    public static TrinityExactInputTransaction withdraw(TrinityExactWorkingInventory exact,
                                                        ListCraftingInventory physical,
                                                        Object2ObjectMap<AEKey, BigInteger> amounts) {
        for (var entry : amounts.entrySet()) {
            if (entry.getValue().signum() < 0 || exact.totalAmount(entry.getKey(), physical).compareTo(entry.getValue()) < 0) {
                throw new IllegalStateException("Exact dispatch no longer owns its prepared inputs");
            }
        }
        TrinityExactInputTransaction transaction = new TrinityExactInputTransaction(exact, physical);
        try {
            for (var entry : amounts.entrySet()) {
                if (entry.getValue().signum() > 0) {
                    exact.discard(entry.getKey(), entry.getValue(), physical);
                    transaction.withdrawn.put(entry.getKey(), entry.getValue());
                }
            }
            return transaction;
        } catch (RuntimeException failure) {
            transaction.close();
            throw failure;
        }
    }

    /** Ends CPU ownership before any fallible post-transfer accounting or observer callback. */
    public void commit() {
        this.active = false;
    }

    /** Restores only material that has not crossed the provider ownership boundary. */
    @Override
    public void close() {
        if (this.active) {
            this.active = false;
            this.withdrawn.forEach((key, amount) -> this.exact.deposit(key, amount, this.physical));
        }
    }
}
