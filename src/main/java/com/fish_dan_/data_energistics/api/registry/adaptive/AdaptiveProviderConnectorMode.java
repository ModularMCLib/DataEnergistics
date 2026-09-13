package com.fish_dan_.data_energistics.api.registry.adaptive;

/** Selects whether a connector sends pattern inputs or pulls accepted outputs. */
public enum AdaptiveProviderConnectorMode {

    INPUT,
    PULL,
    BOTH;

    /** Whether a link accepts pattern inputs from the provider. */
    public boolean supportsInput() {
        return this == INPUT || this == BOTH;
    }

    /** Whether a link returns inventory output to the provider. */
    public boolean supportsPull() {
        return this == PULL || this == BOTH;
    }
}
