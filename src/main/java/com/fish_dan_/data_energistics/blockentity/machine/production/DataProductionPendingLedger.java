package com.fish_dan_.data_energistics.blockentity.machine.production;

import com.fish_dan_.data_energistics.api.production.DataProductionOutput;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/**
 * Ordered, persistent AE-key balances awaiting delivery. Container routing may reject a key without losing it; AE
 * routing can accept every registered key directly.
 */
public final class DataProductionPendingLedger {

    @FunctionalInterface
    public interface AmountSink {

        long accept(AEKey key, long amount);
    }

    @FunctionalInterface
    public interface ItemSink {

        int accept(ItemStack stack);
    }

    private final Object2LongLinkedOpenHashMap<AEKey> contents = new Object2LongLinkedOpenHashMap<>();
    private final ObjectArrayFIFOQueue<AEKey> offerQueue = new ObjectArrayFIFOQueue<>();
    private final Runnable changeListener;

    public DataProductionPendingLedger(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    public boolean isEmpty() {
        return contents.isEmpty();
    }

    public long amount(AEKey key) {
        return contents.getOrDefault(key, 0L);
    }

    public void append(DataProductionOutput output) {
        appendAmounts(output.amounts());
    }

    public void appendAmounts(Object2LongMap<? extends AEKey> amounts) {
        if (amounts.isEmpty()) {
            return;
        }
        Object2LongLinkedOpenHashMap<AEKey> merged = new Object2LongLinkedOpenHashMap<>(this.contents);
        ObjectArrayList<AEKey> newKeys = new ObjectArrayList<>();
        for (Object2LongMap.Entry<? extends AEKey> entry : amounts.object2LongEntrySet()) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            requirePositive(key, amount);
            if (!this.contents.containsKey(key)) {
                newKeys.add(key);
            }
            merged.put(key, addExact(merged.getOrDefault(key, 0L), amount));
        }
        this.contents.clear();
        this.contents.putAll(merged);
        for (AEKey key : newKeys) {
            this.offerQueue.enqueue(key);
        }
        changeListener.run();
    }

    /** Fairly offers complete balances and decrements only what the destination confirms. */
    public long flushAmounts(AmountSink sink, int offerBudget) {
        if (offerBudget <= 0) {
            throw new IllegalArgumentException("offerBudget must be positive");
        }
        long totalAccepted = 0;
        int rejected = 0;
        boolean changed = false;
        try {
            for (int offers = 0; offers < offerBudget && !offerQueue.isEmpty(); offers++) {
                AEKey key = offerQueue.dequeue();
                long current = contents.getLong(key);
                long accepted;
                try {
                    accepted = sink.accept(key, current);
                } catch (RuntimeException | Error exception) {
                    offerQueue.enqueue(key);
                    throw exception;
                }
                if (accepted < 0 || accepted > current) {
                    offerQueue.enqueue(key);
                    throw new IllegalStateException("Production sink accepted " + accepted + " from " + current);
                }
                long remaining = current - accepted;
                if (remaining > 0) {
                    contents.put(key, remaining);
                    offerQueue.enqueue(key);
                } else {
                    contents.removeLong(key);
                }
                if (accepted == 0) {
                    rejected++;
                    if (rejected >= offerQueue.size()) {
                        break;
                    }
                } else {
                    rejected = 0;
                    changed = true;
                    totalAccepted = Math.addExact(totalAccepted, accepted);
                }
            }
        } finally {
            if (changed) {
                changeListener.run();
            }
        }
        return totalAccepted;
    }

    /** Item-only compatibility route; all non-item keys remain in the authoritative queue. */
    public long flush(ItemSink sink, int offerBudget) {
        return flushAmounts((key, amount) -> {
            if (!(key instanceof AEItemKey itemKey)) {
                return 0L;
            }
            ItemStack prototype = itemKey.toStack(1);
            if (prototype.isEmpty() || prototype.getMaxStackSize() <= 0) {
                throw new IllegalStateException("AE item key has no legal item-stack representation");
            }
            ItemStack offered = itemKey.toStack((int) Math.min(prototype.getMaxStackSize(), amount));
            int accepted = sink.accept(offered);
            if (accepted < 0 || accepted > offered.getCount()) {
                throw new IllegalStateException("Item sink accepted " + accepted + " from " + offered.getCount());
            }
            return accepted;
        }, offerBudget);
    }

    public ListTag writeToNbt(HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Object2LongMap.Entry<AEKey> entry : contents.object2LongEntrySet()) {
            entries.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getLongValue())));
        }
        return entries;
    }

    public void readFromNbt(HolderLookup.Provider registries, ListTag entries) {
        Object2LongLinkedOpenHashMap<AEKey> restored = new Object2LongLinkedOpenHashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            GenericStack stack = GenericStack.readTag(registries, entries.getCompound(index));
            if (stack == null || stack.amount() <= 0 || stack.what() == null) {
                throw new IllegalArgumentException("Invalid data-production pending entry at index " + index);
            }
            restored.mergeLong(stack.what(), stack.amount(), DataProductionPendingLedger::addExact);
        }
        contents.clear();
        contents.putAll(restored);
        offerQueue.clear();
        restored.keySet().forEach(offerQueue::enqueue);
    }

    /** Emits every pending key using AE's own drop representation, preserving non-item resources. */
    public void addDrops(Level level, BlockPos pos, ObjectList<ItemStack> drops) {
        for (Object2LongMap.Entry<AEKey> entry : contents.object2LongEntrySet()) {
            entry.getKey().addDrops(entry.getLongValue(), drops, level, pos);
        }
    }

    public ObjectList<ItemStack> toDrops(Level level, BlockPos pos) {
        ObjectArrayList<ItemStack> drops = new ObjectArrayList<>();
        addDrops(level, pos, drops);
        return drops;
    }

    public ObjectList<ItemStack> toItemStacks() {
        ObjectArrayList<ItemStack> drops = new ObjectArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : contents.object2LongEntrySet()) {
            if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                continue;
            }
            long remaining = entry.getLongValue();
            while (remaining > 0) {
                int count = (int) Math.min(Integer.MAX_VALUE, remaining);
                ItemStack stack = itemKey.toStack(count);
                if (stack.isEmpty()) {
                    throw new IllegalStateException("AE item key produced an empty pending-output stack");
                }
                drops.add(stack);
                remaining -= stack.getCount();
            }
        }
        return drops;
    }

    public void clear() {
        if (!contents.isEmpty()) {
            contents.clear();
            offerQueue.clear();
            changeListener.run();
        }
    }

    private static void requirePositive(AEKey key, long amount) {
        if (key == null || amount <= 0) {
            throw new IllegalArgumentException("Pending production amounts must be positive");
        }
    }

    private static long addExact(long left, long right) {
        if (left < 0 || right < 0) {
            throw new IllegalArgumentException("Pending production amounts cannot be negative");
        }
        return Math.addExact(left, right);
    }
}
