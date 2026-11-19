package com.fish_dan_.data_energistics.common.entrypoint.provider;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderWorkstationSource;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRuntimeRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderWorkstationSourceRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderFactory;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderFactoryContext;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapterRegistry;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsRegistrySnapshot;
import com.fish_dan_.data_energistics.common.pattern.ProviderIdentity;
import com.fish_dan_.data_energistics.common.pattern.ProviderIdentityResolver;
import com.fish_dan_.data_energistics.integration.ae.ae2.accessor.patternprovider.PatternProviderBatchAccess;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.helpers.patternprovider.PatternContainer;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Identity-preserving runtime registry installed during mod construction. Live publication mutations belong to the
 * logical server thread. The runtime adapters also support read-side lookups on the logical client.
 */
public final class LivePatternProviderBindingRegistry {

    private static volatile @Nullable LivePatternProviderBindingRegistry installed;

    private final Object2ObjectMap<ProviderIdentityDescriptor, PatternProviderRegistration> registrationsByIdentity;
    private final Object2ObjectMap<ProviderIdentityDescriptor, PatternProviderWorkstationSource> workstationSourcesByIdentity;
    private final ProviderIdentityResolver identityResolver;
    private final PatternProviderRuntimeAdapters runtimeAdapters;
    private final Object2ObjectMap<CraftingProviderId, ICraftingProvider> publications = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<CraftingProviderId, ProviderAdapterState> adaptersByPublication = new Object2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<ICraftingProvider, ProviderAdapterState> adaptersByProvider = new Reference2ObjectOpenHashMap<>();

    private LivePatternProviderBindingRegistry(List<PatternProviderRegistration> registrations,
                                               List<PatternProviderWorkstationSourceRegistration> workstationSources,
                                               List<PatternProviderRuntimeRegistration<?>> runtimeRegistrations) {
        Object2ObjectMap<ProviderIdentityDescriptor, PatternProviderRegistration> indexed = new Object2ObjectOpenHashMap<>(
                registrations.size());
        for (PatternProviderRegistration registration : registrations) {
            ProviderIdentityDescriptor descriptor = registration.metadata().providerIdentity();
            PatternProviderRegistration existing = indexed.putIfAbsent(descriptor, registration);
            if (existing != null) {
                throw new IllegalStateException("Duplicate frozen pattern provider identity: " + descriptor);
            }
        }
        this.registrationsByIdentity = Object2ObjectMaps.unmodifiable(indexed);
        Object2ObjectMap<ProviderIdentityDescriptor, PatternProviderWorkstationSource> indexedSources = new Object2ObjectOpenHashMap<>(
                workstationSources.size());
        for (PatternProviderWorkstationSourceRegistration registration : workstationSources) {
            PatternProviderWorkstationSource existing = indexedSources.putIfAbsent(
                    registration.providerIdentity(),
                    registration.source());
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate frozen pattern provider workstation source identity: " +
                                registration.providerIdentity());
            }
        }
        this.workstationSourcesByIdentity = Object2ObjectMaps.unmodifiable(indexedSources);
        this.identityResolver = ProviderIdentityResolver.create();
        this.runtimeAdapters = new PatternProviderRuntimeAdapters(runtimeRegistrations);
    }

    public static void install(DataEnergisticsRegistrySnapshot snapshot) {
        installed = new LivePatternProviderBindingRegistry(
                snapshot.patternProviderRegistrations(),
                snapshot.patternProviderWorkstationSourceRegistrations(),
                snapshot.patternProviderRuntimeRegistrations());
    }

    public static LivePatternProviderBindingRegistry get() {
        LivePatternProviderBindingRegistry registry = installed;
        if (registry == null) {
            throw new IllegalStateException("Pattern provider bindings have not been installed");
        }
        return registry;
    }

    public void bind(CraftingProviderId publicationId, ICraftingProvider provider) {
        // Track the AE2 publication even if the optional adapter cannot be created.
        if (this.publications.putIfAbsent(publicationId, provider) != null) {
            throw new IllegalStateException("Pattern provider publication is already bound: " + publicationId);
        }

        Optional<ResolvedProviderBinding> resolved = this.resolve(provider);
        if (resolved.isEmpty()) {
            return;
        }
        ResolvedProviderBinding binding = resolved.get();
        PatternProviderFactory factory = binding.registration().factory();
        if (factory == null) {
            return;
        }

        ProviderAdapterState current = this.adaptersByProvider.get(provider);
        if (current != null) {
            current.retain(binding);
            this.adaptersByPublication.put(publicationId, current);
            return;
        }

        PatternProviderFactoryContext context = new PatternProviderFactoryContext(
                provider,
                binding.container(),
                binding.identity(),
                binding.registration().metadata());
        CountedCraftingProviderAdapter adapter;
        try {
            adapter = Objects.requireNonNull(factory.create(context), "Provider adapter factory returned null");
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Pattern provider plugin '" + binding.registration().metadata().registrationId() + "' failed to create an adapter for " + binding.identity(),
                    exception);
        }
        ProviderAdapterState created = new ProviderAdapterState(binding, adapter);
        CountedCraftingProviderAdapterRegistry.get().register(provider, adapter);
        this.adaptersByProvider.put(provider, created);
        this.adaptersByPublication.put(publicationId, created);
    }

    public void unbind(CraftingProviderId publicationId) {
        ICraftingProvider provider = this.publications.get(publicationId);
        if (provider == null) {
            throw new IllegalStateException("Pattern provider publication is not bound: " + publicationId);
        }
        ProviderAdapterState state = this.adaptersByPublication.get(publicationId);
        if (state == null) {
            this.publications.remove(publicationId);
            return;
        }
        if (state.publicationCount() == 1) {
            CountedCraftingProviderAdapterRegistry.get().unregister(provider, state.adapter());
            this.adaptersByProvider.remove(provider);
        } else {
            state.release();
        }
        this.adaptersByPublication.remove(publicationId);
        this.publications.remove(publicationId);
    }

    public Optional<ResolvedProviderBinding> resolve(PatternContainer container) {
        return Optional.ofNullable(this.resolveContext(container).binding());
    }

    public ResolvedProviderContext resolveContext(PatternContainer container) {
        ProviderIdentity identity = this.runtimeAdapters.identity(container);
        if (identity == null) {
            identity = this.identityResolver.resolve(container);
        }
        Optional<ProviderIdentityDescriptor> descriptor = ProviderIdentityDescriptor.from(identity);
        PatternProviderRegistration registration = descriptor.isEmpty() ? null : this.registrationsByIdentity.get(descriptor.get());
        return new ResolvedProviderContext(identity,
                registration == null ? null : new ResolvedProviderBinding(registration, container, identity));
    }

    public PatternProviderRuntimeAdapters runtimeAdapters() {
        return this.runtimeAdapters;
    }

    @Nullable
    public PatternProviderWorkstationSource resolveWorkstationSource(PatternProviderIdentity identity) {
        Optional<ProviderIdentityDescriptor> descriptor = identity.descriptor();
        return descriptor.isEmpty() ? null : this.workstationSourcesByIdentity.get(descriptor.get());
    }

    public void clearLiveBindings() {
        CountedCraftingProviderAdapterRegistry.get().clear();
        this.adaptersByProvider.clear();
        this.adaptersByPublication.clear();
        this.publications.clear();
    }

    private Optional<ResolvedProviderBinding> resolve(ICraftingProvider provider) {
        PatternContainer container = this.resolveContainer(provider);
        return container == null ? Optional.empty() : Optional.ofNullable(this.resolveContext(container).binding());
    }

    @Nullable
    private PatternContainer resolveContainer(ICraftingProvider provider) {
        PatternContainer linked = this.runtimeAdapters.container(provider);
        if (linked != null) {
            return linked;
        }
        if (provider instanceof PatternContainer container) {
            return container;
        }
        if (provider instanceof PatternProviderBatchAccess access) {
            return access.dataEnergistics$getHost();
        }
        return null;
    }

    private static final class ProviderAdapterState {

        private final ResolvedProviderBinding binding;
        private final CountedCraftingProviderAdapter adapter;
        private int publicationCount = 1;

        private ProviderAdapterState(ResolvedProviderBinding binding,
                                     CountedCraftingProviderAdapter adapter) {
            this.binding = binding;
            this.adapter = adapter;
        }

        private CountedCraftingProviderAdapter adapter() {
            return this.adapter;
        }

        private int publicationCount() {
            return this.publicationCount;
        }

        private void retain(ResolvedProviderBinding current) {
            if (this.binding.registration() != current.registration() ||
                    !this.binding.identity().equals(current.identity())) {
                throw new IllegalStateException("One provider instance resolved to different plugin bindings");
            }
            this.publicationCount = Math.incrementExact(this.publicationCount);
        }

        private void release() {
            this.publicationCount--;
        }
    }
}
