package com.fish_dan_.data_energistics.integration.technology.extendedcrafting.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.matching.RecipeMatchingRuleAdapter;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRuleAdapter;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;
import com.fish_dan_.data_energistics.common.crafting.packaged.reusable.PackagedReusableState;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Ingredient;

import com.blakebr0.extendedcrafting.api.crafting.ICompressorRecipe;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Declares the native catalyst role for pattern matching and reusable custody on a packaged compressor. */
@NullMarked
public final class QuantumCompressionRecipeRoles implements RecipeMatchingRuleAdapter, ReusableInputRuleAdapter {

    private static final ResourceLocation ID = Data_Energistics.id("extended_crafting_quantum_compression_catalyst");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public @Nullable ObjectList<Ingredient> inputIngredients(ServerLevel level, ResourceLocation recipeId) {
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof ICompressorRecipe recipe)) return null;
        return ObjectList.of(recipe.getIngredients().getFirst(), recipe.getCatalyst());
    }

    @Override
    public boolean mayMatch(IPatternDetails pattern, Optional<ResourceLocation> recipeId) {
        return recipeId.isPresent() && pattern.getOutputs().size() == 1 &&
                QuantumCompressionAdapter.RECIPE_TYPE.equals(pattern.getDefinition().get(DEDataComponents.PROCESSING_PATTERN_RECIPE_TYPE.get()));
    }

    @Override
    public Optional<ReusableInputRule> resolve(ReusableInputContext context) {
        if (context.actualInput().amount() != 1 || context.target().providerScoped() ||
                context.machineMode().filter(PackagedReusableState.MODE::equals).isEmpty() ||
                !mayMatch(context.pattern(), context.recipeId()))
            return Optional.empty();
        var recipeId = context.recipeId().orElseThrow();
        var level = context.level();
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof ICompressorRecipe recipe)) return Optional.empty();

        var supplied = new KeyCounter();
        for (var input : context.exactInputs()) supplied.add(input.what(), input.amount());
        var inputs = QuantumCompressionInputs.match(level, recipe, new KeyCounter[] { supplied });
        if (inputs == null || !context.actualInput().what().equals(inputs.catalyst()) ||
                !inputs.hasSeparateCatalyst(level, recipe))
            return Optional.empty();
        var nativeInput = inputs.craftingInput();
        var selected = level.getRecipeManager().getRecipeFor(ModRecipeTypes.COMPRESSOR.get(), nativeInput, level);
        if (selected.isEmpty() || !selected.get().id().equals(recipeId)) return Optional.empty();

        // A legacy pattern may declare the return as an output, including when it merges with the result key.
        var result = recipe.assemble(nativeInput, level.registryAccess());
        if (!PackagedOutputMatching.matches(context.pattern(), result, result.getCount())) return Optional.empty();
        return Optional.of(ReusableInputRule.unchanged(ID, 1L, inputs.catalyst()));
    }
}
