package com.fish_dan_.data_energistics.api.registry.provider.client;

import org.jspecify.annotations.NullMarked;

/**
 * Client-plugin transaction for native provider screen factories. Declare factories only while the plugin callback
 * is open; callbacks run later on the screen's client thread. Native screens need no DE interfaces or constructors.
 */
@NullMarked
public interface PatternProviderScreenRegistry {

    /**
     * Stages a non-null menu class and screen factory. Duplicate classes and closed transactions
     * throw IllegalStateException; successful declarations are published after client plugin discovery completes.
     */
    void register(PatternProviderScreenRegistration registration);
}
