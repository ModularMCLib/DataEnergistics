package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit;

/**
 * Lets an in-process crafting admission return immediately available outputs after CPU accounting has committed.
 *
 * <p>
 * Providers must not return outputs from their ownership-transfer callback: the CPU has not registered its waiting
 * amounts yet. This optional boundary runs on the server thread after that registration, within the same measured
 * physical submission. It must not start another dispatch or retain the caller's submission scope.
 * </p>
 */
public interface CraftingDispatchOutputRecovery {

    /**
     * Executes admitted synchronous work and routes its outputs to waiting CPUs. Pending or blocked work remains
     * provider-owned for a later pass. Failures propagate to the caller's Grid isolation boundary.
     */
    void recoverOutputs();
}
