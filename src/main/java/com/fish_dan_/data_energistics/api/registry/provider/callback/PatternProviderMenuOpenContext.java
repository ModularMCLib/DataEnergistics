package com.fish_dan_.data_energistics.api.registry.provider.callback;

import appeng.helpers.patternprovider.PatternContainer;

import net.minecraft.server.level.ServerPlayer;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

/**
 * Immutable group snapshot supplied to a menu-open adapter.
 *
 * @param player    server-side player requesting the provider menu
 * @param providers complete provider group selected by the terminal
 */
public record PatternProviderMenuOpenContext(ServerPlayer player,
                                             ObjectList<PatternContainer> providers) {

    /** Returns an immutable FastUtil view of the selected provider group. */
    public ObjectList<PatternContainer> providersFast() {
        return ObjectLists.unmodifiable(new ObjectArrayList<>(providers));
    }

    /**
     * Copies the group so an adapter cannot mutate the dispatcher-owned list.
     */
    public PatternProviderMenuOpenContext {
        providers = new ObjectImmutableList<>(providers);
    }
}
