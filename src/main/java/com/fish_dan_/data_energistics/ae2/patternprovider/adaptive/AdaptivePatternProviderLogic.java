package com.fish_dan_.data_energistics.ae2.patternprovider.adaptive;

import com.fish_dan_.data_energistics.accessor.patternprovider.PatternProviderBatchAccess;
import com.fish_dan_.data_energistics.accessor.patternprovider.PatternProviderBatchBridge;
import com.fish_dan_.data_energistics.accessor.patternprovider.PatternProviderLogicAccessor;
import com.fish_dan_.data_energistics.accessor.patternprovider.RedstoneTuningAwareHost;
import com.fish_dan_.data_energistics.ae2.patternprovider.PatternProviderBatching;
import com.fish_dan_.data_energistics.ae2.patternprovider.RedstoneTuningAutoRequestHelper;
import com.fish_dan_.data_energistics.ae2.patternprovider.RedstoneTuningMode;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.reusable.AdaptiveReusableCraftingState;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingCustodyCensus;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.AppendReceipt;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchContext;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchTarget;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderProfile;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorMode;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorPolicy;
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
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.NativeReusableCrafting;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Binding;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Host;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.NativeResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Identity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Operation;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;
import com.fish_dan_.data_energistics.common.entrypoint.machine.CraftingMachineCapacityAdapters;
import com.fish_dan_.data_energistics.common.recipe.RecipeReloadEpoch;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingWatcherNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.core.definitions.AEItems;
import appeng.core.settings.TickRates;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.helpers.MachineSource;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class AdaptivePatternProviderLogic extends PatternProviderLogic
                                          implements PatternProviderLogicAccessor, TargetedCountedCraftingProvider, BoundPatternInputProvider, ReusableCraftingProviderAdapter {

    private static final int EXPANDED_RETURN_SLOTS = 18;
    private static final int CONNECTOR_PULL_KEYS_PER_TICK = 32;
    private static final long CONNECTOR_PULL_AMOUNT_PER_KEY = 4096L;
    private static final String NBT_PATTERN_SLOT_OVERFLOW = "adaptive_pattern_slot_overflow";
    private static final String NBT_RECONCILED_PATTERN_SLOT_COUNT = "adaptive_reconciled_pattern_slot_count";
    private static final String NBT_CONNECTOR_MODE = "connector_mode";
    private static final String NBT_CONNECTOR_POLICY = "connector_policy";
    private static final String NBT_CONNECTOR_CURSOR = "connector_cursor";
    private static final String NBT_CONNECTOR_PULL_CURSOR = "connector_pull_cursor";
    private static final String NBT_CONNECTOR_PULL_SLOT_CURSOR = "connector_pull_slot_cursor";
    private static final String NBT_CONNECTOR_TARGETS = "connector_targets";

    private final PatternProviderLogicHost host;
    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;
    private int localRoundRobinIndex;
    private ConnectorMode connectorMode = ConnectorMode.INPUT;
    private ConnectorPolicy connectorPolicy = ConnectorPolicy.ROUND_ROBIN;
    private int connectorCursor;
    private int connectorPullCursor;
    private int connectorPullSlotCursor;
    private final ObjectArrayList<ConnectorTarget> connectorTargets = new ObjectArrayList<>();
    private final ObjectArrayList<ItemStack> patternSlotOverflow = new ObjectArrayList<>();
    private final ObjectSet<AEKey> trackedCrafts = new ObjectOpenHashSet<>();
    private final ObjectSet<AEKey> outputCache = new ObjectOpenHashSet<>();
    private @Nullable IStackWatcher craftingWatcher;
    private int reusableWorkCount;
    private AdaptiveReusableCraftingState reusableCrafting = new AdaptiveReusableCraftingState();
    private @Nullable CompoundTag reusableItemHandoff;
    private final Int2ObjectLinkedOpenHashMap<NativePatternSlot> nativePatternSlots = new Int2ObjectLinkedOpenHashMap<>();
    private long nativeRecipeEpoch = RecipeReloadEpoch.current();
    private boolean dataEnergistics$dispatchPulsePending;
    private int reconciledPatternSlotCount = -1;
    private int suppressedPatternInventoryCallbacks;
    private boolean patternInventoryChangedWhileCallbacksSuppressed;
    private static final String NBT_DISPATCH_STATES = "adaptive_dispatch_states";
    private final Object2ObjectOpenHashMap<ResourceLocation, AdaptivePatternProviderRuntimeTarget> dispatchTargets = new Object2ObjectOpenHashMap<>();
    private CompoundTag unloadedDispatchStates = new CompoundTag();

    public AdaptivePatternProviderLogic(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize) {
        super(mainNode, host, patternInventorySize);
        this.host = host;
        this.mainNode = mainNode
                .addService(IGridTickable.class, new Ticker())
                .addService(ICraftingWatcherNode.class, new AdaptiveCraftingWatcherNode());
        this.actionSource = new MachineSource(mainNode::getNode);
        installExpandedReturnInventory();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (this.suppressedPatternInventoryCallbacks > 0) {
            this.patternInventoryChangedWhileCallbacksSuppressed = true;
            return;
        }

        super.onChangeInventory(inv, slot);
        refreshAdaptivePatternTracking();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        if (this.suppressedPatternInventoryCallbacks > 0) {
            return;
        }

        super.saveChangedInventory(inv);
    }

    @Override
    public void updatePatterns() {
        rebuildPatternsForConfiguredSlots();
        refreshAdaptivePatternTracking();
        var target = activeDispatchTarget();
        if (target != null) {
            target.dispatch().onPatternsUpdated(target);
        }
    }

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        writePatternSlotOverflowToNBT(tag, registries);
        tag.put(AdaptiveReusableCraftingState.NBT_KEY, this.reusableCrafting.writeToTag(registries));
        tag.putString(NBT_CONNECTOR_MODE, this.connectorMode.name());
        tag.putString(NBT_CONNECTOR_POLICY, this.connectorPolicy.name());
        tag.putInt(NBT_CONNECTOR_CURSOR, this.connectorCursor);
        tag.putInt(NBT_CONNECTOR_PULL_CURSOR, this.connectorPullCursor);
        tag.putInt(NBT_CONNECTOR_PULL_SLOT_CURSOR, this.connectorPullSlotCursor);
        ListTag connectorTargetTags = new ListTag();
        for (ConnectorTarget target : this.connectorTargets) {
            CompoundTag targetTag = new CompoundTag();
            targetTag.putLong("pos", target.position().asLong());
            targetTag.putByte("side", (byte) target.side().get3DDataValue());
            targetTag.putString("mode", target.mode().name());
            connectorTargetTags.add(targetTag);
        }
        tag.put(NBT_CONNECTOR_TARGETS, connectorTargetTags);
        activeDispatchTarget();
        CompoundTag states = this.unloadedDispatchStates.copy();
        for (var target : this.dispatchTargets.values()) {
            CompoundTag state = new CompoundTag();
            target.dispatch().writeState(target, state, registries);
            if (!state.isEmpty()) {
                states.put(target.registrationId().toString(), state);
            }
        }
        tag.put(NBT_DISPATCH_STATES, states);
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains(NBT_DISPATCH_STATES) && !(tag.get(NBT_DISPATCH_STATES) instanceof CompoundTag)) {
            throw new IllegalArgumentException("Adaptive dispatch states must be a compound");
        }
        AdaptiveReusableCraftingState restoredReusable;
        if (tag.contains(AdaptiveReusableCraftingState.NBT_KEY)) {
            if (!(tag.get(AdaptiveReusableCraftingState.NBT_KEY) instanceof CompoundTag reusableTag)) {
                throw new IllegalArgumentException("Adaptive reusable state must be a compound");
            }
            restoredReusable = AdaptiveReusableCraftingState.readFromTag(reusableTag, registries);
        } else {
            restoredReusable = new AdaptiveReusableCraftingState();
        }
        super.readFromNBT(tag, registries);

        this.connectorMode = readConnectorMode(tag, NBT_CONNECTOR_MODE, ConnectorMode.INPUT);
        this.connectorPolicy = readConnectorPolicy(tag, NBT_CONNECTOR_POLICY, ConnectorPolicy.ROUND_ROBIN);
        this.connectorCursor = Math.max(0, readLegacyInt(tag, NBT_CONNECTOR_CURSOR));
        this.connectorPullCursor = Math.max(0, readLegacyInt(tag, NBT_CONNECTOR_PULL_CURSOR));
        this.connectorPullSlotCursor = Math.max(0, readLegacyInt(tag, NBT_CONNECTOR_PULL_SLOT_CURSOR));
        this.connectorTargets.clear();
        ListTag connectorTargetTags = tag.contains(NBT_CONNECTOR_TARGETS, Tag.TAG_LIST) ? tag.getList(NBT_CONNECTOR_TARGETS, Tag.TAG_COMPOUND) : tag.getList("adaptive_" + NBT_CONNECTOR_TARGETS, Tag.TAG_COMPOUND);
        for (int index = 0; this.host instanceof BlockEntity && index < connectorTargetTags.size(); index++) {
            CompoundTag targetTag = connectorTargetTags.getCompound(index);
            int side = targetTag.getByte("side");
            if (side >= 0 && side < 6) {
                this.connectorTargets.add(new ConnectorTarget(
                        BlockPos.of(targetTag.getLong("pos")), Direction.from3DDataValue(side),
                        readConnectorMode(targetTag, "mode", this.connectorMode)));
            }
        }

        readPatternSlotOverflowFromNBT(tag, registries);
        this.reusableCrafting = restoredReusable;
        this.reusableItemHandoff = null;
        this.dispatchTargets.clear();
        this.unloadedDispatchStates = tag.getCompound(NBT_DISPATCH_STATES).copy();
        ObjectSet<String> legacyKeys = new ObjectOpenHashSet<>();
        AdaptivePatternProviderRegistration selected = resolvedRegistration();
        if (selected != null) {
            restoreDispatchState(selected, tag, registries, legacyKeys);
        }
        for (var registration : AdaptivePatternProviderResolver.registrations()) {
            if (registration != selected) {
                restoreDispatchState(registration, tag, registries, legacyKeys);
            }
        }
    }

    public ConnectorMode connectorMode() {
        return this.connectorMode;
    }

    public ConnectorPolicy connectorPolicy() {
        return this.connectorPolicy;
    }

    public int connectorCursor() {
        return this.connectorCursor;
    }

    public void setConnectorMode(ConnectorMode mode) {
        this.connectorMode = mode;
        onConnectorChanged();
    }

    public void setConnectorPolicy(ConnectorPolicy policy) {
        this.connectorPolicy = policy;
        onConnectorChanged();
    }

    public void advanceConnectorCursor(int routeCount) {
        if (routeCount > 0) {
            this.connectorCursor = Math.floorMod(this.connectorCursor + 1, routeCount);
        }
    }

    public List<ConnectorTarget> connectorTargets() {
        return List.copyOf(this.connectorTargets);
    }

    /** Writes only connector display data into the host's normal client update, in binding order. */
    public void writeConnectorVisualState(RegistryFriendlyByteBuf data) {
        data.writeEnum(this.connectorMode);
        data.writeEnum(this.connectorPolicy);
        data.writeCollection(this.connectorTargets, (buffer, target) -> {
            buffer.writeBlockPos(target.position());
            buffer.writeEnum(target.side());
            buffer.writeEnum(target.mode());
        });
    }

    /** Replaces the client display state after a complete update has been decoded; performs no world writes. */
    public boolean readConnectorVisualState(RegistryFriendlyByteBuf data) {
        ConnectorMode mode = data.readEnum(ConnectorMode.class);
        ConnectorPolicy policy = data.readEnum(ConnectorPolicy.class);
        List<ConnectorTarget> targets = data.readList(buffer -> new ConnectorTarget(
                buffer.readBlockPos(), buffer.readEnum(Direction.class), buffer.readEnum(ConnectorMode.class)));
        boolean changed = this.connectorMode != mode || this.connectorPolicy != policy || !this.connectorTargets.equals(targets);
        this.connectorMode = mode;
        this.connectorPolicy = policy;
        this.connectorTargets.clear();
        this.connectorTargets.addAll(targets);
        return changed;
    }

    private void onConnectorChanged() {
        this.host.saveChanges();
        if (this.host instanceof AdaptivePatternProviderHost adaptiveHost) {
            adaptiveHost.markForClientUpdate();
        }
        adaptiveAlertDevice();
    }

    public BlockPos hostPosition() {
        return this.host.getBlockEntity().getBlockPos();
    }

    public boolean hasConnectorBindings() {
        return !this.connectorTargets.isEmpty();
    }

    private boolean hasInputConnectorTargets() {
        return this.connectorTargets.stream().anyMatch(
                target -> target.mode().supportsInput());
    }

    private boolean hasPullConnectorTargets() {
        return this.connectorTargets.stream().anyMatch(
                target -> target.mode().supportsPull());
    }

    public boolean bindConnectorTarget(BlockPos position, Direction side) {
        if (!(this.host instanceof BlockEntity)) {
            return false;
        }
        if (this.connectorTargets.stream().anyMatch(target -> target.position().equals(position) && target.side() == side)) {
            return false;
        }
        ConnectorTarget candidate = new ConnectorTarget(position, side, this.connectorMode);
        this.connectorTargets.add(candidate);
        onConnectorChanged();
        return true;
    }

    public boolean unbindConnectorTarget(BlockPos position, Direction side) {
        boolean removed = this.connectorTargets.removeIf(target -> target.position().equals(position) && target.side() == side);
        if (removed) {
            if (this.connectorTargets.isEmpty()) {
                this.connectorCursor = 0;
                this.connectorPullCursor = 0;
                this.connectorPullSlotCursor = 0;
            } else {
                this.connectorCursor = Math.floorMod(this.connectorCursor, this.connectorTargets.size());
                this.connectorPullCursor = Math.floorMod(this.connectorPullCursor, this.connectorTargets.size());
            }
            onConnectorChanged();
        }
        return removed;
    }

    /** Replaces all links from a validated clipboard while preserving their independent transfer modes. */
    public int replaceConnectorTargets(List<ConnectorLink> bindings) {
        if (!(this.host instanceof BlockEntity)) {
            return 0;
        }
        this.connectorTargets.clear();
        ObjectOpenHashSet<String> identities = new ObjectOpenHashSet<>();
        for (ConnectorLink binding : bindings) {
            String identity = binding.position().asLong() + ":" + binding.side().get3DDataValue();
            if (identities.add(identity)) {
                this.connectorTargets.add(new ConnectorTarget(binding.position(), binding.side(), binding.mode()));
            }
        }
        normalizeConnectorCursors();
        onConnectorChanged();
        return this.connectorTargets.size();
    }

    /** Clears every provider-owned link without changing the connector's current default mode. */
    public void clearConnectorTargets() {
        if (this.connectorTargets.isEmpty()) {
            return;
        }
        this.connectorTargets.clear();
        this.connectorCursor = 0;
        this.connectorPullCursor = 0;
        this.connectorPullSlotCursor = 0;
        onConnectorChanged();
    }

    private void normalizeConnectorCursors() {
        if (this.connectorTargets.isEmpty()) {
            this.connectorCursor = 0;
            this.connectorPullCursor = 0;
            this.connectorPullSlotCursor = 0;
        } else {
            this.connectorCursor = Math.floorMod(this.connectorCursor, this.connectorTargets.size());
            this.connectorPullCursor = Math.floorMod(this.connectorPullCursor, this.connectorTargets.size());
        }
    }

    public boolean hasConnectorTarget(BlockPos position, Direction side) {
        return this.connectorTargets.stream().anyMatch(target -> target.position().equals(position) && target.side() == side);
    }

    private static ConnectorMode readConnectorMode(
                                                   CompoundTag tag, String key, ConnectorMode fallback) {
        String readKey = tag.contains(key) ? key : "adaptive_" + key;
        if (!tag.contains(readKey)) {
            return fallback;
        }
        try {
            return ConnectorMode.valueOf(tag.getString(readKey));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid adaptive connector mode", exception);
        }
    }

    private static ConnectorPolicy readConnectorPolicy(
                                                       CompoundTag tag, String key, ConnectorPolicy fallback) {
        String readKey = tag.contains(key) ? key : "adaptive_" + key;
        if (!tag.contains(readKey)) {
            return fallback;
        }
        try {
            return ConnectorPolicy.valueOf(tag.getString(readKey));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid adaptive connector policy", exception);
        }
    }

    private static int readLegacyInt(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) : tag.getInt("adaptive_" + key);
    }

    public record ConnectorTarget(BlockPos position, Direction side, ConnectorMode mode) {}

    /** Dismantled physical items carry escrow independently from copyable MemoryCard settings. */
    public void exportReusableItem(DataComponentMap.Builder builder, HolderLookup.Provider registries) {
        CompoundTag handoff = this.reusableItemHandoff;
        if (handoff == null) {
            if (this.reusableCrafting.handoffPrepared()) {
                throw new IllegalStateException("Persisted adaptive item handoff is unresolved; refusing a second asset copy");
            }
            handoff = this.reusableCrafting.prepareItemHandoff(registries);
            this.reusableItemHandoff = handoff;
            this.host.saveChanges();
        }
        CustomData previous = builder.build().get(DataComponents.CUSTOM_DATA);
        CompoundTag custom = previous == null ? new CompoundTag() : previous.copyTag();
        custom.put(AdaptiveReusableCraftingState.NBT_KEY, handoff.copy());
        builder.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }

    public void importReusableItem(DataComponentMap components, HolderLookup.Provider registries) {
        CustomData custom = components.get(DataComponents.CUSTOM_DATA);
        if (custom == null || !custom.contains(AdaptiveReusableCraftingState.NBT_KEY)) {
            return;
        }
        CompoundTag source = custom.copyTag();
        if (!(source.get(AdaptiveReusableCraftingState.NBT_KEY) instanceof CompoundTag payload)) {
            throw new IllegalArgumentException("Adaptive reusable item payload must be a compound");
        }
        AdaptiveReusableCraftingState restored = AdaptiveReusableCraftingState.readFromTag(payload, registries);
        if (this.reusableCrafting.hasResidents()) {
            throw new IllegalStateException("Cannot replace live adaptive reusable ownership during item placement");
        }
        this.reusableCrafting = restored;
        this.reusableItemHandoff = null;
        onHostStateChanged();
        this.host.saveChanges();
    }

    /**
     * Reconciles the backing pattern inventory with the host's current visible slot count.
     *
     * @return whether the visible boundary or stored pattern state changed
     */
    public boolean reconcileConfiguredPatternSlots() {
        return reconcileConfiguredPatternSlots(false);
    }

    /**
     * Runs an imported pattern update without publishing every intermediate inventory state.
     */
    public boolean runWithPatternInventoryCallbacksSuppressed(Runnable action) {
        boolean outermostSuppression = this.suppressedPatternInventoryCallbacks == 0;
        if (outermostSuppression) {
            this.patternInventoryChangedWhileCallbacksSuppressed = false;
        }

        boolean changed = false;
        this.suppressedPatternInventoryCallbacks++;
        try {
            action.run();
        } finally {
            this.suppressedPatternInventoryCallbacks--;
            if (outermostSuppression) {
                changed = this.patternInventoryChangedWhileCallbacksSuppressed;
                this.patternInventoryChangedWhileCallbacksSuppressed = false;
            }
        }
        return changed;
    }

    /**
     * Reconciles an imported pattern inventory even if its configured slot count did not change.
     *
     * @return whether the visible boundary or stored pattern state changed
     */
    public boolean reconcileConfiguredPatternSlotsAfterSettingsImport() {
        return reconcileConfiguredPatternSlots(true);
    }

    /**
     * Gives every persisted overflow pattern to the supplied recovery path in queue order.
     * A queue entry is removed only after that path returns normally.
     *
     * @return whether an overflow stack was recovered
     */
    public boolean returnPatternSlotOverflow(Consumer<ItemStack> recoveryPath) {
        boolean recovered = false;
        while (!this.patternSlotOverflow.isEmpty()) {
            ItemStack stack = this.patternSlotOverflow.getFirst().copy();
            recoveryPath.accept(stack);
            this.patternSlotOverflow.removeFirst();
            this.host.saveChanges();
            recovered = true;
        }
        return recovered;
    }

    private boolean reconcileConfiguredPatternSlots(boolean force) {
        int configuredSlotCount = getConfiguredPatternSlotCount();
        if (!force && configuredSlotCount == this.reconciledPatternSlotCount) {
            return false;
        }

        List<ItemStack> plannedInventory = copyPatternInventory();
        List<ItemStack> plannedOverflow = copyPatternStacks(this.patternSlotOverflow);
        if (this.reconciledPatternSlotCount >= 0 && configuredSlotCount > this.reconciledPatternSlotCount) {
            restorePatternSlotOverflow(plannedInventory, plannedOverflow, this.reconciledPatternSlotCount, configuredSlotCount);
        }
        moveHiddenPatternSlotsToVisibleOrOverflow(plannedInventory, plannedOverflow, configuredSlotCount);

        boolean inventoryChanged = !matchesPatternInventory(plannedInventory);
        boolean overflowChanged = !matchesPatternStacks(this.patternSlotOverflow, plannedOverflow);
        boolean slotCountChanged = this.reconciledPatternSlotCount != configuredSlotCount;
        if (!inventoryChanged && !overflowChanged && !slotCountChanged) {
            return false;
        }

        runWithPatternInventoryCallbacksSuppressed(() -> applyPatternInventory(plannedInventory));
        this.patternSlotOverflow.clear();
        this.patternSlotOverflow.addAll(plannedOverflow);
        this.reconciledPatternSlotCount = configuredSlotCount;
        updatePatterns();
        return true;
    }

    private int getConfiguredPatternSlotCount() {
        if (this.host instanceof AdaptivePatternProviderHost adaptiveHost) {
            return Math.max(0, Math.min(adaptiveHost.getPatternSlotCountForMenu(), this.patternInventory.size()));
        }
        return this.patternInventory.size();
    }

    private List<ItemStack> copyPatternInventory() {
        ObjectArrayList<ItemStack> copiedInventory = new ObjectArrayList<>(this.patternInventory.size());
        for (int slot = 0; slot < this.patternInventory.size(); slot++) {
            copiedInventory.add(this.patternInventory.getStackInSlot(slot).copy());
        }
        return copiedInventory;
    }

    private static List<ItemStack> copyPatternStacks(List<ItemStack> stacks) {
        ObjectArrayList<ItemStack> copiedStacks = new ObjectArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copiedStacks.add(stack.copy());
        }
        return copiedStacks;
    }

    private void restorePatternSlotOverflow(List<ItemStack> plannedInventory, List<ItemStack> plannedOverflow, int previousSlotCount, int configuredSlotCount) {
        int restoredCount = 0;
        for (int slot = previousSlotCount; slot < configuredSlotCount && restoredCount < plannedOverflow.size(); slot++) {
            if (plannedInventory.get(slot).isEmpty()) {
                plannedInventory.set(slot, plannedOverflow.get(restoredCount));
                restoredCount++;
            }
        }
        if (restoredCount > 0) {
            plannedOverflow.subList(0, restoredCount).clear();
        }
    }

    private static void moveHiddenPatternSlotsToVisibleOrOverflow(List<ItemStack> plannedInventory, List<ItemStack> plannedOverflow, int configuredSlotCount) {
        ObjectArrayList<ItemStack> hiddenPatterns = new ObjectArrayList<>();
        for (int slot = configuredSlotCount; slot < plannedInventory.size(); slot++) {
            ItemStack stack = plannedInventory.get(slot);
            if (!stack.isEmpty()) {
                hiddenPatterns.add(stack);
                plannedInventory.set(slot, ItemStack.EMPTY);
            }
        }

        int movedCount = 0;
        for (int slot = 0; slot < configuredSlotCount && movedCount < hiddenPatterns.size(); slot++) {
            if (plannedInventory.get(slot).isEmpty()) {
                plannedInventory.set(slot, hiddenPatterns.get(movedCount));
                movedCount++;
            }
        }
        if (movedCount < hiddenPatterns.size()) {
            plannedOverflow.addAll(0, hiddenPatterns.subList(movedCount, hiddenPatterns.size()));
        }
    }

    private boolean matchesPatternInventory(List<ItemStack> plannedInventory) {
        for (int slot = 0; slot < this.patternInventory.size(); slot++) {
            if (!ItemStack.matches(this.patternInventory.getStackInSlot(slot), plannedInventory.get(slot))) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesPatternStacks(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!ItemStack.matches(first.get(index), second.get(index))) {
                return false;
            }
        }
        return true;
    }

    private void applyPatternInventory(List<ItemStack> plannedInventory) {
        for (int slot = 0; slot < this.patternInventory.size(); slot++) {
            ItemStack plannedStack = plannedInventory.get(slot);
            if (!ItemStack.matches(this.patternInventory.getStackInSlot(slot), plannedStack)) {
                this.patternInventory.setItemDirect(slot, plannedStack);
            }
        }
    }

    private void writePatternSlotOverflowToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag overflowTag = new ListTag();
        for (ItemStack stack : this.patternSlotOverflow) {
            overflowTag.add(stack.saveOptional(registries));
        }
        tag.put(NBT_PATTERN_SLOT_OVERFLOW, overflowTag);
        tag.putInt(NBT_RECONCILED_PATTERN_SLOT_COUNT, this.reconciledPatternSlotCount);
    }

    private void readPatternSlotOverflowFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        this.patternSlotOverflow.clear();
        ListTag overflowTag = tag.getList(NBT_PATTERN_SLOT_OVERFLOW, Tag.TAG_COMPOUND);
        for (int index = 0; index < overflowTag.size(); index++) {
            ItemStack stack = ItemStack.parseOptional(registries, overflowTag.getCompound(index));
            if (!stack.isEmpty()) {
                this.patternSlotOverflow.add(stack);
            }
        }
        this.reconciledPatternSlotCount = tag.contains(NBT_RECONCILED_PATTERN_SLOT_COUNT, Tag.TAG_INT) ? tag.getInt(NBT_RECONCILED_PATTERN_SLOT_COUNT) : -1;
    }

    @Override
    @Nullable
    public CountedCraftingAdmission prepareBatch(
                                                 IPatternDetails patternDetails,
                                                 KeyCounter[] prototype,
                                                 long requestedCount) {
        return prepareBatch(
                patternDetails,
                prototype,
                requestedCount,
                CraftingDispatchTargetAvailability.all()).admission();
    }

    @Override
    public CountedCraftingPreparation prepareBatch(
                                                   IPatternDetails patternDetails,
                                                   KeyCounter[] prototype,
                                                   long requestedCount,
                                                   CraftingDispatchTargetAvailability targetAvailability) {
        if (targetAvailability == null) {
            throw new IllegalArgumentException("Crafting dispatch target availability must not be null");
        }
        if (usesSpecialBatchRoute(patternDetails)) {
            CraftingDispatchTarget target = CraftingDispatchTarget.provider();
            if (!targetAvailability.canAttempt(target)) {
                return CountedCraftingPreparation.rejected(
                        CraftingDispatchRejection.targeted(CraftingDispatchStatus.NO_CAPACITY, target));
            }
            return CountedCraftingPreparation.accepted(
                    PatternProviderBatching.prepareSingle(this, patternDetails, prototype, requestedCount),
                    target);
        }
        return ((PatternProviderBatchBridge) this).dataEnergistics$prepareStandardBatch(
                patternDetails,
                prototype,
                requestedCount,
                this::dataEnergistics$afterPushPattern,
                targetAvailability);
    }

    @Override
    public CountedCraftingPreparation prepareBoundInputBatch(
                                                             IPatternDetails patternDetails,
                                                             IPatternDetails extractionDetails,
                                                             KeyCounter[] prototype,
                                                             long requestedCount,
                                                             CraftingDispatchTargetAvailability targetAvailability) {
        if (usesSpecialBatchRoute(patternDetails)) {
            return prepareBatch(patternDetails, prototype, requestedCount, targetAvailability);
        }
        return ((PatternProviderBatchBridge) this).dataEnergistics$prepareStandardBoundInputBatch(
                patternDetails,
                extractionDetails,
                prototype,
                requestedCount,
                this::dataEnergistics$afterPushPattern,
                targetAvailability);
    }

    @Override
    public List<ProviderCapacitySnapshot> snapshotCapacity(
                                                           CraftingProviderId providerId,
                                                           IPatternDetails patternDetails,
                                                           KeyCounter[] prototype,
                                                           long requestedCrafts,
                                                           String patternIdentity,
                                                           long publicationRevision,
                                                           long capacityRevision,
                                                           long captureTick) {
        if (usesSpecialBatchRoute(patternDetails)) {
            return List.of(new ProviderCapacitySnapshot(
                    providerId,
                    CraftingDispatchTarget.provider(),
                    Optional.empty(),
                    patternIdentity,
                    publicationRevision,
                    capacityRevision,
                    captureTick,
                    ProviderRoutingMode.UNKNOWN,
                    DispatchCapacity.Unknown.INSTANCE,
                    new DispatchCapacity.Known(1L)));
        }
        return PatternProviderBatching.snapshotStandardCapacity(
                this,
                (PatternProviderBatchAccess) this,
                providerId,
                patternDetails,
                prototype,
                requestedCrafts,
                patternIdentity,
                publicationRevision,
                capacityRevision,
                captureTick);
    }

    @Override
    @Nullable
    public CountedCraftingAdmission prepareBatchForTarget(
                                                          IPatternDetails patternDetails,
                                                          KeyCounter[] prototype,
                                                          long requestedCount,
                                                          CraftingDispatchTarget target) {
        if (usesSpecialBatchRoute(patternDetails)) {
            return target.equals(CraftingDispatchTarget.provider()) ?
                    PatternProviderBatching.prepareSingle(this, patternDetails, prototype, requestedCount) :
                    null;
        }
        return PatternProviderBatching.prepareStandardBatchForTarget(
                this,
                (PatternProviderBatchAccess) this,
                patternDetails,
                prototype,
                requestedCount,
                this::dataEnergistics$afterPushPattern,
                target);
    }

    @Override
    @Nullable
    public CountedCraftingAdmission prepareBoundInputBatchForTarget(
                                                                    IPatternDetails patternDetails,
                                                                    IPatternDetails extractionDetails,
                                                                    KeyCounter[] prototype,
                                                                    long requestedCount,
                                                                    CraftingDispatchTarget target) {
        if (usesSpecialBatchRoute(patternDetails)) {
            return prepareBatchForTarget(patternDetails, prototype, requestedCount, target);
        }
        return PatternProviderBatching.prepareStandardBatchForTarget(
                this,
                (PatternProviderBatchAccess) this,
                patternDetails,
                extractionDetails,
                prototype,
                requestedCount,
                this::dataEnergistics$afterPushPattern,
                target);
    }

    private boolean usesSpecialBatchRoute(IPatternDetails patternDetails) {
        AdaptivePatternProviderRegistration registration = resolvedRegistration();
        return registration == null || registration.dispatch().usesSpecialBatchRoute(patternDetails);
    }

    @SuppressWarnings("removal")
    @Override
    public List<Target> reusableTargets(IPatternDetails pattern, IActionSource source, ServerLevel level) {
        if (!reusableNativeAvailable() || this.host.getBlockEntity().getLevel() != level || !(pattern instanceof IMolecularAssemblerSupportedPattern)) {
            return List.of();
        }
        ObjectArrayList<Target> targets = new ObjectArrayList<>();
        for (var entry : this.nativePatternSlots.int2ObjectEntrySet()) {
            if (entry.getValue().pattern().getDefinition().equals(pattern.getDefinition())) {
                String identity = this.reusableCrafting.targetIdentity(entry.getIntKey());
                targets.add(new Target(identity, CountedCraftingTarget.route(identity), Optional.of(AdaptiveReusableCraftingState.MODE)));
            }
        }
        return ObjectLists.unmodifiable(targets);
    }

    @Override
    public @Nullable ReusableCraftingAdmission prepareReusable(ReusableCraftingRequest request) {
        if (!reusableNativeAvailable() || this.host.getBlockEntity().getLevel() != request.level() ||
                !request.target().mode().equals(Optional.of(AdaptiveReusableCraftingState.MODE))) {
            return null;
        }
        int slot = -1;
        NativePatternSlot nativePattern = null;
        for (var entry : this.nativePatternSlots.int2ObjectEntrySet()) {
            if (this.reusableCrafting.targetIdentity(entry.getIntKey()).equals(request.target().persistentIdentity()) &&
                    entry.getValue().pattern().getDefinition().equals(request.pattern().getDefinition())) {
                slot = entry.getIntKey();
                nativePattern = entry.getValue();
                break;
            }
        }
        if (nativePattern == null) {
            return null;
        }
        int nativeSlot = slot;
        ResourceLocation recipe = nativePattern.recipe();
        ReusableCraftingAdmission prepared = this.reusableCrafting.prepare(nativeSlot, recipe, request,
                request.level().getGameTime(), availableReusableAdmissions(), reusableHost(nativeSlot, recipe));
        if (prepared == null) {
            return null;
        }
        return new ReusableCraftingAdmission() {

            @Override
            public long count() {
                return prepared.count();
            }

            @SuppressWarnings("removal")
            @Override
            public List<SlotStack> physicalInputs() {
                return prepared.physicalInputsFast();
            }

            @Override
            public boolean replay() {
                return prepared.replay();
            }

            @Override
            public boolean hasTransferredInputOwnership() {
                return prepared.hasTransferredInputOwnership();
            }

            @Override
            public boolean commit(KeyCounter[] delivery) {
                if (!prepared.replay() && (!reusableNativeAvailable() || prepared.count() > availableReusableAdmissions())) {
                    return false;
                }
                boolean accepted = prepared.commit(delivery);
                if (accepted && !prepared.replay()) {
                    dataEnergistics$afterPushPattern();
                }
                return accepted;
            }
        };
    }

    @Override
    public Optional<ReusableCraftingSessionView> reusableSession(UUID sessionId) {
        AdaptiveReusableCraftingState.Slot slot = this.reusableCrafting.locate(sessionId);
        return slot == null ? Optional.empty() : slot.endpoint().query(sessionId);
    }

    @Override
    public ReusableCraftingCustodyCensus reusableCustody(String cpuOwner) {
        var blockEntity = this.host.getBlockEntity();
        return this.reusableCrafting.reusableCustody(cpuOwner, !blockEntity.isRemoved() && blockEntity.getLevel() instanceof ServerLevel);
    }

    @Override
    public boolean requestReusableYield(ReusableCraftingRequest contender) {
        if (!reusableNativeAvailable() || this.host.getBlockEntity().getLevel() != contender.level() ||
                !contender.target().mode().equals(Optional.of(AdaptiveReusableCraftingState.MODE))) {
            return false;
        }
        for (var entry : this.nativePatternSlots.int2ObjectEntrySet()) {
            int index = entry.getIntKey();
            if (this.reusableCrafting.targetIdentity(index).equals(contender.target().persistentIdentity())) {
                AdaptiveReusableCraftingState.Slot resident = this.reusableCrafting.slot(index);
                return resident != null && resident.endpoint().requestYield(contender, contender.level().getGameTime(),
                        reusableHost(index, entry.getValue().recipe(), ReusableHostPurpose.YIELD));
            }
        }
        return false;
    }

    @Override
    public Optional<AppendReceipt> reusableReceipt(UUID sessionId, long sequence) {
        AdaptiveReusableCraftingState.Slot slot = this.reusableCrafting.locate(sessionId);
        return slot == null ? Optional.empty() : slot.endpoint().receipt(sessionId, sequence);
    }

    @Override
    public void closeReusableSession(UUID sessionId) {
        AdaptiveReusableCraftingState.Slot slot = this.reusableCrafting.locate(sessionId);
        if (slot != null) {
            slot.endpoint().close(sessionId, reusableHost(slot.index(), slot.recipe()));
        }
    }

    @Override
    public boolean settleReusableSession(UUID sessionId, ReturnReceiver receiver) {
        AdaptiveReusableCraftingState.Slot slot = this.reusableCrafting.locate(sessionId);
        return slot != null && slot.endpoint().settle(sessionId, receiver, reusableHost(slot.index(), slot.recipe()));
    }

    private boolean reusableNativeAvailable() {
        return activeDispatchSupportsReusable() && !this.reusableCrafting.handoffPrepared() && this.mainNode.isActive() &&
                !super.isBusy() && !this.host.getBlockEntity().isRemoved();
    }

    private long availableReusableAdmissions() {
        int workLimit = activeReusableWorkLimit();
        return Math.max(0L, (long) workLimit - this.reusableWorkCount - this.reusableCrafting.pendingOperations());
    }

    private Host reusableHost(int slot, ResourceLocation recipe) {
        return reusableHost(slot, recipe, ReusableHostPurpose.EXECUTION);
    }

    private Host reusableHost(int slot, ResourceLocation recipe, ReusableHostPurpose purpose) {
        return new Host() {

            private @Nullable Binding checkedBinding;
            private @Nullable NativePatternSlot checkedPattern;
            private long batchLimit = 1L;

            @Override
            public boolean isAvailable(Binding binding) {
                NativePatternSlot current = nativePatternSlots.get(slot);
                if (!reusableNativeAvailable() || current == null || !current.recipe().equals(recipe) ||
                        !binding.identity().mode().equals(Optional.of(AdaptiveReusableCraftingState.MODE.toString())) ||
                        binding.recipeId().isPresent() && !binding.recipeId().orElseThrow().equals(recipe.toString()) ||
                        !binding.publicationIdentity().equals(current.identity())) {
                    return false;
                }
                if (checkedPattern != current || !binding.equals(checkedBinding)) {
                    var inputs = current.pattern().getInputs();
                    for (var material : binding.consumed()) {
                        if (!inputs[material.slot()].isValid(material.stack().what(), host.getBlockEntity().getLevel())) {
                            return false;
                        }
                    }
                    if (!NativeReusableCrafting.supports(current.pattern(), binding)) {
                        return false;
                    }
                    checkedPattern = current;
                    checkedBinding = binding;
                    batchLimit = NativeReusableCrafting.maximumBatch(current.pattern(), binding,
                            (ServerLevel) host.getBlockEntity().getLevel(), recipe);
                }
                return purpose == ReusableHostPurpose.YIELD || reusableWorkCount < activeReusableWorkLimit() && hasReusableEnergy();
            }

            @Override
            public long maximumBatch(Binding binding) {
                var grid = getGrid();
                if (grid == null) return 0L;
                long workLimit = Math.min(batchLimit, Math.max(0, activeReusableWorkLimit() - reusableWorkCount));
                double perWork = activeReusableEnergyPerWork();
                if (perWork <= 0.0D) {
                    return 0L;
                }
                double available = grid.getEnergyService().extractAEPower(perWork * workLimit, Actionable.SIMULATE, PowerMultiplier.ONE);
                return Math.min(workLimit, (long) ((available + 1.0e-9D) / perWork));
            }

            @Override
            public NativeResult execute(Binding binding, Operation operation) {
                NativePatternSlot current = nativePatternSlots.get(slot);
                var grid = getGrid();
                if (current == null || grid == null || !(host.getBlockEntity().getLevel() instanceof ServerLevel level) ||
                        operation.count() > activeReusableWorkLimit() - reusableWorkCount) {
                    return NativeResult.paused();
                }
                double cost = activeReusableEnergyPerWork() * operation.count();
                IEnergyService energy = grid.getEnergyService();
                double extracted = energy.extractAEPower(cost, Actionable.MODULATE, PowerMultiplier.ONE);
                if (!isReusableEnergyRequirementMet(extracted, cost)) {
                    energy.injectPower(extracted, Actionable.MODULATE);
                    return NativeResult.paused();
                }
                int count = Math.toIntExact(operation.count());
                reusableWorkCount += count;
                NativeResult result = NativeReusableCrafting.execute(current.pattern(), binding, operation, level, recipe);
                if (!result.executed()) {
                    reusableWorkCount -= count;
                    energy.injectPower(extracted, Actionable.MODULATE);
                }
                return result;
            }

            @Override
            public void acceptOutputs(Identity identity, List<GenericStack> outputs) {
                var target = activeDispatchTarget();
                if (target != null) {
                    target.dispatch().acceptReusableOutputsFast(target, new ObjectArrayList<>(outputs));
                }
            }

            @Override
            public void persistChanges() {
                host.saveChanges();
                mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
            }
        };
    }

    private boolean tickReusableCrafting() {
        if (this.reusableCrafting.handoffPrepared() || !(this.host.getBlockEntity().getLevel() instanceof ServerLevel level)) {
            return false;
        }
        if (this.nativeRecipeEpoch != RecipeReloadEpoch.current()) {
            rebuildPatternsForConfiguredSlots();
        }
        boolean worked = false;
        for (AdaptiveReusableCraftingState.Slot slot : this.reusableCrafting.slots()) {
            if (!slot.endpoint().hasResidentSession()) {
                continue;
            }
            Host host = reusableHost(slot.index(), slot.recipe());
            if (slot.closing()) {
                slot.close(host);
            }
            int budget = Math.max(0, activeReusableWorkLimit() - this.reusableWorkCount);
            worked |= slot.endpoint().tick(level.getGameTime(), budget, host) > 0;
        }
        return worked;
    }

    private record NativePatternSlot(IMolecularAssemblerSupportedPattern pattern, ResourceLocation recipe,
                                     TrinityPatternIdentity identity) {}

    private enum ReusableHostPurpose {
        EXECUTION,
        YIELD
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!this.connectorTargets.isEmpty() && !hasInputConnectorTargets()) {
            return false;
        }
        if (!this.connectorTargets.isEmpty() && hasInputConnectorTargets() && !hasConnectorCapacity(patternDetails, inputHolder)) {
            return false;
        }
        AdaptivePatternProviderRegistration registration = resolvedRegistration();
        if (registration != null) {
            AdaptivePatternProviderDispatchContext context = createDispatchContext(registration, patternDetails, inputHolder);
            if (registration.dispatch().handles(context)) {
                boolean dispatched = registration.dispatch().dispatch(context);
                if (dispatched) {
                    dataEnergistics$afterPushPattern();
                }
                return dispatched;
            }
        }

        boolean pushed = adaptivePushDefault(patternDetails, inputHolder);
        if (pushed) {
            dataEnergistics$afterPushPattern();
        }
        return pushed;
    }

    private boolean hasConnectorCapacity(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!(this.host.getBlockEntity().getLevel() instanceof ServerLevel level)) {
            return false;
        }
        boolean hasRegisteredCapacity = false;
        for (ConnectorTarget binding : this.connectorTargets) {
            if (!binding.mode().supportsInput()) {
                continue;
            }
            CraftingMachineCapacityAdapters.Observation observation = CraftingMachineCapacityAdapters.capture(
                    level,
                    binding.position(),
                    binding.side(),
                    patternDetails,
                    inputHolder,
                    1L);
            if (observation != null && observation.remainingLogicalCrafts() > 0L) {
                return true;
            }
            hasRegisteredCapacity |= observation != null;
        }
        return !hasRegisteredCapacity;
    }

    private @Nullable AdaptivePatternProviderRegistration resolvedRegistration() {
        if (!(this.host instanceof AdaptivePatternProviderHost adaptiveHost)) {
            return null;
        }
        return AdaptivePatternProviderResolver.resolveProviderRegistration(adaptiveHost.getProviderStack());
    }

    /**
     * Creates the generic runtime surface for the currently installed registration.
     * Registration resolution is intentionally the only provider-kind decision made
     * by this class; all route behavior remains in the registration callback.
     */
    private @Nullable AdaptivePatternProviderRuntimeTarget activeDispatchTarget() {
        AdaptivePatternProviderRegistration registration = resolvedRegistration();
        return registration == null ? null : runtimeTarget(registration);
    }

    private AdaptivePatternProviderRuntimeTarget runtimeTarget(AdaptivePatternProviderRegistration registration) {
        return this.dispatchTargets.computeIfAbsent(registration.registrationId(),
                ignored -> new AdaptivePatternProviderRuntimeTarget(this, registration));
    }

    private void restoreDispatchState(AdaptivePatternProviderRegistration registration, CompoundTag tag,
                                      HolderLookup.Provider registries, ObjectSet<String> legacyKeys) {
        String id = registration.registrationId().toString();
        if (this.unloadedDispatchStates.contains(id) && !(this.unloadedDispatchStates.get(id) instanceof CompoundTag)) {
            throw new IllegalArgumentException("Adaptive dispatch state must be a compound: " + id);
        }
        if (this.unloadedDispatchStates.contains(id, Tag.TAG_COMPOUND)) {
            var target = runtimeTarget(registration);
            target.dispatch().readState(target, this.unloadedDispatchStates.getCompound(id), registries);
            this.unloadedDispatchStates.remove(id);
        } else if (!tag.contains(NBT_DISPATCH_STATES)) {
            String legacyKey = registration.dispatch().legacyStateKey();
            if (legacyKey != null && tag.contains(legacyKey) && legacyKeys.add(legacyKey)) {
                var target = runtimeTarget(registration);
                target.dispatch().readState(target, tag, registries);
            }
        }
    }

    private boolean hasDispatchWork() {
        activeDispatchTarget();
        for (var target : this.dispatchTargets.values()) {
            if (target.dispatch().hasWork(target)) {
                return true;
            }
        }
        return false;
    }

    /** Pulls a bounded batch from linked generic inventories into the provider return inventory. */
    private boolean tickConnectorPull() {
        if (!hasPullConnectorTargets()) {
            return false;
        }
        if (!(this.host.getBlockEntity().getLevel() instanceof ServerLevel currentLevel)) {
            return false;
        }
        int scanned = 0;
        boolean changed = false;
        int targetCount = this.connectorTargets.size();
        int start = this.connectorPolicy == ConnectorPolicy.ROUND_ROBIN ? Math.floorMod(this.connectorPullCursor, targetCount) : 0;
        for (int offset = 0; offset < targetCount; offset++) {
            ConnectorTarget binding = this.connectorTargets.get((start + offset) % targetCount);
            if (!binding.mode().supportsPull()) {
                continue;
            }
            if (scanned >= CONNECTOR_PULL_KEYS_PER_TICK) {
                break;
            }
            BlockPos position = binding.position();
            if (!currentLevel.isLoaded(position)) {
                continue;
            }
            MEStorage storage = currentLevel.getCapability(
                    AECapabilities.ME_STORAGE,
                    position,
                    currentLevel.getBlockState(position),
                    currentLevel.getBlockEntity(position),
                    binding.side());
            if (storage != null) {
                PullResult result = pullStorage(storage, scanned);
                scanned = result.keysScanned();
                if (result.changed()) {
                    changed = true;
                    advanceConnectorPullCursor(offset + 1);
                    break;
                }
                if (scanned >= CONNECTOR_PULL_KEYS_PER_TICK) {
                    break;
                }
            }
            GenericInternalInventory source = currentLevel.getCapability(
                    AECapabilities.GENERIC_INTERNAL_INV,
                    position,
                    currentLevel.getBlockState(position),
                    currentLevel.getBlockEntity(position),
                    binding.side());
            if (source != null && source.canExtract()) {
                changed |= pullGenericInventory(source);
                if (changed) {
                    advanceConnectorPullCursor(offset + 1);
                    break;
                }
                continue;
            }
            IItemHandler itemHandler = currentLevel.getCapability(
                    Capabilities.ItemHandler.BLOCK,
                    position, currentLevel.getBlockState(position), currentLevel.getBlockEntity(position), binding.side());
            if (itemHandler != null) {
                changed |= pullItemHandler(itemHandler);
                if (changed) {
                    advanceConnectorPullCursor(offset + 1);
                    break;
                }
                continue;
            }
            IFluidHandler fluidHandler = currentLevel.getCapability(
                    Capabilities.FluidHandler.BLOCK,
                    position, currentLevel.getBlockState(position), currentLevel.getBlockEntity(position), binding.side());
            if (fluidHandler != null) {
                changed |= pullFluidHandler(fluidHandler);
                if (changed) {
                    advanceConnectorPullCursor(offset + 1);
                    break;
                }
            }
            continue;
        }
        if (changed) {
            this.host.saveChanges();
        }
        return changed;
    }

    private void advanceConnectorPullCursor(int amount) {
        if (this.connectorPolicy == ConnectorPolicy.ROUND_ROBIN && !this.connectorTargets.isEmpty()) {
            this.connectorPullCursor = Math.floorMod(this.connectorPullCursor + Math.max(1, amount), this.connectorTargets.size());
        }
    }

    private boolean pullGenericInventory(GenericInternalInventory source) {
        int size = source.size();
        if (size == 0) return false;
        int start = Math.floorMod(this.connectorPullSlotCursor, size);
        for (int offset = 0; offset < size; offset++) {
            int slot = (start + offset) % size;
            AEKey key = source.getKey(slot);
            long available = source.getAmount(slot);
            if (key == null || available <= 0L) continue;
            long requested = Math.min(available, CONNECTOR_PULL_AMOUNT_PER_KEY);
            long accepted = this.returnInv.insert(key, requested, Actionable.SIMULATE, this.actionSource);
            if (accepted <= 0L) continue;
            long extracted = source.extract(slot, key, accepted, Actionable.MODULATE);
            if (extracted <= 0L) continue;
            long inserted = this.returnInv.insert(key, extracted, Actionable.MODULATE, this.actionSource);
            if (inserted < extracted) source.insert(slot, key, extracted - inserted, Actionable.MODULATE);
            if (inserted > 0L) {
                this.connectorPullSlotCursor = (slot + 1) % size;
                return true;
            }
        }
        return false;
    }

    private boolean pullItemHandler(IItemHandler source) {
        int size = source.getSlots();
        if (size == 0) return false;
        int start = Math.floorMod(this.connectorPullSlotCursor, size);
        for (int offset = 0; offset < size; offset++) {
            int slot = (start + offset) % size;
            ItemStack available = source.getStackInSlot(slot);
            AEItemKey key = AEItemKey.of(available);
            if (key == null || available.isEmpty()) continue;
            int requested = (int) Math.min(available.getCount(), CONNECTOR_PULL_AMOUNT_PER_KEY);
            long accepted = this.returnInv.insert(key, requested, Actionable.SIMULATE, this.actionSource);
            if (accepted <= 0L) continue;
            ItemStack extracted = source.extractItem(slot, (int) accepted, false);
            if (extracted.isEmpty()) continue;
            long inserted = this.returnInv.insert(key, extracted.getCount(), Actionable.MODULATE, this.actionSource);
            if (inserted < extracted.getCount()) source.insertItem(slot, extracted.copyWithCount((int) (extracted.getCount() - inserted)), false);
            if (inserted > 0L) {
                this.connectorPullSlotCursor = (slot + 1) % size;
                return true;
            }
        }
        return false;
    }

    private boolean pullFluidHandler(IFluidHandler source) {
        int size = source.getTanks();
        if (size == 0) return false;
        int start = Math.floorMod(this.connectorPullSlotCursor, size);
        for (int offset = 0; offset < size; offset++) {
            int tank = (start + offset) % size;
            FluidStack available = source.getFluidInTank(tank);
            AEFluidKey key = AEFluidKey.of(available);
            if (key == null || available.isEmpty()) continue;
            int requested = (int) Math.min(available.getAmount(), CONNECTOR_PULL_AMOUNT_PER_KEY);
            long accepted = this.returnInv.insert(key, requested, Actionable.SIMULATE, this.actionSource);
            if (accepted <= 0L) continue;
            FluidStack extracted = source.drain(available.copyWithAmount((int) accepted), IFluidHandler.FluidAction.EXECUTE);
            if (extracted.isEmpty()) continue;
            long inserted = this.returnInv.insert(key, extracted.getAmount(), Actionable.MODULATE, this.actionSource);
            if (inserted < extracted.getAmount()) source.fill(extracted.copyWithAmount((int) (extracted.getAmount() - inserted)), IFluidHandler.FluidAction.EXECUTE);
            if (inserted > 0L) {
                this.connectorPullSlotCursor = (tank + 1) % size;
                return true;
            }
        }
        return false;
    }

    private PullResult pullStorage(MEStorage storage, int keysScanned) {
        var availableStacks = storage.getAvailableStacks();
        int availableKeyCount = availableStacks.size();
        if (availableKeyCount == 0) {
            return new PullResult(false, keysScanned);
        }
        int remainingBudget = CONNECTOR_PULL_KEYS_PER_TICK - keysScanned;
        if (remainingBudget <= 0) {
            return new PullResult(false, keysScanned);
        }
        int start = Math.floorMod(this.connectorPullSlotCursor, availableKeyCount);
        var iterator = availableStacks.iterator();
        for (int skipped = 0; skipped < start; skipped++) {
            iterator.next();
        }
        int inspected = 0;
        while (inspected < Math.min(remainingBudget, availableKeyCount)) {
            if (!iterator.hasNext()) {
                iterator = availableStacks.iterator();
            }
            var stack = iterator.next();
            inspected++;
            this.connectorPullSlotCursor = (start + inspected) % availableKeyCount;
            AEKey key = stack.getKey();
            long available = stack.getLongValue();
            if (available <= 0) {
                continue;
            }
            long requested = Math.min(available, CONNECTOR_PULL_AMOUNT_PER_KEY);
            long accepted = this.returnInv.insert(key, requested, Actionable.SIMULATE, this.actionSource);
            if (accepted <= 0) {
                continue;
            }
            long extracted = storage.extract(key, accepted, Actionable.MODULATE, this.actionSource);
            if (extracted <= 0) {
                continue;
            }
            long inserted = this.returnInv.insert(key, extracted, Actionable.MODULATE, this.actionSource);
            if (inserted < extracted) {
                storage.insert(key, extracted - inserted, Actionable.MODULATE, this.actionSource);
            }
            return new PullResult(inserted > 0, keysScanned + inspected);
        }
        return new PullResult(false, keysScanned + inspected);
    }

    private record PullResult(boolean changed, int keysScanned) {}

    private boolean activeDispatchSupportsReusable() {
        AdaptivePatternProviderRegistration registration = resolvedRegistration();
        return registration != null && registration.dispatch().supportsReusablePatterns();
    }

    private int activeReusableWorkLimit() {
        var target = activeDispatchTarget();
        return target == null ? 0 : Math.max(0, target.dispatch().reusableWorkLimit(target));
    }

    private double activeReusableEnergyPerWork() {
        var target = activeDispatchTarget();
        return target == null ? 0.0D : Math.max(0.0D, target.dispatch().reusableEnergyPerWork(target));
    }

    private boolean hasReusableEnergy() {
        var target = activeDispatchTarget();
        if (target == null) {
            return false;
        }
        double required = target.dispatch().reusableEnergyPerWork(target);
        if (required <= 0.0D) {
            return true;
        }
        IEnergyService energy = target.energyService();
        if (energy == null) {
            return false;
        }
        double extracted = energy.extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.ONE);
        return isReusableEnergyRequirementMet(extracted, required);
    }

    private static boolean isReusableEnergyRequirementMet(double extractedEnergy, double requiredEnergy) {
        return extractedEnergy + 1.0e-9D >= requiredEnergy;
    }

    private AdaptivePatternProviderDispatchContext createDispatchContext(
                                                                         AdaptivePatternProviderRegistration registration,
                                                                         IPatternDetails patternDetails,
                                                                         KeyCounter[] inputHolder) {
        AdaptivePatternProviderProfile profile = registration.definition().resolve(
                ((AdaptivePatternProviderHost) this.host).getProviderStack());
        if (profile == null) {
            throw new IllegalStateException(
                    "Adaptive pattern provider registration stopped resolving its installed stack: " + registration.registrationId());
        }
        AdaptivePatternProviderDispatchTarget target = runtimeTarget(registration);
        return new AdaptivePatternProviderDispatchContext(
                ((AdaptivePatternProviderHost) this.host).getProviderStack(),
                profile,
                patternDetails,
                inputHolder,
                target);
    }

    /* Package-private provider mechanics consumed by AdaptivePatternProviderRuntimeTarget. */

    boolean adaptivePushDefault(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (this.connectorPolicy == ConnectorPolicy.ROUND_ROBIN) {
            return super.pushPattern(patternDetails, inputHolder);
        }
        var access = (PatternProviderBatchAccess) this;
        int previousIndex = access.dataEnergistics$getRoundRobinIndex();
        access.dataEnergistics$setRoundRobinIndex(0);
        try {
            return super.pushPattern(patternDetails, inputHolder);
        } finally {
            // Priority always starts at the first target; switching back resumes the round-robin cursor.
            access.dataEnergistics$setRoundRobinIndex(previousIndex);
        }
    }

    boolean adaptiveIsBusy() {
        return super.isBusy();
    }

    boolean adaptiveIsActive() {
        return this.mainNode.isActive();
    }

    boolean adaptiveHasPattern(IPatternDetails patternDetails) {
        return getAvailablePatterns().contains(patternDetails);
    }

    boolean adaptiveIsCraftingLocked() {
        return getCraftingLockedReason() != LockCraftingMode.NONE;
    }

    @Nullable
    Level adaptiveLevel() {
        return this.host.getBlockEntity().getLevel();
    }

    BlockPos adaptiveProviderPos() {
        return this.host.getBlockEntity().getBlockPos();
    }

    List<Direction> adaptiveTargetSides() {
        if (!this.connectorTargets.isEmpty()) {
            ObjectArrayList<Direction> linkedSides = new ObjectArrayList<>();
            for (ConnectorTarget target : this.connectorTargets) {
                linkedSides.add(target.side());
            }
            return linkedSides;
        }
        return new ObjectArrayList<>(getActiveSidesFiltered());
    }

    void adaptivePatternSuccess(IPatternDetails patternDetails) {
        invokePatternSuccess(patternDetails);
    }

    IActionSource adaptiveActionSource() {
        return this.actionSource;
    }

    PatternProviderReturnInventory adaptiveReturnInventory() {
        return getReturnInv();
    }

    @Nullable
    MEStorage adaptiveNetworkStorage() {
        var grid = getGrid();
        return grid == null ? null : grid.getStorageService().getInventory();
    }

    @Nullable
    IEnergyService adaptiveEnergyService() {
        var grid = getGrid();
        return grid == null ? null : grid.getEnergyService();
    }

    boolean adaptiveIsBlocking() {
        return isBlocking();
    }

    Set<AEKey> adaptivePatternInputs() {
        return getPatternInputs();
    }

    @Nullable
    PatternProviderTarget adaptiveExternalTarget(Level level, BlockPos position, Direction side) {
        if (AdaptivePatternProviderResolver.isPatternProviderAttachment(level, position, side)) {
            return null;
        }
        return PatternProviderTarget.get(level, position, null, side, this.actionSource);
    }

    public @Nullable PatternProviderTarget dataEnergistics$invokeExternalTarget(BlockPos position, Direction side) {
        Level level = this.host.getBlockEntity().getLevel();
        return level == null ? null : adaptiveExternalTarget(level, position, side);
    }

    @Nullable
    PatternProviderTarget adaptiveResolvedTarget(GlobalPos target, Direction face, ServerLevel sourceLevel) {
        var targetLevel = sourceLevel.getServer().getLevel(target.dimension());
        if (targetLevel == null || !targetLevel.isLoaded(target.pos())) {
            return null;
        }
        return PatternProviderTarget.get(targetLevel, target.pos(), null, face, this.actionSource);
    }

    boolean adaptiveIsBlocked(PatternProviderTarget target) {
        return adaptiveIsBlocking() && target.containsPatternInput(adaptivePatternInputs());
    }

    int adaptiveRoundRobinIndex() {
        if (this.connectorPolicy == ConnectorPolicy.PRIORITY) {
            return 0;
        }
        return this.connectorTargets.isEmpty() ? this.localRoundRobinIndex : this.connectorCursor;
    }

    void adaptiveAdvanceRoundRobin(int amount) {
        if (this.connectorPolicy == ConnectorPolicy.PRIORITY) {
            return;
        }
        this.localRoundRobinIndex += Math.max(0, amount);
        if (!this.connectorTargets.isEmpty()) {
            this.connectorCursor = Math.floorMod(this.connectorCursor + Math.max(0, amount), this.connectorTargets.size());
        }
    }

    void adaptiveSaveChanges() {
        this.host.saveChanges();
    }

    void adaptiveAlertDevice() {
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    boolean adaptiveIsPullModeEnabled() {
        return this.host instanceof AdaptivePatternProviderHost adaptiveHost && adaptiveHost.isResonatingPullEnabled();
    }

    boolean adaptiveIsFilteredImportEnabled() {
        return this.host instanceof AdaptivePatternProviderHost adaptiveHost && adaptiveHost.isAdvancedAeFilteredImportEnabled();
    }

    boolean adaptiveHasAvailableNativeSlot(IPatternDetails patternDetails) {
        for (var entry : this.nativePatternSlots.int2ObjectEntrySet()) {
            NativePatternSlot slot = entry.getValue();
            if (!slot.pattern().getDefinition().equals(patternDetails.getDefinition())) {
                continue;
            }
            AdaptiveReusableCraftingState.Slot resident = this.reusableCrafting.slot(entry.getIntKey());
            if (resident == null || !resident.endpoint().hasResidentSession()) {
                return true;
            }
        }
        return false;
    }

    public List<ConnectorLink> adaptiveConnectorBindings() {
        ObjectArrayList<ConnectorLink> result = new ObjectArrayList<>(this.connectorTargets.size());
        for (ConnectorTarget target : this.connectorTargets) {
            result.add(new ConnectorLink(target.position(), target.side(), target.mode()));
        }
        return ObjectLists.unmodifiable(result);
    }

    int adaptiveReusableWorkCount() {
        return this.reusableWorkCount;
    }

    void adaptiveAddReusableWork(int amount) {
        this.reusableWorkCount = Math.addExact(this.reusableWorkCount, amount);
    }

    void adaptiveResetReusableWorkCount() {
        this.reusableWorkCount = 0;
    }

    long adaptivePendingReusableOperations() {
        return this.reusableCrafting.pendingOperations();
    }

    boolean adaptiveReusableHandoffPrepared() {
        return this.reusableCrafting.handoffPrepared();
    }

    int adaptiveInstalledSpeedCardCount() {
        if (!(this.host instanceof AdaptivePatternProviderHost adaptiveHost)) {
            return 0;
        }
        return Math.max(0, adaptiveHost.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD));
    }

    boolean adaptiveIsSelected(AdaptivePatternProviderRegistration registration) {
        return registration == resolvedRegistration();
    }

    @Override
    public void addDrops(List<ItemStack> drops) {
        super.addDrops(drops);

        for (ItemStack stack : this.patternSlotOverflow) {
            drops.add(stack.copy());
        }

        for (var target : this.dispatchTargets.values()) {
            ObjectArrayList<ItemStack> fastDrops = new ObjectArrayList<>();
            target.dispatch().addDropsFast(target, fastDrops);
            drops.addAll(fastDrops);
        }
    }

    @Override
    public void clearContent() {
        this.reusableCrafting.ensureCanClear();
        super.clearContent();
        for (var target : this.dispatchTargets.values()) {
            target.dispatch().clearState(target);
        }
        this.dispatchTargets.clear();
        this.unloadedDispatchStates = new CompoundTag();
        this.patternSlotOverflow.clear();
        this.reconciledPatternSlotCount = -1;
        this.reusableCrafting = new AdaptiveReusableCraftingState();
        this.reusableItemHandoff = null;
        this.connectorCursor = 0;
        this.connectorPullCursor = 0;
        this.connectorPullSlotCursor = 0;
    }

    public Set<AEKey> getTrackedCrafts() {
        return this.trackedCrafts;
    }

    public Set<AEKey> getOutputCache() {
        return this.outputCache;
    }

    private void rebuildPatternsForConfiguredSlots() {
        this.patterns.clear();
        this.patternInputs.clear();
        this.nativePatternSlots.clear();

        var level = this.host.getBlockEntity().getLevel();
        int configuredSlotCount = getConfiguredPatternSlotCount();
        for (int slot = 0; slot < configuredSlotCount; slot++) {
            ItemStack patternStack = this.patternInventory.getStackInSlot(slot);
            IPatternDetails details = PatternDetailsHelper.decodePattern(patternStack, level);
            if (details == null) {
                continue;
            }

            this.patterns.add(details);
            if (level instanceof ServerLevel serverLevel && details instanceof IMolecularAssemblerSupportedPattern molecular) {
                var resolution = DataEnergisticsEntrypointLoader.snapshot().trinityPatternRecipes().resolve(molecular);
                if (resolution.isPresent()) {
                    this.nativePatternSlots.put(slot, new NativePatternSlot(molecular, resolution.orElseThrow().recipeId(),
                            TrinityPatternIdentity.capture(TrinityPatternPublicationSignature.capture(molecular), serverLevel.registryAccess())));
                }
            }
            for (var input : details.getInputs()) {
                for (var possibleInput : input.getPossibleInputs()) {
                    this.patternInputs.add(possibleInput.what().dropSecondary());
                }
            }
        }

        boolean reloaded = this.nativeRecipeEpoch != RecipeReloadEpoch.current();
        this.nativeRecipeEpoch = RecipeReloadEpoch.current();
        if (!this.reusableCrafting.handoffPrepared()) {
            for (AdaptiveReusableCraftingState.Slot slot : this.reusableCrafting.slots()) {
                NativePatternSlot current = this.nativePatternSlots.get(slot.index());
                if (slot.endpoint().hasResidentSession() && (reloaded || current == null ||
                        !current.pattern().getDefinition().equals(slot.pattern()) || !current.recipe().equals(slot.recipe()))) {
                    slot.requestClose();
                }
            }
        }

        ICraftingProvider.requestUpdate(this.mainNode);
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    private Set<AEKey> getPatternInputs() {
        return this.patternInputs;
    }

    private void invokePatternSuccess(IPatternDetails patternDetails) {
        super.onPushPatternSuccess(patternDetails);
    }

    private boolean invokeBaseDoWork() {
        return super.doWork();
    }

    private boolean invokeBaseIsBusy() {
        return super.isBusy();
    }

    private boolean invokeBaseHasWorkToDo() {
        return super.hasWorkToDo();
    }

    private void refreshAdaptivePatternTracking() {
        this.outputCache.clear();

        IStackWatcher watcher = this.craftingWatcher;
        if (watcher != null) {
            watcher.reset();
        }

        for (IPatternDetails pattern : getAvailablePatterns()) {
            if (pattern == null) {
                continue;
            }

            for (GenericStack output : pattern.getOutputs()) {
                if (output == null || output.what() == null) {
                    continue;
                }
                this.outputCache.add(output.what());
                if (watcher != null) {
                    watcher.add(output.what());
                }
            }
        }
    }

    public void onHostStateChanged() {
        var target = activeDispatchTarget();
        if (target != null) {
            target.dispatch().onProviderStateChanged(target);
        }
        if (!activeDispatchSupportsReusable() && !this.reusableCrafting.handoffPrepared()) {
            for (AdaptiveReusableCraftingState.Slot slot : this.reusableCrafting.slots()) {
                if (slot.endpoint().hasResidentSession()) {
                    slot.requestClose();
                }
            }
            this.host.saveChanges();
        }
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    private void dataEnergistics$afterPushPattern() {
        this.dataEnergistics$dispatchPulsePending = true;
        this.dataEnergistics$tryFinishDispatchPulse();
    }

    private void dataEnergistics$tryFinishDispatchPulse() {
        if (!this.dataEnergistics$dispatchPulsePending) {
            return;
        }
        if (!this.sendList.isEmpty()) {
            return;
        }
        for (var target : this.dispatchTargets.values()) {
            if (target.dispatch().hasPendingInput(target)) {
                return;
            }
        }
        this.dataEnergistics$dispatchPulsePending = false;
        if (this.host instanceof RedstoneTuningAwareHost tuningHost) {
            tuningHost.dataEnergistics$onRedstoneTuningDispatch();
        }
    }

    private void dataEnergistics$updatePulseUnlockState() {
        if (!(this.host instanceof RedstoneTuningAwareHost tuningHost)) {
            return;
        }

        tuningHost.dataEnergistics$scheduleRedstoneInputCheck();
        tuningHost.dataEnergistics$serverTick();

        if (!tuningHost.dataEnergistics$hasRedstoneTuningCard() || tuningHost.dataEnergistics$getRedstoneTuningMode() != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE) {
            return;
        }

        var blockEntity = this.host.getBlockEntity();
        if (blockEntity.getLevel() == null || blockEntity.getLevel().isClientSide()) {
            return;
        }

        if (tuningHost.dataEnergistics$consumeRedstoneInputPulse() && blockEntity.getLevel() instanceof ServerLevel serverLevel) {
            RedstoneTuningAutoRequestHelper.requestPrimaryOutputs(
                    serverLevel,
                    this.host.getGrid(),
                    this.actionSource,
                    getAvailablePatterns());
        }
    }

    @Override
    public boolean dataEnergistics$forcePulseUnlock() {
        if (!(this.host instanceof RedstoneTuningAwareHost tuningHost) || !tuningHost.dataEnergistics$hasRedstoneTuningCard() || tuningHost.dataEnergistics$getRedstoneTuningMode() != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE) {
            return false;
        }

        BlockEntity blockEntity = this.host.getBlockEntity();
        if (!(blockEntity.getLevel() instanceof ServerLevel serverLevel)) {
            return false;
        }

        RedstoneTuningAutoRequestHelper.requestPrimaryOutputs(
                serverLevel,
                this.host.getGrid(),
                this.actionSource,
                getAvailablePatterns());
        return true;
    }

    public int getReturnInventorySlotCount() {
        return getReturnInv().size();
    }

    public ItemStack getReturnInventoryStack(int slot) {
        if (slot < 0 || slot >= getReturnInv().size()) {
            return ItemStack.EMPTY;
        }

        GenericStack stack = getReturnInv().getStack(slot);
        if (stack == null || !(stack.what() instanceof AEItemKey itemKey) || stack.amount() <= 0) {
            return ItemStack.EMPTY;
        }

        return itemKey.toStack((int) Math.min(Integer.MAX_VALUE, stack.amount()));
    }

    public ItemStack insertReturnInventoryItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot < 0 || slot >= getReturnInv().size()) {
            return stack;
        }

        AEItemKey itemKey = AEItemKey.of(stack);
        if (itemKey == null) {
            return stack;
        }

        long inserted = getReturnInv().insert(slot, itemKey, stack.getCount(),
                simulate ? Actionable.SIMULATE : Actionable.MODULATE);
        if (inserted <= 0) {
            return stack;
        }

        if (inserted >= stack.getCount()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = stack.copy();
        remainder.shrink((int) inserted);
        return remainder;
    }

    public long insertReturnInventoryStack(int slot, GenericStack stack, boolean simulate) {
        if (stack == null || stack.what() == null || stack.amount() <= 0 || slot < 0 || slot >= getReturnInv().size()) {
            return 0;
        }

        long inserted = getReturnInv().insert(
                slot,
                stack.what(),
                stack.amount(),
                simulate ? Actionable.SIMULATE : Actionable.MODULATE);
        return inserted;
    }

    public long insertReturnInventoryKey(AEKey key, long amount, boolean simulate) {
        if (key == null || amount <= 0) {
            return 0;
        }

        long inserted = getReturnInv().insert(
                key,
                amount,
                simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                this.actionSource);
        return inserted;
    }

    private void installExpandedReturnInventory() {
        this.returnInv = new ExpandedReturnInventory(this::onReturnInventoryChanged, this);
    }

    private void onReturnInventoryChanged() {
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        this.host.saveChanges();
    }

    private Set<Direction> getActiveSidesFiltered() {
        var sides = EnumSet.copyOf(this.host.getTargets());
        var node = this.mainNode.getNode();
        if (node == null) {
            return sides;
        }

        for (var entry : node.getInWorldConnections().entrySet()) {
            var otherNode = entry.getValue().getOtherSide(node);
            Object owner = otherNode.getOwner();
            if (owner instanceof PatternProviderLogicHost || owner instanceof InterfaceLogicHost && otherNode.getGrid() != null && otherNode.getGrid().equals(this.mainNode.getGrid())) {
                sides.remove(entry.getKey());
            }
        }

        return sides;
    }

    private final class Ticker implements IGridTickable {

        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            boolean routeHasWork = hasDispatchWork();
            boolean sleeping = !invokeBaseHasWorkToDo() && !routeHasWork && !hasPullConnectorTargets() && getReturnInv().isEmpty() && !reusableCrafting.hasResidents();
            return new TickingRequest(
                    TickRates.Interface,
                    sleeping);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!mainNode.isActive()) {
                return TickRateModulation.SLEEP;
            }

            dataEnergistics$updatePulseUnlockState();
            boolean couldDoWork = invokeBaseDoWork();
            couldDoWork = tickConnectorPull() || couldDoWork;
            activeDispatchTarget();
            for (var target : dispatchTargets.values()) {
                couldDoWork = target.dispatch().tick(target, ticksSinceLastCall) || couldDoWork;
            }
            dataEnergistics$tryFinishDispatchPulse();
            couldDoWork = tickReusableCrafting() || couldDoWork;
            boolean routeHasWork = hasDispatchWork();
            // Linked outputs can become available without a crafting dispatch or a local inventory notification.
            boolean hasWork = invokeBaseHasWorkToDo() || routeHasWork || hasPullConnectorTargets() || !getReturnInv().isEmpty() || reusableCrafting.hasResidents();
            adaptiveResetReusableWorkCount();
            return hasWork ? (couldDoWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER) : TickRateModulation.SLEEP;
        }
    }

    private final class AdaptiveCraftingWatcherNode implements ICraftingWatcherNode {

        @Override
        public void updateWatcher(IStackWatcher watcher) {
            craftingWatcher = watcher;
            refreshAdaptivePatternTracking();
        }

        @Override
        public void onRequestChange(AEKey what) {
            if (what == null) {
                return;
            }

            if (trackedCrafts.contains(what)) {
                trackedCrafts.remove(what);
            } else {
                trackedCrafts.add(what);
            }
        }

        @Override
        public void onCraftableChange(AEKey what) {}
    }

    private static final class ExpandedReturnInventory extends PatternProviderReturnInventory {

        private static final ThreadLocal<Integer> PREVIOUS_SLOT_COUNT = new ThreadLocal<>();

        private final AdaptivePatternProviderLogic logic;

        private ExpandedReturnInventory(Runnable listener, AdaptivePatternProviderLogic logic) {
            super(prepare(listener));
            this.logic = logic;
            this.setFilter(this::isAllowed);
            Integer previous = PREVIOUS_SLOT_COUNT.get();
            if (previous != null) {
                PatternProviderReturnInventory.NUMBER_OF_SLOTS = previous;
            }
            PREVIOUS_SLOT_COUNT.remove();
        }

        private boolean isAllowed(int slot, AEKey key) {
            if (key == null) {
                return true;
            }
            var target = this.logic.activeDispatchTarget();
            return target == null || target.dispatch().allowsReturnItem(target, key);
        }

        private static Runnable prepare(Runnable listener) {
            PREVIOUS_SLOT_COUNT.set(PatternProviderReturnInventory.NUMBER_OF_SLOTS);
            PatternProviderReturnInventory.NUMBER_OF_SLOTS = EXPANDED_RETURN_SLOTS;
            return listener;
        }
    }
}
