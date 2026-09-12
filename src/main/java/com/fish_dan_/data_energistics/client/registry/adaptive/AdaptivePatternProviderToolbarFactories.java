package com.fish_dan_.data_energistics.client.registry.adaptive;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.client.DataEnergisticsClientPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.client.DataEnergisticsClientRegistry;
import com.fish_dan_.data_energistics.api.registry.adaptive.client.AdaptivePatternProviderToolbarButton;
import com.fish_dan_.data_energistics.api.registry.adaptive.client.AdaptivePatternProviderToolbarContext;
import com.fish_dan_.data_energistics.api.registry.adaptive.client.AdaptivePatternProviderToolbarRegistry;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.Comparator;
import java.util.function.Function;

/**
 * Publishes the client-only factory snapshot before any adaptive menus can be opened.
 */
public final class AdaptivePatternProviderToolbarFactories {

    private static ObjectList<Entry> entries = ObjectList.of();
    private static boolean initialized;

    private AdaptivePatternProviderToolbarFactories() {}

    public static void initialize() {
        if (initialized) {
            throw new IllegalStateException("Adaptive toolbar factories are already initialized");
        }
        Object2ObjectOpenHashMap<ResourceLocation, Entry> published = new Object2ObjectOpenHashMap<>();
        for (var candidate : DataEnergisticsEntrypointLoader.discoverCandidates(true)) {
            Staging staging = new Staging();
            try {
                var plugin = DataEnergisticsEntrypointLoader.instantiate(candidate, DataEnergisticsClientPlugin.class);
                plugin.register(staging);
                for (ResourceLocation id : staging.factories.keySet()) {
                    if (published.containsKey(id)) {
                        throw new IllegalStateException("Duplicate adaptive toolbar action: " + id);
                    }
                }
                published.putAll(staging.factories);
            } catch (Exception | LinkageError exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to register client plugin {} owned by {}; discarded its toolbar declarations",
                        candidate.className(), candidate.owningModId(), exception);
            } finally {
                staging.open = false;
            }
        }
        ObjectArrayList<Entry> sorted = new ObjectArrayList<>(published.values());
        sorted.sort(Comparator.comparingInt(Entry::order).thenComparing(entry -> entry.actionId().toString()));
        entries = ObjectLists.unmodifiable(sorted);
        initialized = true;
    }

    public static ObjectList<Entry> entries() {
        if (!initialized) {
            throw new IllegalStateException("Adaptive toolbar factories have not been initialized");
        }
        return entries;
    }

    /**
     * Screen factory descriptor, captured only after all successful entrypoint transactions have completed.
     */
    public record Entry(ResourceLocation actionId, int order,
                        Function<AdaptivePatternProviderToolbarContext, AdaptivePatternProviderToolbarButton> factory) {}

    private static final class Staging implements DataEnergisticsClientRegistry, AdaptivePatternProviderToolbarRegistry {

        private final Object2ObjectOpenHashMap<ResourceLocation, Entry> factories = new Object2ObjectOpenHashMap<>();
        private boolean open = true;

        @Override
        public AdaptivePatternProviderToolbarRegistry adaptivePatternProviderToolbar() {
            return this;
        }

        @Override
        public void register(ResourceLocation actionId, int order,
                             Function<AdaptivePatternProviderToolbarContext, AdaptivePatternProviderToolbarButton> factory) {
            if (!open) {
                throw new IllegalStateException("Adaptive toolbar registration is closed");
            }
            if (factories.putIfAbsent(actionId, new Entry(actionId, order, factory)) != null) {
                throw new IllegalStateException("Duplicate adaptive toolbar action: " + actionId);
            }
        }
    }
}
