package com.fish_dan_.data_energistics.ae2.sanctum;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

public class DataSanctumReturnInventory extends ConfigInventory {

    private boolean injectingIntoNetwork;
    private final IntSupplier capacityCardCountSupplier;

    public DataSanctumReturnInventory(@Nullable Runnable listener, IntSupplier capacityCardCountSupplier) {
        this(DataSanctumInterfaceConstants.RETURN_SLOT_COUNT, listener, capacityCardCountSupplier);
    }

    public DataSanctumReturnInventory(int size, @Nullable Runnable listener, IntSupplier capacityCardCountSupplier) {
        super(AEKeyTypes.getAll(),
                null,
                GenericStackInv.Mode.STORAGE,
                size,
                listener,
                true);
        this.capacityCardCountSupplier = capacityCardCountSupplier;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canInsert() {
        return !this.injectingIntoNetwork;
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
    public void setStack(int slot, @Nullable GenericStack stack) {
        GenericStack previous = this.stacks[slot];
        if (stack != null && !isSlotUnlocked(slot) && (previous == null || !previous.what().equals(stack.what()) || stack.amount() > previous.amount())) {
            return;
        }
        if (stack != null && (!isSupportedType(stack.what()) || stack.amount() <= 0)) {
            stack = null;
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

        AEKey currentWhat = getKey(slot);
        long currentAmount = getAmount(slot);
        if (currentWhat != null && !currentWhat.equals(what)) {
            return 0;
        }

        long capacity = Long.MAX_VALUE;
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

    public boolean injectIntoNetwork(MEStorage storage, IActionSource source) {
        boolean didSomething = false;
        this.injectingIntoNetwork = true;

        try {
            for (int slot = 0; slot < this.stacks.length; slot++) {
                GenericStack stack = this.stacks[slot];
                if (stack == null) {
                    continue;
                }

                long inserted = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, source);
                if (inserted <= 0) {
                    continue;
                }

                if (inserted >= stack.amount()) {
                    this.stacks[slot] = null;
                } else {
                    this.stacks[slot] = new GenericStack(stack.what(), stack.amount() - inserted);
                }
                didSomething = true;
            }
        } finally {
            this.injectingIntoNetwork = false;
        }

        if (didSomething) {
            onChange();
        }
        return didSomething;
    }

    public void addDrops(List<ItemStack> drops, Level level, BlockPos pos) {
        for (GenericStack stack : this.stacks) {
            if (stack != null) {
                stack.what().addDrops(stack.amount(), drops, level, pos);
            }
        }
    }

    private int getCapacityCardCount() {
        return Math.max(0, Math.min(
                DataSanctumInterfaceConstants.MAX_CAPACITY_CARDS,
                this.capacityCardCountSupplier.getAsInt()));
    }

    private boolean isSlotUnlocked(int slot) {
        int pages = DataSanctumInterfaceConstants.BASE_PAGE_COUNT + getCapacityCardCount() * DataSanctumInterfaceConstants.PAGES_PER_CAPACITY_CARD;
        return slot < pages * DataSanctumInterfaceConstants.RETURN_SLOTS_PER_PAGE;
    }
}
