package com.fish_dan_.data_energistics.core.mixins.normal.gripe._90.appliede.part;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingCapacity;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProvider;
import com.fish_dan_.data_energistics.integration.ae.appliede.transmutation.AppliedETransmutationBatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import gripe._90.appliede.part.EMCModulePart;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Restriction(require = @Condition("appliede"))
@Pseudo
@Mixin(EMCModulePart.class)
@NullMarked
// These method names implement the counted provider contract and cannot carry a Mixin prefix.
@SuppressWarnings("AddedMixinMembersNamePattern")
public abstract class EMCModulePartMixin implements CountedCraftingProvider {

    @Shadow
    @Final
    private Object2LongMap<AEKey> outputs;

    @Override
    public ObjectList<CountedCraftingCapacity> captureCapacityFast(
                                                                   IPatternDetails patternDetails, KeyCounter[] prototype, long requestedCount) {
        return AppliedETransmutationBatching.captureCapacity(((EMCModulePart) (Object) this).getMainNode(), this.outputs, patternDetails, requestedCount);
    }

    @Override
    public @Nullable CountedCraftingAdmission prepareBatch(IPatternDetails patternDetails, KeyCounter[] prototype, long requestedCount) {
        return AppliedETransmutationBatching.prepare(((EMCModulePart) (Object) this).getMainNode(), this.outputs, patternDetails, requestedCount);
    }

    @Override
    public @Nullable CountedCraftingAdmission prepareBatchForTarget(
                                                                    IPatternDetails patternDetails, KeyCounter[] prototype, long requestedCount,
                                                                    CountedCraftingTarget target) {
        return target.providerScoped() ? this.prepareBatch(patternDetails, prototype, requestedCount) : null;
    }
}
