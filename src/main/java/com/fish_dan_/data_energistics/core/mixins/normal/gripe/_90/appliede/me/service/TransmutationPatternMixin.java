package com.fish_dan_.data_energistics.core.mixins.normal.gripe._90.appliede.me.service;

import appeng.api.crafting.IPatternDetails;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import gripe._90.appliede.me.service.TransmutationPattern;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Arrays;

@Restriction(require = @Condition("appliede"))
@Pseudo
@Mixin(TransmutationPattern.class)
public abstract class TransmutationPatternMixin {

    @ModifyReturnValue(method = "getInputs", at = @At("RETURN"))
    private IPatternDetails.IInput[] dataEnergistics$omitEmptyEmcTiers(IPatternDetails.IInput[] inputs) {
        // Empty low tiers are redundant only when another tier still pays a positive EMC cost.
        // Preserve all-zero and negative costs for the publication boundary to reject.
        boolean hasPositiveTier = false;
        boolean hasEmptyTier = false;
        for (IPatternDetails.IInput input : inputs) {
            long amount = input.getPossibleInputs()[0].amount();
            hasPositiveTier |= amount > 0L;
            hasEmptyTier |= amount == 0L;
        }
        if (!hasPositiveTier || !hasEmptyTier) return inputs;
        return Arrays.stream(inputs).filter(input -> input.getPossibleInputs()[0].amount() != 0L)
                .toArray(IPatternDetails.IInput[]::new);
    }
}
