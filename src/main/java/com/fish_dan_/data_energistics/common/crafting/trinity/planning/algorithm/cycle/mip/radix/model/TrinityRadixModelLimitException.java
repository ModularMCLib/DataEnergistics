package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityDiagnosticField;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityDiagnosticReason;
import com.fish_dan_.data_energistics.utils.FastUtilCollections;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

/**
 * Signals that a proof-carrying radix model exceeds the configured safe structural envelope.
 */
public final class TrinityRadixModelLimitException extends RuntimeException {

    private final Object2ObjectMap<TrinityDiagnosticField, Object> metadata;

    public TrinityRadixModelLimitException(Object2ObjectMap<TrinityDiagnosticField, Object> metadata) {
        super(String.valueOf(metadata.getOrDefault(TrinityDiagnosticField.REASON, TrinityDiagnosticReason.RADIX_MODEL_LIMIT)));
        this.metadata = FastUtilCollections.immutableMap(metadata);
    }

    /**
     * @return immutable structured limit details for the user-facing diagnostic
     */
    public Object2ObjectMap<TrinityDiagnosticField, Object> metadata() {
        return this.metadata;
    }
}
