package com.fish_dan_.data_energistics.ae2.patternprovider.packaged;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.util.ConfigMenuInventory;

import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/** Two rows of real returned resources; each slot retains its full long quantity. */
public final class PackagedReturnInventory extends PatternProviderReturnInventory {

    public static final int SLOT_COUNT = 18;

    public PackagedReturnInventory(Runnable listener) {
        super(listener);
    }

    @Override
    public long getMaxAmount(AEKey key) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getCapacity(AEKeyType space) {
        return Long.MAX_VALUE;
    }

    @Override
    public long insert(int slot, AEKey what, long amount, Actionable mode) {
        if (amount < 0) {
            throw new IllegalArgumentException("Return amount must be non-negative");
        }
        // AE2 adds current + requested before clamping. Limit the request first to avoid long overflow.
        return super.insert(slot, what, Math.min(amount, Long.MAX_VALUE - getAmount(slot)), mode);
    }

    @Override
    public ConfigMenuInventory createMenuWrapper() {
        return new ConfigMenuInventory(this) {

            @Override
            public ItemStack getStackInSlot(int slot) {
                return GenericStack.wrapInItemStack(getDelegate().getStack(slot));
            }

            @Override
            public @Nullable GenericStack convertToSuitableStack(ItemStack stack) {
                GenericStack wrapped = GenericStack.unwrapItemStack(stack);
                return wrapped != null ? wrapped : super.convertToSuitableStack(stack);
            }
        };
    }
}
