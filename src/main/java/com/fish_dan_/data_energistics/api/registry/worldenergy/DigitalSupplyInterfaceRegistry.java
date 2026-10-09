package com.fish_dan_.data_energistics.api.registry.worldenergy;

/** Registration-stage surface for world-energy adapters owned by one unified plugin. */
public interface DigitalSupplyInterfaceRegistry {

    /** Stages one adapter and its stable resource catalog. */
    void register(DigitalSupplyInterfaceRegistration registration);
}
