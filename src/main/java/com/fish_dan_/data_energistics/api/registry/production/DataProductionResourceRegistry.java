package com.fish_dan_.data_energistics.api.registry.production;

/** Registration-stage facet for AE keys used by data-production rules. */
@FunctionalInterface
public interface DataProductionResourceRegistry {

    /** Registers one stable resource identifier and its AE key identity. */
    void register(DataProductionResourceRegistration registration);
}
