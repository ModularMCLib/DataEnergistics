package com.fish_dan_.data_energistics.api.registry.adaptive;

import net.minecraft.resources.ResourceLocation;

/**
 * Stable identifier for one Adaptive left-toolbar action.
 *
 * <p>
 * The client resolves the identifier to a button factory. Registrations
 * therefore describe which controls belong to a provider without constructing
 * client widgets from common setup.
 * </p>
 */
public record AdaptivePatternProviderToolbarAction(ResourceLocation actionId) {}
