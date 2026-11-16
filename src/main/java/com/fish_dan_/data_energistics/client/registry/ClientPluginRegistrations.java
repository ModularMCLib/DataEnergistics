package com.fish_dan_.data_energistics.client.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.client.DataEnergisticsClientPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.client.DataEnergisticsClientRegistry;
import com.fish_dan_.data_energistics.api.registry.adaptive.client.AdaptivePatternProviderToolbarButton;
import com.fish_dan_.data_energistics.api.registry.adaptive.client.AdaptivePatternProviderToolbarContext;
import com.fish_dan_.data_energistics.api.registry.adaptive.client.AdaptivePatternProviderToolbarRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.client.PatternProviderScreenRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.client.PatternProviderScreenRegistry;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;

import appeng.menu.AEBaseMenu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;

/**
 * Publishes client plugin factories before any provider screen can open.
 */
@NullMarked
public final class ClientPluginRegistrations {

    private static ObjectList<Entry> entries = ObjectList.of();
    private static Reference2ObjectMap<Class<?>, @Nullable PatternProviderScreenRegistration> providerScreens = Reference2ObjectMaps.emptyMap();

    private ClientPluginRegistrations() {}

    public static void initialize() {
        Object2ObjectOpenHashMap<ResourceLocation, Entry> published = new Object2ObjectOpenHashMap<>();
        Reference2ObjectMap<Class<?>, PatternProviderScreenRegistration> screens = new Reference2ObjectOpenHashMap<>();
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
                for (Class<?> screenType : staging.screens.keySet()) {
                    if (screens.containsKey(screenType)) {
                        throw new IllegalStateException("Duplicate pattern provider screen: " + screenType.getName());
                    }
                }
                published.putAll(staging.factories);
                screens.putAll(staging.screens);
            } catch (Exception | LinkageError exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to register client plugin {} owned by {}; discarded its declarations",
                        candidate.className(), candidate.owningModId(), exception);
            } finally {
                staging.open = false;
            }
        }
        ObjectArrayList<Entry> sorted = new ObjectArrayList<>(published.values());
        sorted.sort(Comparator.comparingInt(Entry::order).thenComparing(entry -> entry.actionId().toString()));
        entries = ObjectLists.unmodifiable(sorted);
        providerScreens = Reference2ObjectMaps.unmodifiable(screens);
    }

    public static ObjectList<Entry> entries() {
        return entries;
    }

    /** Resolves the closest registered menu superclass once during native screen construction. */
    public static @Nullable PatternProviderScreenRegistration providerScreen(Class<? extends AEBaseMenu> menuType) {
        for (Class<?> type = menuType; type != AbstractContainerMenu.class; type = type.getSuperclass()) {
            var registration = providerScreens.get(type);
            if (registration != null) {
                return registration;
            }
        }
        return null;
    }

    /**
     * Screen factory descriptor, captured only after all successful entrypoint transactions have completed.
     */
    public record Entry(ResourceLocation actionId, int order,
                        Function<AdaptivePatternProviderToolbarContext, AdaptivePatternProviderToolbarButton> factory) {}

    private static final class Staging implements DataEnergisticsClientRegistry, AdaptivePatternProviderToolbarRegistry, PatternProviderScreenRegistry {

        private final Object2ObjectOpenHashMap<ResourceLocation, Entry> factories = new Object2ObjectOpenHashMap<>();
        private final Reference2ObjectMap<Class<?>, PatternProviderScreenRegistration> screens = new Reference2ObjectOpenHashMap<>();
        private boolean open = true;

        @Override
        public AdaptivePatternProviderToolbarRegistry adaptivePatternProviderToolbar() {
            return this;
        }

        @Override
        public PatternProviderScreenRegistry patternProviderScreens() {
            return this;
        }

        @Override
        public void register(PatternProviderScreenRegistration registration) {
            if (!open) {
                throw new IllegalStateException("Pattern provider screen registration is closed");
            }
            Objects.requireNonNull(registration.menuType(), "Pattern provider screen menu type");
            Objects.requireNonNull(registration.factory(), "Pattern provider screen factory");
            if (screens.putIfAbsent(registration.menuType(), registration) != null) {
                throw new IllegalStateException("Duplicate pattern provider screen menu type: " + registration.menuType().getName());
            }
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
