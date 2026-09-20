package com.fish_dan_.data_energistics.api.registry.packaged;

import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;

/**
 * Transaction-local declarations of real-machine packaged crafting adapters.
 * Register during the owning plugin's common-setup callback only. A failed plugin discards all its declarations;
 * worlds consume the frozen snapshot. Adapters are shared and must not retain per-provider state.
 */
@FunctionalInterface
public interface PackagedCraftingRegistry {

    /** Registers a non-null adapter with a unique ID; duplicate IDs fail the plugin transaction. */
    void register(PackagedMachineAdapter adapter);
}
