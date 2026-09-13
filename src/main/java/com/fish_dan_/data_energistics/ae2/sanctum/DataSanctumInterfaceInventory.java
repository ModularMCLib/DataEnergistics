package com.fish_dan_.data_energistics.ae2.sanctum;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.AEKeySlotFilter;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigInventory;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;

public class DataSanctumInterfaceInventory extends ConfigInventory {

    private final IntSupplier capacityCardCountSupplier;

    public DataSanctumInterfaceInventory(Set<AEKeyType> supportedTypes,
                                         @Nullable AEKeySlotFilter slotFilter,
                                         GenericStackInv.Mode mode,
                                         int size,
                                         @Nullable Runnable listener,
                                         IntSupplier capacityCardCountSupplier) {
        super(supportedTypes, slotFilter, mode, size, listener, true);
        this.capacityCardCountSupplier = capacityCardCountSupplier;
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
    public @Nullable GenericStack getStack(int slot) {
        GenericStack stack = this.stacks[slot];
        // AE2 plans stock from this view; retain the original configuration in the serialized backing array.
        if (getMode() != Mode.STORAGE && !isSlotUnlocked(slot)) {
            return null;
        }
        return stack;
    }

    @Override
    public @Nullable AEKey getKey(int slot) {
        GenericStack stack = getStack(slot);
        return stack != null ? stack.what() : null;
    }

    @Override
    public long getAmount(int slot) {
        GenericStack stack = getStack(slot);
        return stack != null ? stack.amount() : 0;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < size(); slot++) {
            if (getStack(slot) != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<@Nullable GenericStack> toList() {
        // Snapshots, like inherited NBT serialization, retain locked configuration slots.
        return new ArrayList<>(Arrays.asList(this.stacks));
    }

    @Override
    public void setStack(int slot, @Nullable GenericStack stack) {
        GenericStack previous = this.stacks[slot];
        if (stack != null && !isSlotUnlocked(slot) && (getMode() != Mode.STORAGE || previous == null || !previous.what().equals(stack.what()) || stack.amount() > previous.amount())) {
            return;
        }
        if (stack != null) {
            if (!isSupportedType(stack.what())) {
                return;
            }
            boolean typesOnly = getMode() == Mode.CONFIG_TYPES;
            if (typesOnly && stack.amount() != 0) {
                stack = new GenericStack(stack.what(), 0);
            } else if (!typesOnly && stack.amount() <= 0) {
                if (getMode() == Mode.CONFIG_STACKS && getStack(slot) == null) {
                    stack = new GenericStack(stack.what(), 1);
                } else {
                    stack = null;
                }
            }
        }

        if (!Objects.equals(this.stacks[slot], stack)) {
            this.stacks[slot] = stack;
            onChange();
        }
    }

    @Override
    public long insert(int slot, AEKey what, long amount, Actionable mode) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount >= 0");
        }

        if (!isSlotUnlocked(slot) || !canInsert() || !isAllowedIn(slot, what)) {
            return 0;
        }

        long capacity = Long.MAX_VALUE;
        AEKey currentWhat = getKey(slot);
        long currentAmount = getAmount(slot);
        if (currentWhat != null && !currentWhat.equals(what)) {
            return 0;
        }

        long insertable = Math.min(amount, Math.max(0, capacity - currentAmount));
        if (insertable <= 0) {
            return 0;
        }

        if (mode == Actionable.MODULATE) {
            setStack(slot, new GenericStack(what, currentAmount + insertable));
            return Math.max(0, getAmount(slot) - currentAmount);
        }
        return insertable;
    }

    private int getCapacityCardCount() {
        return Math.max(0, Math.min(
                DataSanctumInterfaceConstants.MAX_CAPACITY_CARDS,
                this.capacityCardCountSupplier.getAsInt()));
    }

    private boolean isSlotUnlocked(int slot) {
        int pages = DataSanctumInterfaceConstants.BASE_PAGE_COUNT + getCapacityCardCount() * DataSanctumInterfaceConstants.PAGES_PER_CAPACITY_CARD;
        return slot < pages * DataSanctumInterfaceConstants.STOCK_SLOTS_PER_PAGE;
    }

    public static DataSanctumInterfaceInventory config(Runnable listener, IntSupplier capacityCardCountSupplier) {
        return config(DataSanctumInterfaceConstants.LOGIC_SLOT_COUNT, listener, capacityCardCountSupplier);
    }

    public static DataSanctumInterfaceInventory config(int size, Runnable listener, IntSupplier capacityCardCountSupplier) {
        return new DataSanctumInterfaceInventory(
                AEKeyTypes.getAll(),
                null,
                GenericStackInv.Mode.CONFIG_STACKS,
                size,
                listener,
                capacityCardCountSupplier);
    }

    public static DataSanctumInterfaceInventory storage(AEKeySlotFilter slotFilter, Runnable listener, IntSupplier capacityCardCountSupplier) {
        return storage(DataSanctumInterfaceConstants.LOGIC_SLOT_COUNT, slotFilter, listener, capacityCardCountSupplier);
    }

    public static DataSanctumInterfaceInventory storage(int size, AEKeySlotFilter slotFilter, Runnable listener, IntSupplier capacityCardCountSupplier) {
        return new DataSanctumInterfaceInventory(
                AEKeyTypes.getAll(),
                slotFilter,
                GenericStackInv.Mode.STORAGE,
                size,
                listener,
                capacityCardCountSupplier);
    }
}
