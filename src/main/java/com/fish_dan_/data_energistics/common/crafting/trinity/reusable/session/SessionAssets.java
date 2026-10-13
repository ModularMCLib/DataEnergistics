package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Exact positive asset arithmetic shared by escrow, output and persistence boundaries. */
final class SessionAssets {

    private SessionAssets() {}

    static ObjectList<GenericStack> checked(ObjectList<GenericStack> assets) {
        ObjectList<GenericStack> result = new ObjectImmutableList<>(assets);
        for (GenericStack asset : result) {
            if (asset.amount() <= 0) {
                throw new IllegalArgumentException("Session asset amount must be positive");
            }
        }
        return result;
    }

    static Object2LongLinkedOpenHashMap<AEKey> counts(ObjectList<GenericStack> assets) {
        Object2LongLinkedOpenHashMap<AEKey> result = new Object2LongLinkedOpenHashMap<>();
        for (GenericStack asset : assets) {
            result.put(asset.what(), Math.addExact(result.getLong(asset.what()), asset.amount()));
        }
        return result;
    }

    static ObjectList<GenericStack> merge(ObjectList<GenericStack> first, ObjectList<GenericStack> second) {
        ObjectArrayList<GenericStack> combined = new ObjectArrayList<>(first);
        combined.addAll(second);
        return list(counts(combined));
    }

    static ObjectList<GenericStack> subtract(ObjectList<GenericStack> assets, ObjectList<GenericStack> consumed) {
        Object2LongLinkedOpenHashMap<AEKey> result = counts(assets);
        counts(consumed).forEach((key, amount) -> {
            long remaining = Math.subtractExact(result.getLong(key), amount);
            if (remaining < 0) {
                throw new IllegalArgumentException("Session does not own the requested asset");
            }
            if (remaining == 0) {
                result.removeLong(key);
            } else {
                result.put(key, remaining);
            }
        });
        return list(result);
    }

    static ObjectList<GenericStack> multiply(ObjectList<GenericStack> assets, long count) {
        if (count < 0) {
            throw new IllegalArgumentException("Negative asset multiplier");
        }
        if (count == 0) {
            return ObjectList.of();
        }
        ObjectArrayList<GenericStack> result = new ObjectArrayList<>();
        for (GenericStack asset : assets) {
            result.add(new GenericStack(asset.what(), Math.multiplyExact(asset.amount(), count)));
        }
        return list(counts(result));
    }

    private static ObjectList<GenericStack> list(Object2LongLinkedOpenHashMap<AEKey> values) {
        ObjectArrayList<GenericStack> result = new ObjectArrayList<>(values.size());
        values.forEach((key, amount) -> result.add(new GenericStack(key, amount)));
        return new ObjectImmutableList<>(result);
    }
}
