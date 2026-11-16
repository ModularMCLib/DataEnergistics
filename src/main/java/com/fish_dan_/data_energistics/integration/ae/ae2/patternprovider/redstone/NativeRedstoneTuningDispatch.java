package com.fish_dan_.data_energistics.integration.ae.ae2.patternprovider.redstone;

import com.fish_dan_.data_energistics.api.registry.provider.dispatch.PatternProviderDispatchHooks;
import com.fish_dan_.data_energistics.api.registry.provider.dispatch.RedstoneTuningDispatchContext;
import com.fish_dan_.data_energistics.integration.ae.ae2.accessor.patternprovider.PatternProviderBatchAccess;
import com.fish_dan_.data_energistics.integration.ae.ae2.accessor.patternprovider.RedstoneTuningAwareHost;
import com.fish_dan_.data_energistics.utils.RedstoneTuningUtils;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.helpers.patternprovider.PatternProviderLogic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Native AE2 host wiring for the public dispatch policy; business state lives in the policy instance. */
@NullMarked
public final class NativeRedstoneTuningDispatch {

    private NativeRedstoneTuningDispatch() {}

    /** Creates a standard policy around public host state and the required AE2 send-list access bridge. */
    public static @Nullable PatternProviderDispatchHooks standard(PatternProviderLogic logic) {
        return standard(logic, false);
    }

    /** Creates the policy used by resonating and mechanical native subclasses. */
    public static @Nullable PatternProviderDispatchHooks requesting(PatternProviderLogic logic) {
        return standard(logic, true);
    }

    private static @Nullable PatternProviderDispatchHooks standard(PatternProviderLogic logic, boolean requestOnDispatch) {
        var access = (PatternProviderBatchAccess) logic;
        var host = access.dataEnergistics$getHost();
        return create(host, () -> !access.dataEnergistics$getSendList().isEmpty(),
                host::getBlockEntity, host::getGrid, access::dataEnergistics$getActionSource,
                logic::getAvailablePatterns, requestOnDispatch);
    }

    /** Wires another native provider using callbacks, without reflection or a DE interface on its logic class. */
    public static @Nullable PatternProviderDispatchHooks create(
                                                                Object nativeHost,
                                                                BooleanSupplier sendingInputs,
                                                                Supplier<BlockEntity> blockEntity,
                                                                Supplier<@Nullable IGrid> grid,
                                                                Supplier<IActionSource> actionSource,
                                                                Supplier<List<IPatternDetails>> patterns,
                                                                boolean requestOnDispatch) {
        if (!(nativeHost instanceof RedstoneTuningAwareHost host)) {
            return null;
        }
        var context = RedstoneTuningDispatchContext.builder()
                .hasCard(host::dataEnergistics$hasRedstoneTuningCard)
                .mode(() -> host.dataEnergistics$getRedstoneTuningMode().ordinal())
                .tick(host::dataEnergistics$serverTick)
                .inputChanged(host::dataEnergistics$scheduleRedstoneInputCheck)
                .consumeInputPulse(host::dataEnergistics$consumeRedstoneInputPulse)
                .dispatched(host::dataEnergistics$onRedstoneTuningDispatch)
                .sendingInputs(sendingInputs)
                .requestOutputs(() -> {
                    if (!(blockEntity.get().getLevel() instanceof ServerLevel level)) {
                        return false;
                    }
                    RedstoneTuningUtils.requestPrimaryOutputs(level, grid.get(), actionSource.get(), patterns.get());
                    return true;
                }).build();
        return requestOnDispatch ? PatternProviderDispatchHooks.requestingRedstoneTuning(context) :
                PatternProviderDispatchHooks.redstoneTuning(context);
    }
}
