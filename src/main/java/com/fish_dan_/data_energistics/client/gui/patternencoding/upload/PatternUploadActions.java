package com.fish_dan_.data_energistics.client.gui.patternencoding.upload;

import org.jspecify.annotations.NullMarked;

/**
 * Binds the shared upload view to an existing menu or player-terminal session. Invoked on the client thread;
 * implementations send requests through their own server-authoritative protocol. A zero leaf selects the group.
 */
@NullMarked
public interface PatternUploadActions {

    /** Requests upload of the currently displayed pattern to the selected group or physical provider. */
    void upload(long group, long leaf);

    /** Commits an entered name of at most 50 characters; an empty name restores the provider's default name. */
    void rename(long group, long leaf, String name);

    /** Opens the selected provider's existing menu, preserving the source menu's return route. */
    void open(long group, long leaf);
}
