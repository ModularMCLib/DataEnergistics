package com.fish_dan_.data_energistics.api.registry.digitalsupply;

/** Registration-stage surface for AE2 key types discovered by a common Data Energistics plugin. */
public interface AeKeyTypeRegistry {

    /** Stages one stable key type for the AE2 registry event. */
    void register(AeKeyTypeRegistration registration);
}
