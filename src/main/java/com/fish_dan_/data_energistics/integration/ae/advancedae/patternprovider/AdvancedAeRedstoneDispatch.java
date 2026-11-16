package com.fish_dan_.data_energistics.integration.ae.advancedae.patternprovider;

import com.fish_dan_.data_energistics.api.registry.provider.dispatch.PatternProviderDispatchHooks;
import com.fish_dan_.data_energistics.integration.ae.ae2.patternprovider.redstone.NativeRedstoneTuningDispatch;

import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Advanced AE registration callback; private-field access stays in the native Mixin bridge. */
@NullMarked
public final class AdvancedAeRedstoneDispatch {

    private AdvancedAeRedstoneDispatch() {}

    public static @Nullable PatternProviderDispatchHooks create(AdvPatternProviderLogic logic) {
        var access = (AdvancedAeDispatchAccess) logic;
        var host = access.dataEnergistics$dispatchHost();
        return NativeRedstoneTuningDispatch.create(host, access::dataEnergistics$sendingInputs,
                host::getBlockEntity, host::getGrid, access::dataEnergistics$dispatchActionSource,
                logic::getAvailablePatterns, false);
    }
}
