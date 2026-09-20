package com.fish_dan_.data_energistics.integration.ae.advancedae.patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;

import net.minecraft.core.Direction;
import net.pedroksl.advanced_ae.common.patterns.IAdvPatternDetails;

import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/** Direct Advanced AE pattern API calls used by the Adaptive provider. */
public final class AdvancedAeCompat {

    private AdvancedAeCompat() {}

    public static boolean hasDirectionalInputs(IPatternDetails patternDetails) {
        return patternDetails instanceof IAdvPatternDetails advanced && advanced.directionalInputsSet();
    }

    public static @Nullable Direction getInputSide(IPatternDetails patternDetails, AEKey key) {
        return patternDetails instanceof IAdvPatternDetails advanced ? advanced.getDirectionSideForInputKey(key) : null;
    }

    public static Map<AEKey, Direction> getDirectionMap(IPatternDetails patternDetails) {
        return patternDetails instanceof IAdvPatternDetails advanced ? advanced.getDirectionMap() : Object2ObjectMaps.emptyMap();
    }
}
