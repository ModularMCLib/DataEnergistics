package com.fish_dan_.data_energistics.integration.ae.ae2.patternprovider;

public enum RedstoneTuningMode {

    EMIT_ON_DISPATCH,
    PULSE_TO_UNLOCK_ONCE;

    public RedstoneTuningMode next() {
        return this == EMIT_ON_DISPATCH ? PULSE_TO_UNLOCK_ONCE : EMIT_ON_DISPATCH;
    }
}
