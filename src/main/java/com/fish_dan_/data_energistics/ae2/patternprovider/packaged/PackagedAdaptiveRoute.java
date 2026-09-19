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
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

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
        var catalog = DataEnergisticsEntrypointLoader.snapshot().packagedCrafting();
        var dispatch = state(target);
        for (var adapter : catalog.adapters()) dispatch.policy(adapter.id(), target.connectorPolicy());
        boolean accepted = dispatch.dispatch(level, catalog,
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

    @Override
    public void addDropsFast(AdaptivePatternProviderDispatchTarget target, ObjectList<ItemStack> drops) {
        if (!(target.level() instanceof ServerLevel level)) return;
        var receipt = state(target).prepareRecoveryDrop(level, target.providerPos(), "adaptive", target.returnInventory());
        if (!receipt.isEmpty()) {
            drops.add(receipt);
            target.saveChanges();
        }
    }

    @Override
    public void clearState(AdaptivePatternProviderDispatchTarget target) {
        state(target).ensureCanClear();
        target.clearRouteState();
    }

    @Override
    public boolean restoreRecoveryItem(AdaptivePatternProviderDispatchTarget target, ItemStack receipt) {
        if (!(target.level() instanceof ServerLevel level) || !target.isSelected() ||
                !state(target).restoreRecovery(level, target.providerPos(), "adaptive", receipt, target.returnInventory()))
            return false;
        target.saveChanges();
        target.alertDevice();
        return true;
    }

    private static final class State {

        private PackagedDispatchState dispatch = new PackagedDispatchState();
    }
}
