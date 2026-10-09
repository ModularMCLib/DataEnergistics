package com.fish_dan_.data_energistics.api.registry.digitalsupply;

/** Registration-stage surface for digital-supply adapters owned by one unified plugin. */
public interface DigitalSupplyInterfaceRegistry {

    /** Stages one adapter and its stable resource catalog. */
    void register(DigitalSupplyInterfaceRegistration registration);
}
