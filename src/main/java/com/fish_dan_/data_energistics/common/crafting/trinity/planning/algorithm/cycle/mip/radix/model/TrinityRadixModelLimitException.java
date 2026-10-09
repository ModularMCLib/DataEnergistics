package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model;

import com.fish_dan_.data_energistics.util.FastUtilCollections;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

/**
 * Signals that a proof-carrying radix model exceeds the configured safe structural envelope.
 */
public final class TrinityRadixModelLimitException extends RuntimeException {

    private final Object2ObjectMap<String, String> metadata;

    public TrinityRadixModelLimitException(Object2ObjectMap<String, String> metadata) {
        super(metadata.getOrDefault("reason", "radix_model_limit"));
        this.metadata = FastUtilCollections.immutableMap(metadata);
    }

    /**
     * @return immutable structured limit details for the user-facing diagnostic
     */
    public Object2ObjectMap<String, String> metadata() {
        return this.metadata;
    }
}
