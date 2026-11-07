package com.fish_dan_.data_energistics.core.mixins.io.github.lounode.ae2cs.common.block.entity;

import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;
import com.fish_dan_.data_energistics.integration.ae.ae2cs.production.CrystalScienceOutputBatch;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.externalstorage.GenericStackInv;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.lounode.ae2cs.common.block.entity.EntropyVariationReactionChamberBlockEntity;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Restriction(require = @Condition("ae2cs"))
@Pseudo
@Mixin(EntropyVariationReactionChamberBlockEntity.class)
public abstract class EntropyVariationReactionChamberBlockEntityMixin {

    @Shadow
    public abstract IUpgradeInventory getUpgrades();

    @Shadow
    public abstract GenericStackInv getOutputInv();

    @Final
    @Shadow
    private IActionSource actionSource;

    @ModifyExpressionValue(
                           method = "serverTick",
                           at = @At(
                                    value = "INVOKE",
                                    target = "Lio/github/lounode/ae2cs/common/block/entity/EntropyVariationReactionChamberBlockEntity;getRecipeOutput(Lappeng/recipes/entropy/EntropyRecipe;)Ljava/util/List;"))
    private List<GenericStack> dataEnergistics$multiplyGeneratedOutputs(List<GenericStack> outputs) {
        if (!MultiplicationCardOutput.isInstalled(this.getUpgrades())) {
            return outputs;
        }
        List<GenericStack> boosted = new ArrayList<>(outputs.size());
        for (GenericStack output : outputs) {
            boosted.add(new GenericStack(output.what(), MultiplicationCardOutput.boostExternalAmount(output.amount(), this.getUpgrades())));
        }
        return boosted;
    }

    @Inject(
            method = "serverTick",
            at = @At(
                     value = "INVOKE",
                     target = "Lio/github/lounode/ae2cs/common/block/entity/EntropyVariationReactionChamberBlockEntity;consumeInputs(Lappeng/recipes/entropy/EntropyRecipe;)Z"),
            cancellable = true)
    private void dataEnergistics$checkWholeOutputBatch(CallbackInfo ci, @Local(ordinal = 0) List<GenericStack> outputs) {
        if (MultiplicationCardOutput.isInstalled(this.getUpgrades()) && !CrystalScienceOutputBatch.canInsertAll(this.getOutputInv(), outputs, this.actionSource)) {
            ci.cancel();
        }
    }
}
