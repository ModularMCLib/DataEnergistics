package com.fish_dan_.data_energistics.common.entrypoint.provider;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderMenuOpenResult;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderWorkstationSource;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRuntimeRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderWorkstationSourceRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentity;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderMatchingMetadata;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.helpers.patternprovider.PatternContainer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Installed access point for the frozen provider registry and its server-lifetime live bindings.
 */
public final class PatternProviderRuntimeBindings {

    @Nullable
    private static volatile LivePatternProviderBindingRegistry registry;

    private PatternProviderRuntimeBindings() {}

    /**
     * Installs the immutable declaration snapshot exactly once during common setup.
     */
    public static synchronized void install(
                                            List<PatternProviderRegistration> registrations,
                                            List<PatternProviderWorkstationSourceRegistration> workstationSources,
                                            List<PatternProviderRuntimeRegistration<?>> runtimeRegistrations) {
        if (registry != null) {
            throw new IllegalStateException("Pattern provider runtime bindings are already installed");
        }
        registry = new LivePatternProviderBindingRegistry(registrations, workstationSources, runtimeRegistrations);
    }

    /**
     * Binds one publication while isolating plugin factory failures from AE2's mount lifecycle.
     */
    public static void bind(CraftingProviderId publicationId, ICraftingProvider provider) {
        LivePatternProviderBindingRegistry current = requireInstalled();
        try {
            current.bind(publicationId, provider);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to bind Data Energistics provider plugin for publication {} and provider {}",
                    publicationId,
                    provider,
                    exception);
        }
    }

    /**
     * Releases one exact publication while allowing AE2 to finish its unmount lifecycle.
     */
    public static void unbind(CraftingProviderId publicationId) {
        LivePatternProviderBindingRegistry current = requireInstalled();
        try {
            current.unbind(publicationId);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to unbind Data Energistics provider plugin publication {}",
                    publicationId,
                    exception);
        }
    }

    /**
     * Resolves the unique plugin declaration selected for one terminal-visible provider.
     */
    public static Optional<ResolvedProviderBinding> resolve(PatternContainer container) {
        return requireInstalled().resolve(container);
    }

    /** Resolves the identity and declaration together for one terminal operation. */
    public static ResolvedProviderContext resolveContext(PatternContainer container) {
        return requireInstalled().resolveContext(container);
    }

    /** Reads current matching data through the callback selected for this native provider type. */
    public static @Nullable PatternProviderMatchingMetadata matchingMetadata(
                                                                             PatternContainer container, @Nullable ResourceLocation recipeCategoryId) {
        return requireInstalled().runtimeAdapters().matchingMetadata(container, recipeCategoryId);
    }

    /** Reports whether this native provider type declares a menu callback. */
    public static boolean hasMenuOpener(PatternContainer container) {
        return requireInstalled().runtimeAdapters().hasMenuOpener(container);
    }

    /** Invokes the selected menu callback, or returns PASS when none is registered. */
    public static PatternProviderMenuOpenResult openMenu(PatternContainer container, ServerPlayer player) {
        return requireInstalled().runtimeAdapters().openMenu(container, player);
    }

    /** Resolves the custom workstation topology registered for one live provider identity family. */
    public static @Nullable PatternProviderWorkstationSource resolveWorkstationSource(
                                                                                      PatternProviderIdentity identity) {
        return requireInstalled().resolveWorkstationSource(identity);
    }

    /**
     * Clears every live provider binding when the server stops.
     */
    public static void clearLiveBindings() {
        requireInstalled().clear();
    }

    private static LivePatternProviderBindingRegistry requireInstalled() {
        LivePatternProviderBindingRegistry current = registry;
        if (current == null) {
            throw new IllegalStateException("Pattern provider runtime bindings are not installed");
        }
        return current;
    }
}
