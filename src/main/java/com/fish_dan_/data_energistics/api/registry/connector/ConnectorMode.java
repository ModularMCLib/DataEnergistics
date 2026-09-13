package com.fish_dan_.data_energistics.api.registry.connector;

/** Selects the transfer directions of an individual provider or interface link. */
public enum ConnectorMode {

    INPUT,
    PULL,
    BOTH;

    /** Whether a link sends pattern inputs or interface stock to its target. */
    public boolean supportsInput() {
        return this == INPUT || this == BOTH;
    }

    /** Whether a link extracts target outputs into its host's return inventory. */
    public boolean supportsPull() {
        return this == PULL || this == BOTH;
    }
}
