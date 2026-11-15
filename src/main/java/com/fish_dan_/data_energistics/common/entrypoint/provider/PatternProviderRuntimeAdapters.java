package com.fish_dan_.data_energistics.common.entrypoint.provider;

import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderMenuOpenResult;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRuntimeRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentitySource;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderMatchingMetadata;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderMatchingMetadataSource;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderRuntimeLink;
import com.fish_dan_.data_energistics.common.pattern.ProviderIdentity;

import appeng.helpers.patternprovider.PatternContainer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;

/** Frozen type callbacks with a server-thread class cache; no live provider instance is retained. */
final class PatternProviderRuntimeAdapters {

    private final ObjectList<PatternProviderRuntimeRegistration<?>> registrations;
    private final Reference2ObjectMap<Class<?>, RuntimeAccess> accessByType = new Reference2ObjectOpenHashMap<>();

    PatternProviderRuntimeAdapters(List<PatternProviderRuntimeRegistration<?>> registrations) {
        this.registrations = new ObjectArrayList<>(registrations);
        this.registrations.sort(Comparator.comparingInt(
                (PatternProviderRuntimeRegistration<?> registration) -> registration.priority()).reversed());
    }

    @Nullable
    PatternContainer container(Object provider) {
        var link = access(provider).runtimeLink;
        return link == null ? null : link.patternContainer(provider);
    }

    @Nullable
    ProviderIdentity identity(Object provider) {
        var source = access(provider).identitySource;
        return source == null ? null : ProviderIdentity.fromExternal(source.providerIdentity(provider), "Registered provider");
    }

    @Nullable
    PatternProviderMatchingMetadata matchingMetadata(PatternContainer provider, @Nullable ResourceLocation recipeCategoryId) {
        var source = access(provider).matchingMetadataSource;
        return source == null ? null : source.matchingMetadata(provider, recipeCategoryId);
    }

    boolean hasMenuOpener(PatternContainer provider) {
        return access(provider).menuOpener != null;
    }

    PatternProviderMenuOpenResult openMenu(PatternContainer provider, ServerPlayer player) {
        var opener = access(provider).menuOpener;
        return opener == null ? PatternProviderMenuOpenResult.PASS : opener.apply(provider, player);
    }

    private RuntimeAccess access(Object provider) {
        return this.accessByType.computeIfAbsent(provider.getClass(), this::resolveAccess);
    }

    private RuntimeAccess resolveAccess(Class<?> providerType) {
        var access = new RuntimeAccess();
        for (var registration : this.registrations) {
            if (registration.providerType().isAssignableFrom(providerType)) {
                access.include(registration);
            }
        }
        return access;
    }

    private static final class RuntimeAccess {

        @Nullable
        private PatternProviderRuntimeLink<Object> runtimeLink;
        @Nullable
        private PatternProviderIdentitySource<Object> identitySource;
        @Nullable
        private PatternProviderMatchingMetadataSource<Object> matchingMetadataSource;
        @Nullable
        private BiFunction<Object, ServerPlayer, PatternProviderMenuOpenResult> menuOpener;

        private <T> void include(PatternProviderRuntimeRegistration<T> registration) {
            Class<T> type = registration.providerType();
            var link = registration.runtimeLink();
            if (this.runtimeLink == null && link != null) {
                this.runtimeLink = provider -> link.patternContainer(type.cast(provider));
            }
            var identity = registration.identitySource();
            if (this.identitySource == null && identity != null) {
                this.identitySource = provider -> identity.providerIdentity(type.cast(provider));
            }
            var metadata = registration.matchingMetadataSource();
            if (this.matchingMetadataSource == null && metadata != null) {
                this.matchingMetadataSource = (provider, category) -> metadata.matchingMetadata(type.cast(provider), category);
            }
            var opener = registration.menuOpener();
            if (this.menuOpener == null && opener != null) {
                this.menuOpener = (provider, player) -> opener.apply(type.cast(provider), player);
            }
        }
    }
}
