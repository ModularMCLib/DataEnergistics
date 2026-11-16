package com.fish_dan_.data_energistics.client.screen.patternprovider;

import com.fish_dan_.data_energistics.api.registry.provider.client.PatternProviderScreenRegistration;

import org.jspecify.annotations.NullMarked;

/**
 * Mixin bridge for screen-local registration. Only a constructor-tail compatibility wrapper may replace the pending
 * non-null registration, before the first client-thread init; resizing keeps the existing initialized widgets.
 */
@NullMarked
public interface PatternProviderScreenAccess {

    /**
     * Schedules one non-null factory for this screen's first layout, replacing its menu-type declaration if present.
     */
    void dataEnergistics$attachProviderScreen(PatternProviderScreenRegistration registration);
}
