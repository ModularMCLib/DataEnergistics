package com.fish_dan_.data_energistics.util.input;

import net.minecraft.server.level.ServerPlayer;

/**
 * Observes synchronized key transitions without coupling server logic to client input classes.
 * Callbacks run on the server thread, including releases during logout or respawn. Listeners are
 * retained until explicitly removed; player-specific listeners are also removed when their player
 * session ends. Explicit removal ends a subscription immediately.
 */
@FunctionalInterface
public interface KeyPressedListener {

    /**
     * Called once per changed state with a non-null player and registered binding. {@code isDown}
     * describes the complete chord, including its context and modifiers. This is untrusted client
     * input: listeners must still validate the player's equipment and permissions before acting.
     * Exceptions propagate to the calling server task; callbacks may unregister themselves.
     */
    void onKeyPressed(ServerPlayer player, SyncedKeyMapping mapping, boolean isDown);
}
