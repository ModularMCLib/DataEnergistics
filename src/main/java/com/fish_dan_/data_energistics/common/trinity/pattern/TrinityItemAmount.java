package com.fish_dan_.data_energistics.common.trinity.pattern;

import com.fish_dan_.data_energistics.common.crafting.trinity.serialization.TrinityBigIntegerEncoding;

import appeng.api.stacks.AEItemKey;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;

/** Immutable item identity and exact positive amount used by Trinity output and refund state. */
public record TrinityItemAmount(AEItemKey key, BigInteger exactAmount) {

    public TrinityItemAmount {
        if (key == null) {
            throw new IllegalArgumentException("A Trinity item amount requires an item key");
        }
        if (exactAmount == null || exactAmount.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity item amount must be positive: " + exactAmount);
        }
        TrinityBigIntegerEncoding.encode(exactAmount, "pattern item amount");
    }

    /** Retains the long-sized construction boundary used by existing integrations. */
    public TrinityItemAmount(AEItemKey key, long amount) {
        this(key, BigInteger.valueOf(amount));
    }

    /** @return the exact amount as a long; throws when a caller requires an explicit physical chunk */
    public long amount() {
        return this.exactAmount.longValueExact();
    }

    /**
     * Captures one non-empty stack without retaining its mutable count-bearing instance.
     *
     * @param stack source item and components
     * @return counted immutable item entry
     */
    public static TrinityItemAmount of(ItemStack stack) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("A Trinity item amount requires a non-empty stack");
        }
        return new TrinityItemAmount(AEItemKey.of(stack), stack.getCount());
    }

    /**
     * Multiplies one unit stack by a positive logical count into one exact entry.
     *
     * @param stack      one logical unit output or input
     * @param multiplier positive number of identical logical units
     * @return ordered positive entries whose mathematical total equals stack count times multiplier
     */
    public static ObjectList<TrinityItemAmount> multiply(ItemStack stack, long multiplier) {
        return multiply(stack, BigInteger.valueOf(multiplier));
    }

    /** Returns one exact item entry without expanding a large amount into physical chunks. */
    public static ObjectList<TrinityItemAmount> multiply(ItemStack stack, BigInteger multiplier) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("A multiplied Trinity item amount requires a non-empty stack");
        }
        if (multiplier.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity item amount multiplier must be positive: " + multiplier);
        }

        return ObjectList.of(new TrinityItemAmount(AEItemKey.of(stack),
                BigInteger.valueOf(stack.getCount()).multiply(multiplier)));
    }

    /**
     * Reuses this immutable item identity with a different positive amount.
     *
     * @param amount replacement amount
     * @return replacement entry
     */
    public TrinityItemAmount withAmount(long amount) {
        return new TrinityItemAmount(this.key, amount);
    }

    /** Reuses this item identity with an exact positive remainder. */
    public TrinityItemAmount withAmount(BigInteger amount) {
        return new TrinityItemAmount(this.key, amount);
    }
}
