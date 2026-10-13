package com.fish_dan_.data_energistics.integration.magic.astral.packaged;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.EncodedProcessingPattern;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/**
 * Adds Astral's physical altar inputs and returned containers to an encoded processing pattern.
 */
final class AstralPatternEncoding {

    private AstralPatternEncoding() {}

    static @Nullable ItemStack complete(ServerLevel level, ResourceLocation recipeId, ItemStack encodedPattern) {
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof AltarRecipe recipe)) return null;
        var processing = encodedPattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing == null) return null;
        ObjectList<GenericStack> originalInputs = new ObjectArrayList<>(processing.sparseInputs());
        if (!validFluidInputs(recipe, originalInputs)) return null;
        AstralRecipePlanner.Selection selection = AstralRecipePlanner.defaults(recipe, originalInputs);
        if (selection == null) return null;
        ObjectList<GenericStack> completedInputs = appendMissing(originalInputs, allInputs(selection), 81, true);
        ObjectList<GenericStack> completedOutputs = appendReturns(
                new ObjectArrayList<>(processing.sparseOutputs()), selection.returns(), 27);
        if (completedInputs == null || completedOutputs == null) return null;

        ItemStack copy = encodedPattern.copy();
        copy.set(AEComponents.ENCODED_PROCESSING_PATTERN,
                new EncodedProcessingPattern(completedInputs, completedOutputs));
        return copy;
    }

    static @Nullable ObjectList<GenericStack> completeInputs(ServerLevel level, ResourceLocation recipeId,
                                                             ObjectList<GenericStack> inputs) {
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof AltarRecipe recipe)) return null;
        if (!validFluidInputs(recipe, inputs)) return null;
        AstralRecipePlanner.Selection selection = AstralRecipePlanner.defaults(recipe, inputs);
        if (selection == null) return null;
        ObjectList<GenericStack> completed = appendMissing(inputs, allInputs(selection), 81, true);
        if (completed == null) return null;
        var compact = new ObjectArrayList<GenericStack>();
        for (GenericStack stack : completed) if (stack != null) compact.add(stack);
        return compact;
    }

    /**
     * Fluid inputs are optional declarations; native Astral draw supplies them from chalices or digital interfaces.
     */
    private static boolean validFluidInputs(AltarRecipe recipe, ObjectList<GenericStack> inputs) {
        var required = AstralRecipePlanner.acceptedFluidKeys(recipe);
        for (GenericStack stack : inputs) {
            if (stack == null || !(stack.what() instanceof AEFluidKey key)) continue;
            if (stack.amount() <= 0 || !required.contains(key)) return false;
        }
        return true;
    }

    private static ObjectList<ItemStack> allInputs(AstralRecipePlanner.Selection selection) {
        var result = new ObjectArrayList<ItemStack>();
        for (ItemStack stack : selection.grid()) if (!stack.isEmpty()) result.add(stack);
        for (ItemStack stack : selection.relay()) if (!stack.isEmpty()) result.add(stack);
        result.addAll(selection.additional());
        return result;
    }

    private static @Nullable ObjectList<GenericStack> appendReturns(ObjectList<GenericStack> original,
                                                                    ObjectList<ItemStack> returns, int limit) {
        var returnedAmounts = new Object2LongLinkedOpenHashMap<AEItemKey>();
        for (ItemStack stack : returns) {
            if (stack.isEmpty()) return null;
            returnedAmounts.addTo(AEItemKey.of(stack), stack.getCount());
        }
        var result = new ObjectArrayList<>(original);
        int tail = result.size();
        while (tail > 0 && result.get(tail - 1) == null) tail--;
        for (var entry : returnedAmounts.object2LongEntrySet()) {
            if (tail >= limit) return null;
            GenericStack returned = new GenericStack(entry.getKey(), entry.getLongValue());
            if (tail < result.size()) result.set(tail, returned);
            else result.add(returned);
            tail++;
        }
        return result;
    }

    private static @Nullable ObjectList<GenericStack> appendMissing(ObjectList<GenericStack> original,
                                                                    ObjectList<ItemStack> required, int limit,
                                                                    boolean allowFluidInputs) {
        var missing = new Object2LongLinkedOpenHashMap<AEItemKey>();
        for (ItemStack stack : required) {
            if (stack.isEmpty()) return null;
            missing.addTo(AEItemKey.of(stack), stack.getCount());
        }
        var result = new ObjectArrayList<>(original);
        for (int index = 0; index < original.size(); index++) {
            GenericStack stack = original.get(index);
            if (stack == null) continue;
            if (allowFluidInputs && stack.what() instanceof AEFluidKey) continue;
            if (stack.amount() <= 0 || !(stack.what() instanceof AEItemKey key))
                return null;
            long available = missing.getLong(key);
            if (!missing.containsKey(key)) return null;
            if (available <= 0) {
                if (!allowFluidInputs) return null;
                result.set(index, null);
                continue;
            }
            if (stack.amount() > available) {
                if (!allowFluidInputs) return null;
                stack = new GenericStack(key, available);
                result.set(index, stack);
            }
            missing.addTo(key, -stack.amount());
        }
        int tail = result.size();
        while (tail > 0 && result.get(tail - 1) == null) tail--;
        for (var entry : missing.object2LongEntrySet()) {
            if (entry.getLongValue() == 0) continue;
            if (entry.getLongValue() < 0 || tail >= limit) return null;
            GenericStack stack = new GenericStack(entry.getKey(), entry.getLongValue());
            if (tail < result.size()) result.set(tail, stack);
            else result.add(stack);
            tail++;
        }
        return result;
    }
}
