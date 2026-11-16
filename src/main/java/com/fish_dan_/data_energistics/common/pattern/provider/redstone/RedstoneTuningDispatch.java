package com.fish_dan_.data_energistics.common.pattern.provider.redstone;

import com.fish_dan_.data_energistics.api.registry.provider.dispatch.PatternProviderDispatchHooks;
import com.fish_dan_.data_energistics.api.registry.provider.dispatch.RedstoneTuningDispatchContext;

import org.jspecify.annotations.NullMarked;

/** Server-thread dispatch completion and pulse-consumption state, independent of native provider classes. */
@NullMarked
public final class RedstoneTuningDispatch implements PatternProviderDispatchHooks {

    private final RedstoneTuningDispatchContext context;
    private final boolean requestOnDispatch;
    private boolean dispatchPending;

    public RedstoneTuningDispatch(RedstoneTuningDispatchContext context, boolean requestOnDispatch) {
        this.context = context;
        this.requestOnDispatch = requestOnDispatch;
    }

    @Override
    public void patternPushed() {
        dispatchPending = true;
        afterWork();
    }

    @Override
    public void redstoneInputChanged() {
        context.inputChanged().run();
    }

    @Override
    public void beforeWork() {
        context.tick().run();
        if (context.hasCard().getAsBoolean() && context.mode().getAsInt() == 1 && context.consumeInputPulse().getAsBoolean()) {
            context.requestOutputs().getAsBoolean();
        }
    }

    @Override
    public void afterWork() {
        if (dispatchPending && !context.sendingInputs().getAsBoolean()) {
            dispatchPending = false;
            if (requestOnDispatch && context.consumeInputPulse().getAsBoolean()) {
                context.requestOutputs().getAsBoolean();
            }
            context.dispatched().run();
        }
    }

    @Override
    public boolean forcePulseUnlock() {
        return context.mode().getAsInt() == 1 && context.requestOutputs().getAsBoolean();
    }
}
