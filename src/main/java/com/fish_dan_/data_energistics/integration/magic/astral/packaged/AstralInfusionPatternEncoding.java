package com.fish_dan_.data_energistics.integration.magic.astral.packaged;

import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.EncodedProcessingPattern;

import net.minecraft.world.item.ItemStack;

import hellfirepvp.astralsorcery.common.recipe.infusion.InfusionRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** Keeps the native infusion liquid outside the pattern's counted inputs. */
final class AstralInfusionPatternEncoding {

    private AstralInfusionPatternEncoding() {}

    static @Nullable ItemStack complete(InfusionRecipe recipe, ItemStack pattern) {
        var encoded = pattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null) return null;
        var inputs = new ObjectArrayList<>(encoded.sparseInputs());
        for (int slot = 0; slot < inputs.size(); slot++) {
            var input = inputs.get(slot);
            if (input != null && input.what() instanceof AEFluidKey) inputs.set(slot, null);
        }
        if (itemInput(recipe, inputs) == null) return null;
        ItemStack copy = pattern.copy();
        copy.set(AEComponents.ENCODED_PROCESSING_PATTERN,
                new EncodedProcessingPattern(inputs, encoded.sparseOutputs()));
        return copy;
    }

    static @Nullable ObjectList<GenericStack> completeInputs(InfusionRecipe recipe, ObjectList<GenericStack> inputs) {
        var items = new ObjectArrayList<GenericStack>();
        for (GenericStack input : inputs) {
            if (input != null && !(input.what() instanceof AEFluidKey)) items.add(input);
        }
        return itemInput(recipe, items) == null ? null : items;
    }

    static boolean matches(InfusionRecipe recipe, IPatternDetails pattern) {
        var encoded = pattern.getDefinition().get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null || itemInput(recipe, encoded.sparseInputs()) == null) return false;
        ItemStack output = recipe.getOutput();
        return !output.isEmpty() && PackagedOutputMatching.matches(pattern, output, output.getCount());
    }

    private static @Nullable AEItemKey itemInput(InfusionRecipe recipe, Iterable<GenericStack> inputs) {
        AEItemKey selected = null;
        for (GenericStack input : inputs) {
            if (input == null) continue;
            if (selected != null || input.amount() != 1 || !(input.what() instanceof AEItemKey key) ||
                    !recipe.getItemInput().test(key.toStack()))
                return null;
            selected = key;
        }
        return selected;
    }
}
