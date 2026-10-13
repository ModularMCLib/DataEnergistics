package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.capture;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;

import it.unimi.dsi.fastutil.objects.ObjectList;

/**
 * Small server-thread read surface used to derive a graph without exposing a provider cache to planner threads.
 */
public interface TrinityCraftingGraphCaptureSource {

    /**
     * @return current crafting-provider invalidation revision
     */
    long revision();

    /**
     * @return server registry lookup used only while converting AE keys to immutable canonical encodings
     */
    HolderLookup.Provider registries();

    /**
     * Captures the currently craftable primary-output keys.
     *
     * @return detached key list whose elements are immutable AE values
     */
    ObjectList<AEKey> captureCraftableKeys();

    /**
     * Captures the decoded patterns currently published for one primary output.
     *
     * <p>
     * The rebuilder consumes every returned pattern on the server thread and never publishes these mutable runtime
     * objects.
     * </p>
     *
     * @param primaryOutput craftable key captured from this source
     * @return detached list of runtime pattern references
     */
    ObjectList<IPatternDetails> capturePatternsFor(AEKey primaryOutput);
}
