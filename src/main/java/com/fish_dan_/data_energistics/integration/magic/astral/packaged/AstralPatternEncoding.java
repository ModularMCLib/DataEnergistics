package com.fish_dan_.data_energistics.integration.magic.astral.packaged;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.EncodedProcessingPattern;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

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
        ObjectList<GenericStack> physicalInputs = removeAdditionalFluids(recipe, originalInputs);
        if (physicalInputs == null) return null;
        AstralRecipePlanner.Selection selection = AstralRecipePlanner.defaults(recipe, physicalInputs);
        if (selection == null) return null;
        ObjectList<GenericStack> completedInputs = appendMissing(physicalInputs, allInputs(selection), 81);
        ObjectList<GenericStack> completedOutputs = appendMissing(
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
        ObjectList<GenericStack> physicalInputs = removeAdditionalFluids(recipe, inputs);
        if (physicalInputs == null) return null;
        AstralRecipePlanner.Selection selection = AstralRecipePlanner.defaults(recipe, physicalInputs);
        if (selection == null) return null;
        return appendMissing(physicalInputs, allInputs(selection), 81);
    }

    /**
     * Extra altar fluids are native Astral resources rather than AE item inputs. The native draw transaction then
     * prefers unpowered chalices and falls back to linked digital supply interfaces. Fluid entries are retained only
     * as an encoding-time declaration so an unrelated fluid cannot silently disappear from a pattern.
     */
    private static @Nullable ObjectList<GenericStack> removeAdditionalFluids(AltarRecipe recipe,
                                                                             ObjectList<GenericStack> original) {
        var required = new Object2LongLinkedOpenHashMap<AEFluidKey>();
        for (FluidStack fluid : new ObjectArrayList<>(recipe.getRequiredFluid())) {
            if (fluid.isEmpty()) continue;
            AEFluidKey key = AEFluidKey.of(fluid);
            if (key == null) return null;
            required.addTo(key, fluid.getAmount());
        }

        var declared = new Object2LongLinkedOpenHashMap<AEFluidKey>();
        var result = new ObjectArrayList<GenericStack>(original);
        for (int index = 0; index < result.size(); index++) {
            GenericStack stack = result.get(index);
            if (stack == null || !(stack.what() instanceof AEFluidKey key)) continue;
            if (stack.amount() <= 0 || !required.containsKey(key)) return null;
            declared.addTo(key, stack.amount());
            result.set(index, null);
        }
        for (var entry : declared.object2LongEntrySet()) {
            if (entry.getLongValue() != required.getLong(entry.getKey())) return null;
        }
        return result;
    }

    private static ObjectList<ItemStack> allInputs(AstralRecipePlanner.Selection selection) {
        var result = new ObjectArrayList<ItemStack>();
        for (ItemStack stack : selection.grid()) if (!stack.isEmpty()) result.add(stack);
        for (ItemStack stack : selection.relay()) if (!stack.isEmpty()) result.add(stack);
        result.addAll(selection.additional());
        return result;
    }

    private static @Nullable ObjectList<GenericStack> appendMissing(ObjectList<GenericStack> original,
                                                                    ObjectList<ItemStack> required, int limit) {
        var missing = new Object2LongLinkedOpenHashMap<AEItemKey>();
        for (ItemStack stack : required) {
            if (stack.isEmpty()) return null;
            missing.addTo(AEItemKey.of(stack), stack.getCount());
        }
        for (GenericStack stack : original) {
            if (stack == null) continue;
            if (stack.amount() <= 0 || !(stack.what() instanceof AEItemKey key) || missing.getLong(key) < stack.amount())
                return null;
            missing.addTo(key, -stack.amount());
        }
        var result = new ObjectArrayList<>(original);
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
