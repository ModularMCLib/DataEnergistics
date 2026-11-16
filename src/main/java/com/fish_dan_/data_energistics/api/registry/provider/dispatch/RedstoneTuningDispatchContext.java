package com.fish_dan_.data_energistics.api.registry.provider.dispatch;

import lombok.Builder;
import org.jspecify.annotations.NullMarked;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Live callbacks around one native provider's dispatch state. Build once per provider, retain only for its lifetime,
 * and invoke on its server thread. All callbacks are non-null and read current state rather than cached snapshots.
 *
 * @param hasCard           whether the native host currently has a redstone tuning card
 * @param mode              current tuning mode, 0 for emission or 1 for pulse-to-unlock
 * @param tick              advances the host's signal state once for the current game tick
 * @param inputChanged      schedules or samples a native redstone input change
 * @param consumeInputPulse consumes exactly one pending input pulse, returning whether one was available
 * @param dispatched        notifies the host after all accepted inputs have left this provider
 * @param sendingInputs     whether any accepted inputs still await transfer, including native target buffers
 * @param requestOutputs    submits primary-output crafting requests, returning whether the native level accepts them
 */
@Builder
@NullMarked
public record RedstoneTuningDispatchContext(
                                            BooleanSupplier hasCard,
                                            IntSupplier mode,
                                            Runnable tick,
                                            Runnable inputChanged,
                                            BooleanSupplier consumeInputPulse,
                                            Runnable dispatched,
                                            BooleanSupplier sendingInputs,
                                            BooleanSupplier requestOutputs) {}
