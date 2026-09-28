package com.fish_dan_.data_energistics.mixin;

import com.bawnorton.mixinsquared.api.MixinCanceller;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Shared MixinSquared canceller for third-party mixins that conflict with Data Energistics.
 *
 * <p>
 * The caller supplies exact binary mixin class names. Keeping the cancellation policy separate from the
 * individual integration makes the same registrar usable when another compatibility conflict is discovered.
 * </p>
 */
public final class DataEnergisticsMixinCanceller implements MixinCanceller {

    private final Set<String> cancelledMixinClassNames;

    public DataEnergisticsMixinCanceller(Collection<String> cancelledMixinClassNames) {
        this.cancelledMixinClassNames = Set.copyOf(cancelledMixinClassNames);
    }

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        return this.cancelledMixinClassNames.contains(mixinClassName);
    }
}
