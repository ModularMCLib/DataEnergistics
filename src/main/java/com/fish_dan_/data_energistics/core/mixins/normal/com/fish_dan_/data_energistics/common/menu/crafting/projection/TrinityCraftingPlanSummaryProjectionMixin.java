package com.fish_dan_.data_energistics.core.mixins.normal.com.fish_dan_.data_energistics.common.menu.crafting.projection;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityDiagnosedCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.menu.crafting.projection.TrinityCraftingPlanSummaryProjection;
import com.fish_dan_.data_energistics.integration.ae.ae2ct.display.TrinityCraftingTreeProjection;

import appeng.menu.me.crafting.CraftingPlanSummary;

import com.neuvillette.ae2ct.api.ICraftingPlanSummary;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Initializes AE2 Crafting Tree's summary extension when Trinity bypasses AE2's concrete CraftingPlan projection.
 */
@Restriction(require = @Condition("ae2ct"))
@Mixin(value = TrinityCraftingPlanSummaryProjection.class, remap = false)
@NullMarked
public abstract class TrinityCraftingPlanSummaryProjectionMixin {

    @Inject(method = "create", at = @At("RETURN"))
    private static void dataEnergistics$attachCraftingTree(
                                                           TrinityCraftingPlan plan,
                                                           CallbackInfoReturnable<CraftingPlanSummary> cir) {
        ICraftingPlanSummary summary = (ICraftingPlanSummary) cir.getReturnValue();
        summary.setJob(TrinityCraftingTreeProjection.create(plan));
    }

    @Inject(method = "createDiagnostic", at = @At("RETURN"))
    private static void dataEnergistics$attachEmptyDiagnosticTree(
                                                                  TrinityDiagnosedCraftingPlan plan,
                                                                  CallbackInfoReturnable<CraftingPlanSummary> cir) {
        ICraftingPlanSummary summary = (ICraftingPlanSummary) cir.getReturnValue();
        summary.setJob(TrinityCraftingTreeProjection.createDiagnostic(plan.finalOutput()));
    }
}
