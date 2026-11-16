package com.fish_dan_.data_energistics.integration.ae.advancedae.patternprovider;

import appeng.api.networking.security.IActionSource;

import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;

import org.jspecify.annotations.NullMarked;

/** Required Mixin access to Advanced AE's private dispatch state; callbacks read it only on the provider thread. */
@NullMarked
public interface AdvancedAeDispatchAccess {

    /** Returns the non-null native host for the lifetime of this logic instance. */
    AdvPatternProviderLogicHost dataEnergistics$dispatchHost();

    /** Returns the non-null native action source used for crafting requests. */
    IActionSource dataEnergistics$dispatchActionSource();

    /** Reports whether the native send list still contains accepted inputs. */
    boolean dataEnergistics$sendingInputs();
}
