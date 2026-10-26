package com.fish_dan_.data_energistics.integration.ae.ae2.patternprovider.capacity;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Read-only, server-thread capacity accounting for complete batches sent through platform inventory capabilities.
 * Every candidate batch occupies shared empty slots once, following the destination's insertion order and limits.
 */
@NullMarked
public final class SlottedPatternInputCapacity {

    private SlottedPatternInputCapacity() {}

    /** Captures the complete input batch against current sided capabilities without changing machine contents. */
    public static long limit(Level level, BlockPos position, Direction inputSide, ObjectList<GenericStack> emissions, long requestedCrafts) {
        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, position, inputSide);
        IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, position, inputSide);
        ItemStack[] itemContents = snapshotItems(items);
        FluidStack[] fluidContents = snapshotFluids(fluids);
        ObjectList<Input> inputs = inputs(emissions, items, fluids, itemContents, fluidContents);
        if (inputs.isEmpty()) {
            return requestedCrafts;
        }

        long upper = requestedCrafts;
        for (Input input : inputs) {
            upper = Math.min(upper, input.totalRoom() / input.perCraft());
        }
        long lower = 0L;
        while (lower < upper) {
            long middle = lower + (upper - lower) / 2L + 1L;
            if (fits(inputs, itemContents, fluidContents, middle)) {
                lower = middle;
            } else {
                upper = middle - 1L;
            }
        }
        return lower;
    }

    private static ItemStack[] snapshotItems(@Nullable IItemHandler inventory) {
        ItemStack[] contents = new ItemStack[inventory == null ? 0 : inventory.getSlots()];
        for (int slot = 0; slot < contents.length; slot++) {
            contents[slot] = inventory.getStackInSlot(slot).copy();
        }
        return contents;
    }

    private static FluidStack[] snapshotFluids(@Nullable IFluidHandler inventory) {
        FluidStack[] contents = new FluidStack[inventory == null ? 0 : inventory.getTanks()];
        for (int tank = 0; tank < contents.length; tank++) {
            contents[tank] = inventory.getFluidInTank(tank).copy();
        }
        return contents;
    }

    private static ObjectList<Input> inputs(ObjectList<GenericStack> emissions,
                                            @Nullable IItemHandler items,
                                            @Nullable IFluidHandler fluids,
                                            ItemStack[] itemContents,
                                            FluidStack[] fluidContents) {
        ObjectList<Input> inputs = new ObjectArrayList<>();
        for (GenericStack emission : emissions) {
            AEKey key = emission.what();
            long[] room;
            long totalRoom = 0L;
            if (key instanceof AEItemKey item && items != null) {
                room = itemRoom(items, itemContents, item);
            } else if (key instanceof AEFluidKey fluid && fluids != null) {
                room = fluidRoom(fluids, fluidContents, fluid);
            } else {
                continue;
            }
            for (long available : room) {
                totalRoom = Math.addExact(totalRoom, available);
            }
            if (key instanceof AEFluidKey fluid && fluids != null && totalRoom > 0L) {
                int probeAmount = (int) Math.min(Integer.MAX_VALUE, totalRoom);
                int accepted = fluids.fill(fluid.toStack(probeAmount), FluidAction.SIMULATE);
                if (accepted < 0 || accepted > probeAmount) {
                    throw new IllegalStateException("Inventory returned invalid simulated fluid insertion");
                }
                totalRoom = Math.min(totalRoom, accepted);
            }
            inputs.add(new Input(key, emission.amount(), room, totalRoom));
        }
        return inputs;
    }

    private static long[] itemRoom(IItemHandler inventory, ItemStack[] contents, AEItemKey key) {
        long[] room = new long[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            int limit = inventory.getSlotLimit(slot);
            if (limit < 0) {
                throw new IllegalStateException("Negative inventory item slot limit");
            }
            int available = Math.max(0, limit - contents[slot].getCount());
            if (available == 0 || !contents[slot].isEmpty() && !key.matches(contents[slot])) {
                continue;
            }
            // Probe the capability's large slot limit, rather than the item's vanilla stack size.
            ItemStack probe = key.toStack(available);
            if (!inventory.isItemValid(slot, probe)) {
                continue;
            }
            int accepted = available - inventory.insertItem(slot, probe, true).getCount();
            if (accepted < 0 || accepted > available) {
                throw new IllegalStateException("Inventory returned invalid simulated item insertion");
            }
            room[slot] = accepted;
        }
        return room;
    }

    private static long[] fluidRoom(IFluidHandler inventory, FluidStack[] contents, AEFluidKey key) {
        long[] room = new long[contents.length];
        for (int tank = 0; tank < contents.length; tank++) {
            int capacity = inventory.getTankCapacity(tank);
            if (capacity < 0) {
                throw new IllegalStateException("Negative inventory fluid tank capacity");
            }
            int available = Math.max(0, capacity - contents[tank].getAmount());
            if (available > 0 && (contents[tank].isEmpty() || key.matches(contents[tank])) && inventory.isFluidValid(tank, key.toStack(available))) {
                room[tank] = available;
            }
        }
        return room;
    }

    private static boolean fits(ObjectList<Input> inputs, ItemStack[] initialItems, FluidStack[] initialFluids, long crafts) {
        ItemStack[] items = new ItemStack[initialItems.length];
        for (int slot = 0; slot < items.length; slot++) {
            items[slot] = initialItems[slot].copy();
        }
        FluidStack[] fluids = new FluidStack[initialFluids.length];
        for (int tank = 0; tank < fluids.length; tank++) {
            fluids[tank] = initialFluids[tank].copy();
        }
        long[] occupiedItems = new long[items.length];
        long[] occupiedFluids = new long[fluids.length];

        for (Input input : inputs) {
            // The per-key upper bound ensures this multiplication cannot overflow.
            long amount = Math.multiplyExact(input.perCraft(), crafts);
            boolean accepted = switch (input.key()) {
                case AEItemKey item -> insertItems(input, item, items, occupiedItems, amount);
                case AEFluidKey fluid -> insertFluids(input, fluid, fluids, occupiedFluids, amount);
                default -> false;
            };
            if (!accepted) {
                return false;
            }
        }
        return true;
    }

    private static boolean insertItems(Input input, AEItemKey key, ItemStack[] contents, long[] occupied, long remaining) {
        for (int slot = 0; slot < contents.length && remaining > 0L; slot++) {
            ItemStack existing = contents[slot];
            if (!existing.isEmpty() && !key.matches(existing)) {
                continue;
            }
            int inserted = (int) Math.min(remaining, Math.max(0L, input.room()[slot] - occupied[slot]));
            if (inserted > 0) {
                contents[slot] = key.toStack(Math.addExact(existing.getCount(), inserted));
                occupied[slot] += inserted;
                remaining -= inserted;
            }
        }
        return remaining == 0L;
    }

    private static boolean insertFluids(Input input, AEFluidKey key, FluidStack[] contents, long[] occupied, long remaining) {
        for (int pass = 0; pass < 2; pass++) {
            for (int tank = 0; tank < contents.length && remaining > 0L; tank++) {
                FluidStack existing = contents[tank];
                if (existing.isEmpty() != (pass == 1) || !existing.isEmpty() && !key.matches(existing)) {
                    continue;
                }
                int inserted = (int) Math.min(remaining, Math.max(0L, input.room()[tank] - occupied[tank]));
                if (inserted > 0) {
                    contents[tank] = key.toStack(Math.addExact(existing.getAmount(), inserted));
                    occupied[tank] += inserted;
                    remaining -= inserted;
                }
            }
        }
        return remaining == 0L;
    }

    private record Input(AEKey key, long perCraft, long[] room, long totalRoom) {}
}
