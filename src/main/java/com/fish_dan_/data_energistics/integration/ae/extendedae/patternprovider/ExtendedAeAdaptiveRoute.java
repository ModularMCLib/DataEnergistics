package com.fish_dan_.data_energistics.integration.ae.extendedae.patternprovider;

import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatch;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchContext;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchTarget;

import appeng.api.crafting.IPatternDetails;

import org.jspecify.annotations.NonNull;

/**
 * Keeps an adaptive provider installed with an ExtendedAE provider on AE2's native dispatch hook.
 *
 * <p>
 * ExtendedAE patterns do not expose a capacity contract that Data Energistics can safely simulate.
 * The adaptive provider therefore publishes one unknown-capacity craft and lets its native
 * {@code pushPattern} implementation decide whether the request is accepted.
 * </p>
 */
public final class ExtendedAeAdaptiveRoute implements AdaptivePatternProviderDispatch {

    @Override
    public boolean usesSpecialBatchRoute(@NonNull IPatternDetails patternDetails) {
        return true;
    }

    @Override
    public boolean validatesMachineCapacity() {
        return true;
    }

    @Override
    public boolean handles(@NonNull AdaptivePatternProviderDispatchContext context) {
        return true;
    }

    @Override
    public boolean dispatch(AdaptivePatternProviderDispatchContext context) {
        AdaptivePatternProviderDispatchTarget target = context.target();
        return target.pushDefault(context.patternDetails(), context.inputHolder());
    }
}
