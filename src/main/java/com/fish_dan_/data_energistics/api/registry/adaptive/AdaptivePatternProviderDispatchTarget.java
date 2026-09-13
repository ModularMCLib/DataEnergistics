package com.fish_dan_.data_energistics.api.registry.adaptive;

import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.helpers.patternprovider.PatternProviderTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Generic runtime operations exposed to one adaptive provider registration.
 *
 * <p>
 * The target deliberately contains provider mechanics only. A compatibility
 * registration can use these operations to implement its own route without the
 * adaptive provider core importing that compatibility's classes.
 * All operations run on the host level thread. Inputs are non-null unless
 * annotated otherwise. Collection views must be treated as read-only; state
 * changes belong in the explicitly mutating methods or the owned route state.
 * </p>
 */
public interface AdaptivePatternProviderDispatchTarget {

    /** Stable registration identity owning the current runtime target. */
    ResourceLocation registrationId();

    /** Whether the host currently selects this registration; false while draining an old route. */
    boolean isSelected();

    /** Uses AE2's normal pattern-provider route. */
    boolean pushDefault(IPatternDetails patternDetails, KeyCounter[] inputHolder);

    /** Whether AE2 still owns pending input from its normal dispatch path. */
    boolean isBusy();

    /** Whether the grid node is active and may perform work. */
    boolean isActive();

    /** Whether this decoded pattern is currently published by the host. */
    boolean hasPattern(IPatternDetails patternDetails);

    /** Whether AE2's crafting lock prevents a new dispatch. */
    boolean isCraftingLocked();

    /** Host level, or null before attachment to a world. */
    @Nullable
    Level level();

    /** Position of the host block or cable bus. */
    BlockPos providerPos();

    /** Returns adjacent sides after filtering same-grid provider connections. */
    List<Direction> targetSides();

    /** Notifies AE2 of a successful custom dispatch; call once after accepting its inputs. */
    void patternSuccess(IPatternDetails patternDetails);

    /** Returns the AE2 action source used for external target creation. */
    IActionSource actionSource();

    /** Returns the provider return inventory. */
    PatternProviderReturnInventory returnInventory();

    /** Returns the network inventory, or {@code null} while the provider is offline. */
    @Nullable
    MEStorage networkStorage();

    /** Returns the grid energy service, or {@code null} while the provider is offline. */
    @Nullable
    IEnergyService energyService();

    /** Returns whether AE2 Blocking Mode currently rejects a target. */
    boolean isBlocking();

    /** Returns normalized pattern input keys used by Blocking Mode. */
    Set<AEKey> patternInputs();

    /** Resolves a target at an adjacent block while excluding nested providers. */
    @Nullable
    PatternProviderTarget externalTarget(Level level, BlockPos position, Direction side);

    /** Resolves a serialized cross-dimension target when its chunk is loaded. */
    @Nullable
    PatternProviderTarget resolvedTarget(GlobalPos target, Direction face, ServerLevel sourceLevel);

    /** Checks whether Blocking Mode rejects one already resolved target. */
    boolean isBlocked(PatternProviderTarget target);

    /** Returns the round-robin cursor used for adjacent target selection. */
    int roundRobinIndex();

    /** Advances the round-robin cursor after a successful selection. */
    void advanceRoundRobin(int amount);

    /** Persists the host state. */
    void saveChanges();

    /** Wakes the host's grid device after a route queues work. */
    void alertDevice();

    /** Returns whether the provider's optional pull setting is enabled. */
    boolean isPullModeEnabled();

    /** Returns whether the optional returned-item filter is enabled. */
    boolean isFilteredImportEnabled();

    /** Returns keys currently tracked by the adaptive crafting watcher. */
    Set<AEKey> trackedCrafts();

    /** Returns decoded pattern outputs used by optional returned-item filters. */
    Set<AEKey> outputCache();

    /** Returns whether one native pattern has an unoccupied reusable slot. */
    boolean hasAvailableNativeSlot(IPatternDetails patternDetails);

    /** Returns the number of reusable operations already consumed in this round. */
    int reusableWorkCount();

    /** Adds completed reusable operations to the current round count. */
    void addReusableWork(int amount);

    /** Returns reusable operations already pending in resident sessions. */
    long pendingReusableOperations();

    /** Returns whether a dismantle-item handoff currently blocks reusable work. */
    boolean reusableHandoffPrepared();

    /** Returns the installed speed-card count used by a registered reusable route. */
    int installedSpeedCardCount();

    /** Returns the provider-owned connector links in stable configured order. */
    List<ConnectorLink> connectorBindings();

    /**
     * Returns or creates state owned by the current registration.
     *
     * <p>
     * The runtime retains the state object, while its interpretation and
     * serialization remain in the registration that requested it. The factory
     * must return a non-null instance. Changing its type for an existing route
     * throws ClassCastException; clear the state first when replacing it.
     * </p>
     */
    <T> T routeState(Class<T> stateType, Supplier<? extends T> factory);

    /** Drops all state owned by the current registration. */
    void clearRouteState();

    /** Returns whether an adjacent block entity is an AE2 pattern-provider attachment. */
    boolean isPatternProviderAttachment(Level level, BlockPos position, @Nullable Direction side);
}
