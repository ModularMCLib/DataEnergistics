package com.fish_dan_.data_energistics.integration.crafting.packaged.arsnouveau;

import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** Bounds candidate construction by the physical pedestal count, regardless of the envelope's batch size. */
final class ArsInputAssignment {

    private ArsInputAssignment() {}

    static @Nullable ObjectList<ItemStack> units(KeyCounter[] inputs, int cycleSize) {
        var totals = new Object2LongLinkedOpenHashMap<AEItemKey>();
        long total = 0;
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey item) || entry.getLongValue() <= 0 ||
                        entry.getLongValue() > Long.MAX_VALUE - total)
                    return null;
                total += entry.getLongValue();
                totals.addTo(item, entry.getLongValue());
            }
        }
        if (total == 0 || total % cycleSize != 0) return null;
        long cycles = total / cycleSize;
        var result = new ObjectArrayList<ItemStack>();
        for (var entry : totals.object2LongEntrySet()) {
            if (entry.getLongValue() % cycles != 0) return null;
            long count = entry.getLongValue() / cycles;
            for (int index = 0; index < count; index++) result.add(entry.getKey().toStack());
        }
        return result;
    }

    static long cycles(KeyCounter[] inputs, int cycleSize) {
        long count = 0;
        for (var counter : inputs) for (var entry : counter) count = Math.addExact(count, entry.getLongValue());
        return count / cycleSize;
    }

    static boolean outputsMatch(IPatternDetails pattern, ItemStack result, ObjectList<ItemStack> remaining, long cycles) {
        var expected = new Object2LongOpenHashMap<AEItemKey>();
        var actual = new ObjectArrayList<>(remaining);
        actual.add(result);
        for (ItemStack stack : actual) {
            if (stack.isEmpty()) continue;
            if (cycles > Long.MAX_VALUE / stack.getCount()) return false;
            AEItemKey key = AEItemKey.of(stack);
            long amount = cycles * stack.getCount();
            if (amount > Long.MAX_VALUE - expected.getLong(key)) return false;
            expected.addTo(key, amount);
        }
        return PackagedOutputMatching.matches(pattern, expected);
    }
}
