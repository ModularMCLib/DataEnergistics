package com.fish_dan_.data_energistics.common.crafting.packaged.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.EncodedProcessingPattern;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

/** Completes machine-specific, countable recipe inputs before the terminal consumes a blank pattern. */
public final class PackagedPatternEncoding {

    private static final int MAX_OUTPUTS = 27;

    private PackagedPatternEncoding() {}

    public static @Nullable ItemStack complete(ServerLevel level, @Nullable ResourceLocation type,
                                               @Nullable ResourceLocation recipeId, ItemStack encodedPattern) {
        if (type == null) return encodedPattern;
        var adapters = DataEnergisticsEntrypointLoader.snapshot().packagedCrafting().forType(type);
        if (adapters.isEmpty()) return encodedPattern;
        if (recipeId == null) {
            Data_Energistics.LOGGER.warn("Rejected packaged pattern encoding: recipe identity is missing for category {}", type);
            return null;
        }
        ItemStack completed = encodedPattern;
        for (var adapter : adapters) {
            completed = adapter.completeEncoding(level, recipeId, completed);
            if (completed == null) {
                Data_Energistics.LOGGER.warn("Rejected packaged pattern encoding: adapter {} rejected recipe {} in category {}",
                        adapter.id(), recipeId, type);
                return null;
            }
        }
        return completed;
    }

    /** Reads the exact supplied keys selected in the encoder. */
    public static KeyCounter[] suppliedInputs(EncodedProcessingPattern processing) {
        var supplied = new KeyCounter();
        for (var input : processing.sparseInputs()) {
            if (input != null) supplied.add(input.what(), input.amount());
        }
        return new KeyCounter[] { supplied };
    }

    /** Adds only native returns still absent from the sparse output slots. */
    public static @Nullable ItemStack withReturns(ItemStack pattern, EncodedProcessingPattern processing,
                                                  long productAmount, KeyCounter returns) {
        var outputs = new ObjectArrayList<@Nullable GenericStack>(processing.sparseOutputs());
        int first = 0;
        while (first < outputs.size() && outputs.get(first) == null) first++;
        if (first == outputs.size() || productAmount <= 0) return null;
        var missing = new KeyCounter();
        missing.addAll(returns);
        for (int slot = first; slot < outputs.size(); slot++) {
            var declared = outputs.get(slot);
            if (declared == null) continue;
            long alreadyDeclared = slot == first ? Math.max(0, declared.amount() - productAmount) : declared.amount();
            missing.remove(declared.what(), Math.min(alreadyDeclared, missing.get(declared.what())));
        }
        missing.removeZeros();
        if (missing.isEmpty()) return pattern;
        int slot = first + 1;
        for (var entry : missing) {
            while (slot < outputs.size() && outputs.get(slot) != null) slot++;
            if (slot >= MAX_OUTPUTS) return null;
            var added = new GenericStack(entry.getKey(), entry.getLongValue());
            if (slot < outputs.size()) outputs.set(slot, added);
            else outputs.add(added);
        }
        var copy = pattern.copy();
        copy.set(AEComponents.ENCODED_PROCESSING_PATTERN, new EncodedProcessingPattern(processing.sparseInputs(), outputs));
        return copy;
    }

    public static KeyCounter returnedItems(Iterable<ItemStack> returns, long repetitions) {
        var amounts = new KeyCounter();
        for (var stack : returns) {
            if (stack.isEmpty()) continue;
            var key = AEItemKey.of(stack);
            amounts.set(key, Math.addExact(amounts.get(key), Math.multiplyExact(stack.getCount(), repetitions)));
        }
        return amounts;
    }
}
