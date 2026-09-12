package com.fish_dan_.data_energistics.api.registry.adaptive.client;

import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderToolbarMenu;

import java.util.function.BooleanSupplier;

/**
 * Per-screen input for toolbar factories. Only use on the client thread while this menu is open.
 * The right-click supplier must be evaluated during the button callback, not during construction.
 * Neither member is nullable. Factories must create fresh widgets for each context.
 */
public record AdaptivePatternProviderToolbarContext(
                                                    AdaptivePatternProviderToolbarMenu menu,
                                                    BooleanSupplier handlingRightClick) {}
