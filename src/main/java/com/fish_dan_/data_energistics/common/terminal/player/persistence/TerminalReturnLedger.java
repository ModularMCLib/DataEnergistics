package com.fish_dan_.data_energistics.common.terminal.player.persistence;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.longs.Long2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

/**
 * Resources remain owned by their source frequency until delivery or reinsertion succeeds, on the server thread.
 */
public final class TerminalReturnLedger {

    private final Long2ObjectMap<Object2LongMap<AEKey>> resources = new Long2ObjectLinkedOpenHashMap<>();
    private final Long2DoubleMap energy = new Long2DoubleLinkedOpenHashMap();
    private final Runnable changed;

    public TerminalReturnLedger(Runnable changed) {
        this.changed = changed;
    }

    public LongSet frequencies() {
        LongSet result = new LongLinkedOpenHashSet(resources.keySet());
        result.addAll(energy.keySet());
        return result;
    }

    public void extracted(long frequency, AEKey key, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Return amount must be positive");
        resources.computeIfAbsent(frequency, unused -> new Object2LongLinkedOpenHashMap<>()).mergeLong(key, amount, Math::addExact);
        changed.run();
    }

    public void delivered(long frequency, AEKey key, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Delivered amount must not be negative");
        if (amount == 0) return;
        if (!resources.containsKey(frequency))
            throw new IllegalStateException("Terminal delivery has no journaled frequency");
        Object2LongMap<AEKey> pending = resources.get(frequency);
        if (pending.getLong(key) < amount)
            throw new IllegalStateException("Delivery exceeds journaled terminal resources");
        long remainder = pending.getLong(key) - amount;
        if (remainder == 0) pending.removeLong(key);
        else pending.put(key, remainder);
        if (pending.isEmpty()) resources.remove(frequency);
        changed.run();
    }

    public void energy(long frequency, double amount) {
        if (!Double.isFinite(amount) || amount <= 0)
            throw new IllegalArgumentException("Energy return must be positive");
        double total = energy.get(frequency) + amount;
        if (!Double.isFinite(total)) throw new IllegalArgumentException("Energy return overflow");
        energy.put(frequency, total);
        changed.run();
    }

    public void refund(TerminalConnection connection) {
        long frequency = connection.frequency();
        if (resources.containsKey(frequency)) {
            Object2LongMap<AEKey> pending = resources.get(frequency);
            var iterator = pending.object2LongEntrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                long accepted = connection.storage().insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, connection.source());
                TerminalNetworkStorage.checkAmount(accepted, entry.getLongValue());
                if (accepted == 0) continue;
                if (accepted == entry.getLongValue()) iterator.remove();
                else entry.setValue(entry.getLongValue() - accepted);
                changed.run();
            }
            if (pending.isEmpty()) resources.remove(frequency);
        }
        double amount = energy.get(frequency);
        if (amount > 0) {
            double remainder = connection.grid().getEnergyService().injectPower(amount, Actionable.MODULATE);
            if (!Double.isFinite(remainder) || remainder < 0 || remainder > amount)
                throw new IllegalStateException("Invalid terminal energy return");
            if (remainder == 0) energy.remove(frequency);
            else energy.put(frequency, remainder);
            if (remainder != amount) changed.run();
        }
    }

    public ListTag save(HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        for (long frequency : frequencies()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("frequency", frequency);
            entry.putDouble("energy", energy.get(frequency));
            ListTag stacks = new ListTag();
            if (resources.containsKey(frequency)) {
                Object2LongMap<AEKey> pending = resources.get(frequency);
                for (var resource : pending.object2LongEntrySet())
                    stacks.add(GenericStack.writeTag(registries, new GenericStack(resource.getKey(), resource.getLongValue())));
            }
            entry.put("resources", stacks);
            result.add(entry);
        }
        return result;
    }

    public void load(ListTag tags, HolderLookup.Provider registries) {
        resources.clear();
        energy.clear();
        for (Tag value : tags) {
            CompoundTag entry = (CompoundTag) value;
            long frequency = entry.getLong("frequency");
            if (resources.containsKey(frequency) || energy.containsKey(frequency))
                throw new IllegalArgumentException("Duplicate terminal return frequency");
            double power = entry.getDouble("energy");
            if (!Double.isFinite(power) || power < 0)
                throw new IllegalArgumentException("Invalid saved terminal energy return");
            if (power > 0) energy.put(frequency, power);
            Object2LongMap<AEKey> pending = new Object2LongLinkedOpenHashMap<>();
            for (Tag resource : entry.getList("resources", Tag.TAG_COMPOUND)) {
                GenericStack stack = GenericStack.readTag(registries, (CompoundTag) resource);
                if (stack == null || stack.amount() <= 0)
                    throw new IllegalArgumentException("Invalid saved terminal resource return");
                pending.mergeLong(stack.what(), stack.amount(), Math::addExact);
            }
            if (!pending.isEmpty()) resources.put(frequency, pending);
        }
    }
}
