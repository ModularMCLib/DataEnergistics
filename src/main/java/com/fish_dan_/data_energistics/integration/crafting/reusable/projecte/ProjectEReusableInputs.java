package com.fish_dan_.data_energistics.integration.crafting.reusable.projecte;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRuleAdapter;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.NativeReusableCrafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;

import net.minecraft.resources.ResourceLocation;

import moze_intel.projecte.gameObjs.items.EvertideAmulet;
import moze_intel.projecte.gameObjs.items.PhilosophersStone;
import moze_intel.projecte.gameObjs.items.VolcaniteAmulet;
import moze_intel.projecte.gameObjs.items.rings.Arcana;
import moze_intel.projecte.gameObjs.items.rings.Zero;

import java.util.Optional;

/** Registers ProjectE items whose crafting remainder is an unchanged copy of the input. */
@DataEnergisticsEntrypoint(requiredMods = "projecte")
public final class ProjectEReusableInputs implements DataEnergisticsPlugin, ReusableInputRuleAdapter {

    private static final ResourceLocation ID = Data_Energistics.id("projecte_reusable_inputs");

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.reusableInputs().register(this);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean mayMatch(IPatternDetails pattern, Optional<ResourceLocation> recipeId) {
        if (!NativeReusableCrafting.usesNativeRecipeValidation(pattern, recipeId)) {
            return false;
        }
        for (var input : pattern.getInputs()) {
            for (var candidate : input.getPossibleInputs()) {
                if (candidate.what() instanceof AEItemKey key && isReusable(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Optional<ReusableInputRule> resolve(ReusableInputContext context) {
        AEItemKey key = (AEItemKey) context.actualInput().what();
        if (!isReusable(key) || !NativeReusableCrafting.usesStandardItemRemainders(
                context.pattern(), context.recipeId(), context.level())) {
            return Optional.empty();
        }
        return Optional.of(ReusableInputRule.unchanged(ID, 1L, key));
    }

    private static boolean isReusable(AEItemKey key) {
        Class<?> itemClass = key.getItem().getClass();
        return itemClass == PhilosophersStone.class || itemClass == EvertideAmulet.class ||
                itemClass == VolcaniteAmulet.class || itemClass == Arcana.class || itemClass == Zero.class;
    }
}
