package com.fish_dan_.data_energistics.integration.ae.ae2.accessor.patternprovider;

import com.fish_dan_.data_energistics.api.registry.provider.dispatch.PatternProviderDispatchHooks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Mixin storage bridge for native subclasses that override dispatch methods. A policy is resolved once during native
 * construction and subsequently used on the owning server thread; unsupported native hosts have no policy.
 */
@NullMarked
public interface PatternProviderDispatchAccess {

    /** Returns the policy owned by this logic object, or null when its registration declines the native host. */
    @Nullable
    PatternProviderDispatchHooks dataEnergistics$dispatchHooks();
}
