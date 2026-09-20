package com.fish_dan_.data_energistics.integration.magic.arsnouveau.reusable;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRuleAdapter;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Optional;

/** Declares unchanged pedestal custody only for an exact native imbuement on a concrete packaged route. */
public final class ArsImbuementReusableInputs implements ReusableInputRuleAdapter {

    private static final ResourceLocation ID = Data_Energistics.id("ars_imbuement_pedestal");
    private static final ResourceLocation MODE = Data_Energistics.id("packaged_reusable");
    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "imbuement");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean mayMatch(IPatternDetails pattern, Optional<ResourceLocation> recipeId) {
        return recipeId.isPresent() && pattern.getOutputs().size() == 1 &&
                TYPE.equals(pattern.getDefinition().get(DEDataComponents.PROCESSING_PATTERN_RECIPE_TYPE.get()));
    }

    @Override
    public Optional<ReusableInputRule> resolve(ReusableInputContext context) {
        if (!context.machineMode().filter(MODE::equals).isPresent() || context.target().providerScoped() ||
                !mayMatch(context.pattern(), context.recipeId()))
            return Optional.empty();
        var holder = context.level().getRecipeManager().byKey(context.recipeId().orElseThrow());
        if (holder.isEmpty() || !(holder.get().value() instanceof ImbuementRecipe recipe)) return Optional.empty();
        var key = (AEItemKey) context.actualInput().what();
        // A key accepted centrally cannot safely be classified as a tool when AE condenses equal inputs.
        if (recipe.getInput().test(key.toStack())) return Optional.empty();
        var supplied = new KeyCounter();
        for (var input : context.exactInputsFast()) supplied.add(input.what(), input.amount());
        var roles = new ObjectArrayList<Ingredient>();
        roles.add(recipe.getInput());
        roles.addAll(recipe.getPedestalItems());
        var assigned = PackagedIngredientAssignment.match(roles, new KeyCounter[] { supplied });
        if (assigned == null) return Optional.empty();
        for (int index = 0; index < roles.size(); index++) {
            if (!roles.get(index).test(assigned.get(index))) return Optional.empty();
        }
        long retained = 0;
        for (int index = 1; index < assigned.size(); index++) {
            if (key.equals(AEItemKey.of(assigned.get(index)))) retained += assigned.get(index).getCount();
        }
        if (retained != supplied.get(key)) return Optional.empty();
        return Optional.of(ReusableInputRule.unchanged(ID, 1L, key));
    }
}
