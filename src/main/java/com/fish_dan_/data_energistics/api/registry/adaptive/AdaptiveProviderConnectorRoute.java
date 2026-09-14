package com.fish_dan_.data_energistics.api.registry.adaptive;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderTarget;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.List;

/**
 * A provider-registration-owned route usable by a connector.
 *
 * <p>
 * The route resolves live targets on every capture. It must not retain target,
 * level, pattern, or capability objects between calls. Capacity is a read-only
 * observation and must be re-captured before a real transfer.
 * </p>
 */
public interface AdaptiveProviderConnectorRoute {

    ResourceLocation routeId();

    AdaptiveProviderConnectorDirection direction();

    /**
     * Returns the live target sequence in stable registration order.
     *
     * @deprecated scheduled for removal in plan 340; use
     *             {@link #resolveTargetsFast(AdaptivePatternProviderDispatchTarget, IPatternDetails)}
     */
    @Deprecated(forRemoval = true)
    List<PatternProviderTarget> resolveTargets(
                                               AdaptivePatternProviderDispatchTarget target, IPatternDetails patternDetails);

    /** Returns the live target sequence through the FastUtil collection API. */
    @SuppressWarnings("unchecked")
    default ObjectList<PatternProviderTarget> resolveTargetsFast(
                                                                 AdaptivePatternProviderDispatchTarget target, IPatternDetails patternDetails) {
        List<PatternProviderTarget> legacy = resolveTargets(target, patternDetails);
        return legacy instanceof ObjectList<?> fast ? (ObjectList<PatternProviderTarget>) fast : ObjectLists.unmodifiable(new ObjectArrayList<>(legacy));
    }

    /** Returns whether the target can accept the supplied normalized inputs. */
    boolean acceptsInputs(PatternProviderTarget target, KeyCounter[] inputs);

    /** Performs one bounded input transfer and returns the number accepted. */
    long insertInputs(PatternProviderTarget target, KeyCounter[] inputs, long maximum);

    /**
     * Performs one bounded output extraction and returns extracted key stacks.
     *
     * @deprecated scheduled for removal in plan 340; use
     *             {@link #extractOutputsFast(PatternProviderTarget, long, boolean)}
     */
    @Deprecated(forRemoval = true)
    List<GenericStack> extractOutputs(
                                      PatternProviderTarget target, long maximum, boolean simulate);

    /** Performs one bounded output extraction and returns a FastUtil collection. */
    @SuppressWarnings("unchecked")
    default ObjectList<GenericStack> extractOutputsFast(
                                                        PatternProviderTarget target, long maximum, boolean simulate) {
        List<GenericStack> legacy = extractOutputs(target, maximum, simulate);
        return legacy instanceof ObjectList<?> fast ? (ObjectList<GenericStack>) fast : ObjectLists.unmodifiable(new ObjectArrayList<>(legacy));
    }

    /** Returns the route revision used to invalidate a connector reservation. */
    long revision();
}
