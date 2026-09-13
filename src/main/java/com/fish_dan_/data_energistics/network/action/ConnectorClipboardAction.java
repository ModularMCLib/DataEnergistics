package com.fish_dan_.data_energistics.network.action;

public enum ConnectorClipboardAction {

    SELECT_ALL,
    COPY,
    CUT,
    PASTE;

    public static ConnectorClipboardAction fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("Invalid connector clipboard operation: " + ordinal);
        }
        return values()[ordinal];
    }
}
