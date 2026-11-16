package com.fish_dan_.data_energistics.api.registry.provider.menu;

import org.jspecify.annotations.NullMarked;

/**
 * Menu-local redstone controls supplied by a registered factory, without requiring the native menu to implement them.
 * Instances live for one open menu and are accessed only on its logical client or server thread.
 */
@NullMarked
public interface PatternProviderRedstoneMenu {

    /** Returns the current server state, or its synchronized client copy, without changing the upgrade inventory. */
    boolean hasRedstoneTuningCard();

    /** Returns the synchronized mode: 0 emits on dispatch, and 1 consumes a pulse to unlock one dispatch. */
    int getRedstoneTuningMode();

    /**
     * Requests a supported mode on the owning menu thread. Client implementations must send the request to the server;
     * the server validates incoming ordinals before changing the host. Invalid requests may throw.
     */
    void setRedstoneTuningMode(int ordinal);
}
