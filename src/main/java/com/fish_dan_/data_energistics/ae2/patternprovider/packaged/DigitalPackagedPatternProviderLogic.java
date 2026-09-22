package com.fish_dan_.data_energistics.ae2.patternprovider.packaged;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingCustodyCensus;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.AppendReceipt;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedDispatchState;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.TargetedCountedCraftingProvider;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CountedCraftingPreparation;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchRejection;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTargetAvailability;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.BoundPatternInputProvider;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;
import com.fish_dan_.data_energistics.mixin.ae.ae2.accessor.PatternProviderLogicFieldAccessor;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.KeyCounter;
import appeng.core.settings.TickRates;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adjacent-only 36-slot host for the shared real-machine dispatcher. */
public final class DigitalPackagedPatternProviderLogic extends PatternProviderLogic implements IGridTickable, BoundPatternInputProvider, ReusableCraftingProviderAdapter, TargetedCountedCraftingProvider {

    private final IManagedGridNode node;
    private final PatternProviderLogicHost owner;
    private PackagedDispatchState dispatch = new PackagedDispatchState();
    private final UUID unavailableCustodyEpoch = UUID.randomUUID();

    private @Nullable ReusableCraftingProviderAdapter reusableAdapter() {
        if (!(owner.getBlockEntity().getLevel() instanceof ServerLevel level)) return null;
        var access = (PatternProviderLogicFieldAccessor) (Object) this;
        var links = new ObjectArrayList<ConnectorLink>();
        for (var side : Direction.values()) links.add(new ConnectorLink(owner.getBlockEntity().getBlockPos().relative(side), side.getOpposite()));
        return dispatch.reusable().adapter(level, links,
                pattern -> node.isActive() && !owner.getBlockEntity().isRemoved() && !isBusy() && getCraftingLockedReason() == LockCraftingMode.NONE && access.dataEnergistics$getPatterns().contains(pattern),
                this::onReturnInventoryChanged, access::dataEnergistics$invokeOnPushPatternSuccess);
    }

    @Override
    public @Nullable CountedCraftingAdmission prepareBatch(IPatternDetails pattern, KeyCounter[] prototype, long count) {
        if (!(owner.getBlockEntity().getLevel() instanceof ServerLevel level)) return null;
        var access = (PatternProviderLogicFieldAccessor) (Object) this;
        var adjacent = new ObjectArrayList<ConnectorLink>();
        for (var side : Direction.values()) adjacent.add(new ConnectorLink(owner.getBlockEntity().getBlockPos().relative(side), side.getOpposite()));
        var lock = getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE);
        long bounded = lock == LockCraftingMode.LOCK_UNTIL_RESULT || lock == LockCraftingMode.LOCK_UNTIL_PULSE ? 1 : count;
        return dispatch.prepareBatch(level, DataEnergisticsEntrypointLoader.snapshot().packagedCrafting(), pattern, prototype, bounded,
                ObjectList.of(), adjacent, null,
                () -> node.isActive() && !owner.getBlockEntity().isRemoved() && !isBusy() &&
                        getCraftingLockedReason() == LockCraftingMode.NONE && access.dataEnergistics$getPatterns().contains(pattern),
                () -> {
                    access.dataEnergistics$invokeOnPushPatternSuccess(pattern);
                    onReturnInventoryChanged();
                });
    }

    @Override
    public CountedCraftingPreparation prepareBatch(IPatternDetails pattern, KeyCounter[] prototype, long count,
                                                   CraftingDispatchTargetAvailability availability) {
        var target = CraftingDispatchTarget.provider();
        var admission = availability.canAttempt(target) ? prepareBatch(pattern, prototype, count) : null;
        return admission == null ? CountedCraftingPreparation.rejected(CraftingDispatchRejection.targeted(CraftingDispatchStatus.NO_CAPACITY, target)) :
                CountedCraftingPreparation.accepted(admission, target);
    }

    @Override
    public @Nullable CountedCraftingAdmission prepareBatchForTarget(IPatternDetails pattern, KeyCounter[] prototype,
                                                                    long count, CraftingDispatchTarget target) {
        return target.equals(CraftingDispatchTarget.provider()) ? prepareBatch(pattern, prototype, count) : null;
    }

    @Override
    public ObjectList<ProviderCapacitySnapshot> snapshotCapacity(CraftingProviderId providerId, IPatternDetails pattern,
                                                                 KeyCounter[] prototype, long count, String patternIdentity,
                                                                 long publicationRevision, long capacityRevision, long captureTick) {
        var admission = prepareBatch(pattern, prototype, count);
        long capacity = admission == null ? 0 : admission.count();
        return ObjectList.of(new ProviderCapacitySnapshot(providerId, CraftingDispatchTarget.provider(), Optional.empty(),
                patternIdentity, publicationRevision, capacityRevision, captureTick, ProviderRoutingMode.AGGREGATE,
                new DispatchCapacity.Known(capacity), new DispatchCapacity.Known(capacity)));
    }

    @Override
    public ObjectList<Target> reusableTargetsFast(IPatternDetails pattern, IActionSource source, ServerLevel level) {
        var adapter = reusableAdapter();
        return adapter == null ? ObjectList.of() : adapter.reusableTargetsFast(pattern, source, level);
    }

    @Override
    public @Nullable ReusableCraftingAdmission prepareReusable(ReusableCraftingRequest request) {
        var adapter = reusableAdapter();
        return adapter == null ? null : adapter.prepareReusable(request);
    }

    @Override
    public Optional<ReusableCraftingSessionView> reusableSession(UUID id) {
        var adapter = reusableAdapter();
        return adapter == null ? Optional.empty() : adapter.reusableSession(id);
    }

    @Override
    public ReusableCraftingCustodyCensus reusableCustody(String cpuOwner) {
        var adapter = reusableAdapter();
        return adapter == null ? new ReusableCraftingCustodyCensus(unavailableCustodyEpoch, 0, false, ObjectList.of()) : adapter.reusableCustody(cpuOwner);
    }

    @Override
    public Optional<AppendReceipt> reusableReceipt(UUID id, long sequence) {
        var adapter = reusableAdapter();
        return adapter == null ? Optional.empty() : adapter.reusableReceipt(id, sequence);
    }

    @Override
    public void closeReusableSession(UUID id) {
        var adapter = reusableAdapter();
        if (adapter != null) adapter.closeReusableSession(id);
    }

    @Override
    public boolean requestReusableYield(ReusableCraftingRequest request) {
        var adapter = reusableAdapter();
        return adapter != null && adapter.requestReusableYield(request);
    }

    @Override
    public boolean settleReusableSession(UUID id, ReturnReceiver receiver) {
        var adapter = reusableAdapter();
        return adapter != null && adapter.settleReusableSession(id, receiver);
    }

    public DigitalPackagedPatternProviderLogic(IManagedGridNode node, PatternProviderLogicHost owner) {
        super(node, owner, 36);
        this.node = node;
        this.owner = owner;
        this.returnInv = new PackagedReturnInventory(this::onReturnInventoryChanged);
        node.addService(IGridTickable.class, this);
    }

    private void onReturnInventoryChanged() {
        this.owner.saveChanges();
        this.node.ifPresent((grid, gridNode) -> grid.getTickManager().alertDevice(gridNode));
    }

    public PackagedDispatchState dispatchState() {
        return this.dispatch;
    }

    @Override
    public void addDrops(List<ItemStack> drops) {
        if (this.owner.getBlockEntity().getLevel() instanceof ServerLevel level) {
            var receipt = this.dispatch.prepareRecoveryDrop(level, this.owner.getBlockEntity().getBlockPos(), "standalone", getReturnInv());
            if (!receipt.isEmpty()) {
                drops.add(receipt);
                this.owner.saveChanges();
            }
        }
        super.addDrops(drops);
    }

    @Override
    public void clearContent() {
        this.dispatch.ensureCanClear();
        super.clearContent();
        this.dispatch = new PackagedDispatchState();
    }

    /** Restores a dismantled provider receipt on the server thread, then wakes actual machine processing. */
    public boolean restoreRecoveryItem(ItemStack receipt) {
        if (!(this.owner.getBlockEntity().getLevel() instanceof ServerLevel level) ||
                !this.dispatch.restoreRecovery(level, this.owner.getBlockEntity().getBlockPos(), "standalone", receipt, getReturnInv()))
            return false;
        onReturnInventoryChanged();
        return true;
    }

    @Override
    public CountedCraftingPreparation prepareBoundInputBatch(IPatternDetails patternDetails,
                                                             IPatternDetails extractionDetails, KeyCounter[] prototype, long requestedCount,
                                                             CraftingDispatchTargetAvailability targetAvailability) {
        return prepareBatch(patternDetails, prototype, requestedCount, targetAvailability);
    }

    @Override
    public @Nullable CountedCraftingAdmission prepareBoundInputBatchForTarget(IPatternDetails patternDetails,
                                                                              IPatternDetails extractionDetails, KeyCounter[] prototype, long requestedCount,
                                                                              CraftingDispatchTarget target) {
        return prepareBatchForTarget(patternDetails, prototype, requestedCount, target);
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        var access = (PatternProviderLogicFieldAccessor) (Object) this;
        if (!this.node.isActive() || isBusy() || getCraftingLockedReason() != LockCraftingMode.NONE ||
                !access.dataEnergistics$getPatterns().contains(pattern) ||
                !(this.owner.getBlockEntity().getLevel() instanceof ServerLevel level))
            return false;
        var adjacent = new ObjectArrayList<ConnectorLink>();
        for (var side : Direction.values()) {
            adjacent.add(new ConnectorLink(this.owner.getBlockEntity().getBlockPos().relative(side), side.getOpposite()));
        }
        if (!this.dispatch.dispatch(level, DataEnergisticsEntrypointLoader.snapshot().packagedCrafting(),
                pattern, inputs, ObjectList.of(), adjacent))
            return false;
        access.dataEnergistics$invokeOnPushPatternSuccess(pattern);
        this.owner.saveChanges();
        this.node.ifPresent((grid, gridNode) -> grid.getTickManager().alertDevice(gridNode));
        return true;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.Interface, !hasWork());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!this.node.isActive() || !(this.owner.getBlockEntity().getLevel() instanceof ServerLevel level)) return TickRateModulation.SLEEP;
        var access = (PatternProviderLogicFieldAccessor) (Object) this;
        boolean worked = this.dispatch.tick(level, DataEnergisticsEntrypointLoader.snapshot().packagedCrafting(),
                getReturnInv(), access.dataEnergistics$getActionSource());
        worked |= access.dataEnergistics$invokeDoWork();
        if (worked) this.owner.saveChanges();
        return hasWork() ? worked ? TickRateModulation.URGENT : TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    private boolean hasWork() {
        return this.dispatch.hasWork() || ((PatternProviderLogicFieldAccessor) (Object) this).dataEnergistics$invokeHasWorkToDo();
    }

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        var state = new CompoundTag();
        this.dispatch.save(state, registries);
        tag.put("packaged", state);
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);
        this.dispatch = PackagedDispatchState.load(tag.getCompound("packaged"), registries);
    }
}
