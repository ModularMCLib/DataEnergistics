package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model;

import java.math.BigInteger;

/**
 * Structured result of one synchronous provider commit boundary.
 *
 * @param status                    final provider or dispatch status
 * @param exactLogicalCrafts        exact count owned by the provider, or zero before ownership
 * @param physicalAttempted         whether the physical-call budget was acquired
 * @param inputOwnershipTransferred whether the provider owns the admitted inputs
 * @param accountingSettled         whether reserved resources were either applied or released completely
 */
public record CraftingDispatchResult(
                                     CraftingDispatchStatus status,
                                     BigInteger exactLogicalCrafts,
                                     boolean physicalAttempted,
                                     boolean inputOwnershipTransferred,
                                     boolean accountingSettled) {

    public CraftingDispatchResult {
        if (status == null) {
            throw new IllegalArgumentException("Crafting dispatch result status must not be null");
        }
        if (exactLogicalCrafts.signum() < 0) {
            throw new IllegalArgumentException("Crafting dispatch result amount must not be negative");
        }
        if (inputOwnershipTransferred != (exactLogicalCrafts.signum() > 0)) {
            throw new IllegalArgumentException("Crafting dispatch ownership must match its logical amount");
        }
        if (inputOwnershipTransferred && !physicalAttempted) {
            throw new IllegalArgumentException("Crafting dispatch ownership requires a physical attempt");
        }
        if (status == CraftingDispatchStatus.ACCEPTED &&
                (!inputOwnershipTransferred || !accountingSettled)) {
            throw new IllegalArgumentException("Accepted crafting dispatch must own and account its inputs");
        }
        if ((status == CraftingDispatchStatus.FAILED_AFTER_OWNERSHIP) != inputOwnershipTransferred &&
                status != CraftingDispatchStatus.ACCEPTED) {
            throw new IllegalArgumentException("Crafting dispatch failure status disagrees with input ownership");
        }
    }

    public CraftingDispatchResult(CraftingDispatchStatus status, long logicalCrafts, boolean physicalAttempted,
                                  boolean inputOwnershipTransferred, boolean accountingSettled) {
        this(status, BigInteger.valueOf(logicalCrafts), physicalAttempted, inputOwnershipTransferred, accountingSettled);
    }

    /** Legacy projection rejects oversized results rather than reporting a truncated ownership amount. */
    public long logicalCrafts() {
        return this.exactLogicalCrafts.longValueExact();
    }

    /**
     * Returns whether this result consumed logical work and completed its CPU accounting.
     *
     * @return whether task progress may advance
     */
    public boolean dispatched() {
        return this.inputOwnershipTransferred && this.accountingSettled;
    }

    /**
     * Returns whether a resource-settlement failure requires the current job to stop.
     *
     * @return whether continuing could violate conservation
     */
    public boolean requiresJobAbort() {
        return !this.accountingSettled;
    }
}
