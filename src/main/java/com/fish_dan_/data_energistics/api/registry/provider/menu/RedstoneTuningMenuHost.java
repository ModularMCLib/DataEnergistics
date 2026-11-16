package com.fish_dan_.data_energistics.api.registry.provider.menu;

import appeng.api.upgrades.IUpgradeInventory;

import org.jspecify.annotations.NullMarked;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Callback wrapper around one native host, created separately for each open menu. The native class needs no DE
 * interface. All members are non-null and stay on the owning menu thread; do not retain this wrapper globally.
 *
 * @param upgrades live inventory used for the redstone tuning upgrade slot on both logical sides
 * @param hasCard  reads the current upgrade state on the server, including changes while the menu is open
 * @param mode     reads the current server mode, 0 for emit-on-dispatch or 1 for pulse-to-unlock-once
 * @param setMode  applies a server request after the binding has validated its ordinal; performs native save/sync work
 */
@NullMarked
public record RedstoneTuningMenuHost(
                                     IUpgradeInventory upgrades, BooleanSupplier hasCard, IntSupplier mode, IntConsumer setMode) {}
