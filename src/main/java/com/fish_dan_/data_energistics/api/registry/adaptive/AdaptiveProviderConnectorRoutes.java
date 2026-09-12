package com.fish_dan_.data_energistics.api.registry.adaptive;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.List;

/** Stable target selection helpers shared by registered adaptive-provider routes. */
public final class AdaptiveProviderConnectorRoutes {

    private AdaptiveProviderConnectorRoutes() {}

    /** Returns configured links, or normal active sides when no links were configured. */
    public static ObjectList<AdaptiveProviderConnectorBinding> resolve(
                                                                       AdaptivePatternProviderDispatchTarget target) {
        List<AdaptiveProviderConnectorBinding> configured = target.connectorBindings();
        if (!configured.isEmpty()) {
            ObjectArrayList<AdaptiveProviderConnectorBinding> result = new ObjectArrayList<>(configured);
            return result;
        }
        ObjectArrayList<AdaptiveProviderConnectorBinding> active = new ObjectArrayList<>(target.targetSides().size());
        for (var side : target.targetSides()) {
            active.add(new AdaptiveProviderConnectorBinding(
                    target.providerPos().relative(side), side.getOpposite()));
        }
        return active;
    }
}
