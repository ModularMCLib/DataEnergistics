package com.fish_dan_.data_energistics.ae2.digitalsupply;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import lombok.RequiredArgsConstructor;

/** Insertion-only capability whose single virtual slot converts input directly through the adapter API. */
@RequiredArgsConstructor
public final class DigitalSupplyExternalItemHandler implements IItemHandler {

    private final DigitalSupplyExternalInput input;

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        validateSlot(slot);
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        validateSlot(slot);
        int accepted = this.input.acceptItem(stack, simulate);
        if (accepted == 0) {
            return stack;
        }
        return accepted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - accepted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        validateSlot(slot);
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        validateSlot(slot);
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        validateSlot(slot);
        return this.input.acceptItem(stack, true) > 0;
    }

    private static void validateSlot(int slot) {
        if (slot != 0) {
            throw new IndexOutOfBoundsException("Invalid Digital Supply input slot: " + slot);
        }
    }
}
