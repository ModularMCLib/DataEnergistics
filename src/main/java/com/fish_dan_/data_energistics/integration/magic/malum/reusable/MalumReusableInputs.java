package com.fish_dan_.data_energistics.integration.magic.malum.reusable;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRuleAdapter;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;

import net.minecraft.resources.ResourceLocation;

import com.sammy.malum.common.recipe.SpiritFocusingRecipe;

import java.util.Optional;

/** Declares deterministic fixed-damage custody for Malum spirit-focusing tools. */
public final class MalumReusableInputs implements ReusableInputRuleAdapter {

    private static final ResourceLocation ID = Data_Energistics.id("malum_spirit_focusing_tool");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean mayMatch(IPatternDetails pattern, Optional<ResourceLocation> recipeId) {
        // Processing patterns carry the concrete recipe ID, while the recipe type is only the
        // pattern-category metadata. The recipe class/type is verified against the server recipe
        // manager in resolve(), where that information is available.
        return recipeId.isPresent();
    }

    @Override
    public Optional<ReusableInputRule> resolve(ReusableInputContext context) {
        if (!mayMatch(context.pattern(), context.recipeId()) ||
                !context.machineMode().filter(mode -> mode.equals(Data_Energistics.id("packaged_reusable"))).isPresent())
            return Optional.empty();
        var holder = context.level().getRecipeManager().byKey(context.recipeId().orElseThrow());
        if (holder.isEmpty() || !(holder.get().value() instanceof SpiritFocusingRecipe recipe) || recipe.durabilityCost <= 0)
            return Optional.empty();
        var key = (AEItemKey) context.actualInput().what();
        var stack = key.toStack();
        if (!stack.isDamageableItem() || !recipe.input.test(stack)) return Optional.empty();
        return Optional.of(ReusableInputRule.fixedDamageFast(ID, 1L, key, recipe.durabilityCost,
                stack.getMaxDamage(), it.unimi.dsi.fastutil.objects.ObjectList.of()));
    }
}
