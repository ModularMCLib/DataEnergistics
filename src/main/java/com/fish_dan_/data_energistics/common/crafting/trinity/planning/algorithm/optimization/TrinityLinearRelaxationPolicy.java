package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import org.ojalgo.optimisation.ExpressionsBasedModel;

/** Selects the linear relaxation backend for Trinity's integer optimisation models. */
public final class TrinityLinearRelaxationPolicy {

    private TrinityLinearRelaxationPolicy() {}

    /**
     * Apply before solving a request-owned model. The tableau backend rebuilds after non-fixed branch
     * bounds change, avoiding ojAlgo 57.3's expanded in-place bound updates in the revised simplex backend.
     * Integer branching, cut generation and exact result verification remain with their existing callers.
     */
    public static void configure(ExpressionsBasedModel model) {
        model.options.linear().primal();
    }
}
