package com.fish_dan_.data_energistics.ae2.patternprovider.packaged;

import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatch;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchContext;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchTarget;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedDispatchState;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;

import appeng.api.crafting.IPatternDetails;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** Same-dimension remote automation selected by installing the digital packaged provider in an adaptive host. */
public final class PackagedAdaptiveRoute implements AdaptivePatternProviderDispatch {

    public static PackagedDispatchState state(AdaptivePatternProviderDispatchTarget target) {
        return target.routeState(State.class, State::new).dispatch;
    }

    @Override
    public boolean validatesMachineCapacity() {
        return true;
    }

    @Override
    public boolean usesSpecialBatchRoute(IPatternDetails patternDetails) {
        return true;
    }

    @Override
    public boolean handles(AdaptivePatternProviderDispatchContext context) {
        return true;
    }

    @Override
    public boolean acceptsConnectorMachine(ServerLevel level, BlockPos position) {
        if (!level.isLoaded(position)) return false;
        for (var adapter : DataEnergisticsEntrypointLoader.snapshot().packagedCrafting().adapters()) {
            if (adapter.recognizes(level, position)) return true;
        }
        return false;
    }

    @Override
    public boolean dispatch(AdaptivePatternProviderDispatchContext context) {
        var target = context.target();
        if (!(target.level() instanceof ServerLevel level) || !target.isActive() || target.isBusy() ||
                target.isCraftingLocked() || !target.hasPattern(context.patternDetails()))
            return false;
        var adjacent = new ObjectArrayList<ConnectorLink>();
        for (var side : target.targetSidesFast()) {
            adjacent.add(new ConnectorLink(target.providerPos().relative(side), side.getOpposite()));
        }
        boolean accepted = state(target).dispatch(level, DataEnergisticsEntrypointLoader.snapshot().packagedCrafting(),
                context.patternDetails(), context.inputHolder(), target.connectorBindingsFast(), adjacent);
        if (accepted) {
            target.saveChanges();
            target.alertDevice();
            target.patternSuccess(context.patternDetails());
        }
        return accepted;
    }

    @Override
    public boolean hasWork(AdaptivePatternProviderDispatchTarget target) {
        return state(target).hasWork();
    }

    @Override
    public boolean tick(AdaptivePatternProviderDispatchTarget target, int ticksSinceLastCall) {
        if (!(target.level() instanceof ServerLevel level) || !target.isActive()) return false;
        boolean worked = state(target).tick(level, DataEnergisticsEntrypointLoader.snapshot().packagedCrafting(),
                target.returnInventory(), target.actionSource());
        if (worked) target.saveChanges();
        return worked;
    }

    @Override
    public void writeState(AdaptivePatternProviderDispatchTarget target, CompoundTag tag, HolderLookup.Provider registries) {
        state(target).save(tag, registries);
    }

    @Override
    public void readState(AdaptivePatternProviderDispatchTarget target, CompoundTag tag, HolderLookup.Provider registries) {
        target.routeState(State.class, State::new).dispatch = PackagedDispatchState.load(tag, registries);
    }

    private static final class State {

        private PackagedDispatchState dispatch = new PackagedDispatchState();
    }
}
