package com.fish_dan_.data_energistics.api.registry.adaptive;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Dispatch extension for one adaptive pattern-provider registration.
 *
 * <p>
 * The callback is evaluated before the normal AE2 provider route. The
 * separate applicability check keeps the result primitive while still
 * distinguishing an unclaimed pattern from a failed dispatch.
 * Implementations are shared between hosts and must keep per-host mutable state
 * in {@link AdaptivePatternProviderDispatchTarget#routeState}, not instance fields.
 * Runtime callbacks execute on the host's level thread; they must not retain a
 * target after the host is removed. Optional hooks are no-ops unless overridden.
 * </p>
 */
@FunctionalInterface
public interface AdaptivePatternProviderDispatch {

    /** Returns this provider registration's connector routes in stable priority order. */
    default ObjectList<AdaptiveProviderConnectorRoute> connectorRoutes() {
        return ObjectList.of();
    }

    /**
     * Identifies this route's legacy root-tag payload, or {@code null} when it has none.
     * Registrations sharing an old payload must return the same key. Migration gives
     * the selected registration precedence and restores each legacy payload once.
     */
    default @Nullable String legacyStateKey() {
        return null;
    }

    /**
     * Whether this registration needs the adaptive counted-batch preparation
     * path for the supplied pattern.
     *
     * @param patternDetails pattern being prepared
     * @return whether the special route is required
     */
    default boolean usesSpecialBatchRoute(IPatternDetails patternDetails) {
        return false;
    }

    /**
     * Checks whether this route claims one pattern.
     */
    default boolean handles(AdaptivePatternProviderDispatchContext context) {
        return false;
    }

    /**
     * Attempts to dispatch one claimed pattern.
     */
    boolean dispatch(AdaptivePatternProviderDispatchContext context);

    /**
     * Returns whether this registration still owns inputs waiting for a previous
     * dispatch to finish. The core uses this to complete redstone dispatch pulses.
     */
    default boolean hasPendingInput(AdaptivePatternProviderDispatchTarget target) {
        return false;
    }

    /**
     * Returns whether this registration has work that should keep its grid device
     * awake. Inactive registrations must report only already-owned buffered work;
     * new background work requires {@link AdaptivePatternProviderDispatchTarget#isSelected()}.
     */
    default boolean hasWork(AdaptivePatternProviderDispatchTarget target) {
        return false;
    }

    /**
     * Advances integration-specific background work for one grid tick. This also
     * runs after selection changes so accepted inputs and outputs can drain.
     *
     * @param target             the generic adaptive-provider runtime surface
     * @param ticksSinceLastCall elapsed grid ticks supplied by AE2
     * @return whether the registration made progress
     */
    default boolean tick(AdaptivePatternProviderDispatchTarget target, int ticksSinceLastCall) {
        return false;
    }

    /** Writes owned state into this registration's isolated tag, including inactive buffers. */
    default void writeState(
                            AdaptivePatternProviderDispatchTarget target,
                            CompoundTag tag,
                            HolderLookup.Provider registries) {}

    /** Restores owned state. Validate serialized inputs here; legacy migration passes the old root tag. */
    default void readState(
                           AdaptivePatternProviderDispatchTarget target,
                           CompoundTag tag,
                           HolderLookup.Provider registries) {}

    /** Adds registration-owned buffered values to a provider's drops. */
    default void addDrops(AdaptivePatternProviderDispatchTarget target, List<ItemStack> drops) {}

    /** Clears registration-owned runtime state when the provider is emptied. */
    default void clearState(AdaptivePatternProviderDispatchTarget target) {}

    /** Notifies the registration that installed provider settings changed. */
    default void onProviderStateChanged(AdaptivePatternProviderDispatchTarget target) {}

    /** Notifies the registration that the decoded pattern list was rebuilt. */
    default void onPatternsUpdated(AdaptivePatternProviderDispatchTarget target) {}

    /** Checks whether one returned key is allowed by this registration's filter. */
    default boolean allowsReturnItem(AdaptivePatternProviderDispatchTarget target, AEKey key) {
        return true;
    }

    /** Declares that this registration owns the reusable native-pattern route. */
    default boolean supportsReusablePatterns() {
        return false;
    }

    /** Returns the maximum reusable operations allowed in the current round. */
    default int reusableWorkLimit(AdaptivePatternProviderDispatchTarget target) {
        return 0;
    }

    /** Returns the energy cost of one reusable operation. */
    default double reusableEnergyPerWork(AdaptivePatternProviderDispatchTarget target) {
        return 0.0D;
    }

    /** Receives outputs produced by the reusable native-pattern route. */
    default void acceptReusableOutputs(
                                       AdaptivePatternProviderDispatchTarget target,
                                       List<GenericStack> outputs) {}
}
