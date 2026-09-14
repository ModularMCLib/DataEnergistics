package com.fish_dan_.data_energistics.api.registry.connector;

import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchTarget;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.List;

/** Stable target selection helpers shared by registered adaptive-provider routes. */
public final class ConnectorRouteTargets {

    private ConnectorRouteTargets() {}

    /** Returns configured links, or normal active sides when no links were configured. */
    public static ObjectList<ConnectorLink> resolve(
                                                    AdaptivePatternProviderDispatchTarget target) {
        ObjectList<ConnectorLink> configured = target.connectorBindingsFast();
        if (!configured.isEmpty()) {
            return configured;
        }
        ObjectArrayList<ConnectorLink> active = new ObjectArrayList<>(target.targetSidesFast().size());
        for (var side : target.targetSidesFast()) {
            active.add(new ConnectorLink(
                    target.providerPos().relative(side), side.getOpposite()));
        }
        return active;
    }

    /** Returns only links assigned to the requested transfer direction. */
    public static ObjectList<ConnectorLink> resolve(
                                                    AdaptivePatternProviderDispatchTarget target,
                                                    ConnectorMode mode) {
        List<ConnectorLink> configured = target.connectorBindingsFast();
        if (!configured.isEmpty()) {
            ObjectArrayList<ConnectorLink> result = new ObjectArrayList<>(configured.size());
            for (ConnectorLink binding : configured) {
                if (mode == ConnectorMode.INPUT && binding.mode().supportsInput() || mode == ConnectorMode.PULL && binding.mode().supportsPull()) {
                    result.add(binding);
                }
            }
            return result;
        }
        ObjectArrayList<ConnectorLink> fallback = new ObjectArrayList<>(target.targetSidesFast().size());
        for (var side : target.targetSidesFast()) {
            fallback.add(new ConnectorLink(
                    target.providerPos().relative(side), side.getOpposite(), mode));
        }
        return fallback;
    }
}
