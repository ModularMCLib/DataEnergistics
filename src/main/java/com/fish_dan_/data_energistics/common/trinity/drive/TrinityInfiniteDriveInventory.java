package com.fish_dan_.data_energistics.common.trinity.drive;

import com.fish_dan_.data_energistics.common.trinity.core.TrinityDataCoreStorageProfile;

import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.math.BigInteger;
import java.util.List;

/**
 * Persistent Trinity Data Core inventory for the project-owned infinite data cells.
 *
 * <p>
 * The inventory always retains its maximum physical size so that the menu has a stable vanilla slot layout on both
 * sides. Its currently usable prefix is derived from the main structure's exact storage-byte capacity.
 * </p>
 */
public final class TrinityInfiniteDriveInventory extends SimpleContainer {

    /** Four visible cells are arranged on each editor-authored drive row. */
    public static final int COLUMN_COUNT = 4;
    /** The user-defined 10%-to-eight-cell scale yields eighty cells at full 256M capacity. */
    public static final int MAXIMUM_SLOT_COUNT = 80;

    private final Runnable changeListener;

    /**
     * Creates the fixed-layout inventory and invokes {@code changeListener} whenever its persisted contents change.
     */
    public TrinityInfiniteDriveInventory(Runnable changeListener) {
        super(MAXIMUM_SLOT_COUNT);
        this.changeListener = changeListener;
        addListener(ignored -> this.changeListener.run());
    }

    /**
     * Returns the usable drive-cell count for one exact structure profile.
     *
     * <p>
     * Finite fractions are rounded up so a nonzero real storage capacity always makes at least one slot usable.
     * This also produces eight slots at exactly ten percent of the fully-populated 256M structure.
     * </p>
     */
    public static int availableSlotCount(TrinityDataCoreStorageProfile profile) {
        if (profile.unlimited()) {
            return MAXIMUM_SLOT_COUNT;
        }
        BigInteger capacity = profile.totalCapacity();
        if (capacity.signum() <= 0) {
            return 0;
        }

        BigInteger scaledCapacity = capacity.multiply(BigInteger.valueOf(MAXIMUM_SLOT_COUNT));
        BigInteger[] quotientAndRemainder = scaledCapacity.divideAndRemainder(TrinityDataCoreStorageProfile.FULL_CAPACITY);
        BigInteger roundedUp = quotientAndRemainder[1].signum() == 0 ?
                quotientAndRemainder[0] : quotientAndRemainder[0].add(BigInteger.ONE);
        return roundedUp.min(BigInteger.valueOf(MAXIMUM_SLOT_COUNT)).intValueExact();
    }

    /** Returns whether the supplied stack is an injected, recognized AE storage cell. */
    public static boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        StorageCell cell = StorageCells.getCellInventory(stack, null);
        if (cell == null) {
            return false;
        }
        return !cell.getAvailableStacks().isEmpty();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < MAXIMUM_SLOT_COUNT && accepts(stack);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!stack.isEmpty() && !accepts(stack)) {
            throw new IllegalArgumentException("Trinity infinite drive inventory only accepts injected storage cells");
        }
        super.setItem(slot, stack);
    }

    /**
     * Restores only injected storage cells and returns valid but excess or unsupported stacks for authoritative
     * world refund by the host block entity.
     */
    public List<ItemStack> readFromTag(ListTag tag, HolderLookup.Provider registries) {
        clearContent();

        List<ItemStack> rejected = new ObjectArrayList<>();
        for (int index = 0; index < tag.size(); index++) {
            ItemStack.parse(registries, tag.getCompound(index)).ifPresent(stack -> {
                if (!accepts(stack)) {
                    rejected.add(stack);
                    return;
                }
                ItemStack remainder = addItem(stack.copy());
                if (!remainder.isEmpty()) {
                    rejected.add(remainder);
                }
            });
        }
        return List.copyOf(rejected);
    }

    /**
     * Removes every stack in the inactive suffix after a structure capacity downgrade.
     */
    public List<ItemStack> removeAtOrAfter(int firstInactiveSlot) {
        if (firstInactiveSlot < 0 || firstInactiveSlot > MAXIMUM_SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid Trinity infinite drive active-slot boundary: " + firstInactiveSlot);
        }
        List<ItemStack> removed = new ObjectArrayList<>();
        for (int index = firstInactiveSlot; index < MAXIMUM_SLOT_COUNT; index++) {
            ItemStack stack = removeItemNoUpdate(index);
            if (!stack.isEmpty()) {
                removed.add(stack);
            }
        }
        if (!removed.isEmpty()) {
            setChanged();
        }
        return List.copyOf(removed);
    }
}
