package com.fish_dan_.data_energistics.common.crafting.packaged.execution;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

import java.math.BigInteger;

/** Exact owned amounts; conversion to machine-sized integers happens only when transferring a bounded batch. */
final class PackagedAmounts {

    private PackagedAmounts() {}

    static Object2ObjectMap<AEKey, BigInteger> capture(KeyCounter[] inputs) {
        var amounts = new Object2ObjectLinkedOpenHashMap<AEKey, BigInteger>();
        for (var input : inputs) {
            for (var entry : input) {
                if (entry.getLongValue() <= 0) throw new IllegalArgumentException("Packaged inputs must be positive");
                amounts.merge(entry.getKey(), BigInteger.valueOf(entry.getLongValue()), BigInteger::add);
            }
        }
        return amounts;
    }

    static ListTag save(Object2ObjectMap<AEKey, BigInteger> amounts, HolderLookup.Provider registries) {
        var result = new ListTag();
        amounts.forEach((key, amount) -> {
            var tag = new CompoundTag();
            tag.put("key", key.toTagGeneric(registries));
            tag.putString("amount", amount.toString());
            result.add(tag);
        });
        return result;
    }

    static Object2ObjectMap<AEKey, BigInteger> load(ListTag list, HolderLookup.Provider registries) {
        var result = new Object2ObjectLinkedOpenHashMap<AEKey, BigInteger>();
        for (int index = 0; index < list.size(); index++) {
            var tag = list.getCompound(index);
            var key = AEKey.fromTagGeneric(registries, tag.getCompound("key"));
            var amount = new BigInteger(tag.getString("amount"));
            if (key == null || amount.signum() <= 0 || result.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("Invalid persisted packaged amount");
            }
        }
        return result;
    }
}
