package com.fish_dan_.data_energistics.common.menu.patternprovider;

import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationAdapter;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderWorkstationTarget;
import com.fish_dan_.data_energistics.common.blockentity.patternprovider.DigitalPackagedPatternProviderBlockEntity;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;
import com.fish_dan_.data_energistics.integration.ae.ae2.accessor.patternprovider.PatternProviderBatchAccess;
import com.fish_dan_.data_energistics.integration.ae.ae2.patternprovider.adaptive.AdaptivePatternProviderHost;
import com.fish_dan_.data_energistics.integration.ae.ae2.patternprovider.adaptive.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.integration.ae.ae2.patternprovider.adaptive.AdaptivePatternProviderResolver;
import com.fish_dan_.data_energistics.integration.ae.ae2.patternprovider.packaged.PackagedAdaptiveRoute;

import appeng.helpers.patternprovider.PatternContainer;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** Uses dispatch's recipe-specific target check for packaged adapters that also participate in uploads. */
final class PackagedPatternUploadRouting {

    private PackagedPatternUploadRouting() {}

    static @Nullable ObjectList<PatternProviderWorkstationTarget> targets(PatternContainer provider,
                                                                          @Nullable ResourceLocation type) {
        if (adapters(provider, type) == null) return null;
        var links = new ObjectArrayList<ConnectorLink>();
        ServerLevel level;
        if (provider instanceof DigitalPackagedPatternProviderBlockEntity packaged) {
            if (!(packaged.getLevel() instanceof ServerLevel server)) return ObjectList.of();
            level = server;
            for (Direction side : Direction.values()) {
                links.add(new ConnectorLink(packaged.getBlockPos().relative(side), side.getOpposite()));
            }
        } else if (provider instanceof AdaptivePatternProviderHost host && host.getLogic() instanceof AdaptivePatternProviderLogic logic) {
            var registration = AdaptivePatternProviderResolver.resolveProviderRegistration(host.getProviderStack());
            if (registration == null || !(registration.dispatch() instanceof PackagedAdaptiveRoute)) return null;
            if (!(host.getBlockEntity().getLevel() instanceof ServerLevel server)) return ObjectList.of();
            level = server;
            var remote = logic.adaptiveConnectorBindings();
            links.addAll(remote);
            var sides = new ObjectArrayList<Direction>();
            if (remote.isEmpty()) {
                sides.addAll(((PatternProviderBatchAccess) logic).dataEnergistics$invokeGetActiveSides());
            } else {
                for (ConnectorLink link : remote) sides.add(link.side());
            }
            for (Direction side : sides) {
                links.add(new ConnectorLink(host.getBlockEntity().getBlockPos().relative(side), side.getOpposite()));
            }
        } else {
            return null;
        }
        var targets = new ObjectArrayList<PatternProviderWorkstationTarget>();
        var visited = new LongOpenHashSet();
        for (ConnectorLink link : links) {
            if (!link.mode().supportsInput() || !visited.add(link.position().asLong()) || !level.isLoaded(link.position())) continue;
            var workstation = level.getBlockEntity(link.position());
            if (workstation != null) targets.add(new PatternProviderWorkstationTarget(workstation, link.side()));
        }
        return targets;
    }

    static @Nullable ObjectList<PatternProviderWorkstationTarget> compatibleTargets(
                                                                                    PatternContainer provider,
                                                                                    ObjectList<PatternProviderWorkstationTarget> targets,
                                                                                    @Nullable ResourceLocation type,
                                                                                    @Nullable ResourceLocation recipe) {
        var adapters = adapters(provider, type);
        if (adapters == null) return null;
        var compatible = new ObjectArrayList<PatternProviderWorkstationTarget>();
        if (recipe == null) return compatible;
        for (PatternProviderWorkstationTarget target : targets) {
            var workstation = target.workstation();
            if (workstation.isRemoved() || !(workstation.getLevel() instanceof ServerLevel level)) continue;
            for (PackagedMachineAdapter adapter : adapters) {
                if (adapter.supportsRecipe(level, workstation.getBlockPos(), type, recipe)) {
                    compatible.add(target);
                    break;
                }
            }
        }
        return compatible;
    }

    private static @Nullable ObjectList<PackagedMachineAdapter> adapters(PatternContainer provider,
                                                                         @Nullable ResourceLocation type) {
        if (type == null) return null;
        if (!(provider instanceof DigitalPackagedPatternProviderBlockEntity)) {
            if (!(provider instanceof AdaptivePatternProviderHost host) ||
                    !(host.getLogic() instanceof PatternProviderBatchAccess))
                return null;
            var registration = AdaptivePatternProviderResolver.resolveProviderRegistration(host.getProviderStack());
            if (registration == null || !(registration.dispatch() instanceof PackagedAdaptiveRoute)) return null;
        }
        var adapters = new ObjectArrayList<PackagedMachineAdapter>();
        for (PackagedMachineAdapter adapter : DataEnergisticsEntrypointLoader.snapshot().packagedCrafting().forType(type)) {
            if (adapter instanceof PatternUploadWorkstationAdapter) adapters.add(adapter);
        }
        return adapters.isEmpty() ? null : adapters;
    }
}
