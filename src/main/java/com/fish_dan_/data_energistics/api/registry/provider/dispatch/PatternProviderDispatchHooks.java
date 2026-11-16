package com.fish_dan_.data_energistics.api.registry.provider.dispatch;

import com.fish_dan_.data_energistics.common.pattern.provider.redstone.RedstoneTuningCraftingRequests;
import com.fish_dan_.data_energistics.common.pattern.provider.redstone.RedstoneTuningDispatch;
import com.fish_dan_.data_energistics.integration.ae.ae2.accessor.patternprovider.PatternProviderDispatchAccess;
import com.fish_dan_.data_energistics.integration.ae.ae2.patternprovider.redstone.NativeRedstoneTuningDispatch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.helpers.patternprovider.PatternProviderLogic;

import net.minecraft.server.level.ServerLevel;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Provider-local execution policy, supplied by a registration or an optional native compatibility wrapper. The native
 * logic class need not implement this interface. Work and dispatch calls run on the owning server thread after
 * construction; input notifications follow the native host's logical side. Factory callbacks may capture native
 * objects but must defer reading mutable state until these lifecycle calls.
 */
@NullMarked
public interface PatternProviderDispatchHooks {

    /** Records one successful physical or counted push. Failed or simulated pushes must not call this method. */
    void patternPushed();

    /** Samples or schedules an input change on the native host's logical side, before lock-state processing. */
    void redstoneInputChanged();

    /** Updates tuning state and consumes a queued unlock pulse immediately before the native work loop. */
    void beforeWork();

    /** Completes a pending dispatch after the native work loop has transferred its input remainder. */
    void afterWork();

    /** Attempts one pulse-mode output request; returns false when the mode or native level does not accept it. */
    boolean forcePulseUnlock();

    /** Creates the shared redstone dispatch policy from non-null live callbacks, without reading native state yet. */
    static PatternProviderDispatchHooks redstoneTuning(RedstoneTuningDispatchContext context) {
        return new RedstoneTuningDispatch(context, false);
    }

    /** Creates the resonating/mechanical policy, which also consumes a queued request after dispatch completes. */
    static PatternProviderDispatchHooks requestingRedstoneTuning(RedstoneTuningDispatchContext context) {
        return new RedstoneTuningDispatch(context, true);
    }

    /**
     * Creates the requesting policy for a native AE2 subclass from a runtime registration. The non-null input callback
     * must include all of the subclass's pending input queues and defer reading them until after construction.
     * Returns null when the native host has no built-in redstone capability; factory failures propagate.
     */
    static @Nullable PatternProviderDispatchHooks requestingRedstoneTuning(
                                                                           PatternProviderLogic logic, BooleanSupplier sendingInputs) {
        return NativeRedstoneTuningDispatch.requesting(logic, sendingInputs);
    }

    /**
     * Returns the native AE2 logic's attached policy after its base constructor, or null for an unsupported host.
     * Optional wrappers may retain it only for that logic's lifetime and forward overridden native lifecycle calls.
     */
    static @Nullable PatternProviderDispatchHooks attachedTo(PatternProviderLogic logic) {
        return ((PatternProviderDispatchAccess) logic).dataEnergistics$dispatchHooks();
    }

    /**
     * Requests each current pattern's primary output on the server thread. Inputs are non-null except grid, which
     * may be absent while the host is offline. Calculations run asynchronously and submit real plans on the server;
     * invalid third-party outputs and calculation/submission failures are logged with their request context.
     */
    static void requestPrimaryOutputs(ServerLevel level, @Nullable IGrid grid,
                                      IActionSource actionSource, List<IPatternDetails> patterns) {
        RedstoneTuningCraftingRequests.requestPrimaryOutputs(level, grid, actionSource, patterns);
    }
}
