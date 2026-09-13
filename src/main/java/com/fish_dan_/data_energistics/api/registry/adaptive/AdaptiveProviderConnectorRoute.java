package com.fish_dan_.data_energistics.api.registry.adaptive;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderTarget;

import net.minecraft.resources.ResourceLocation;

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

    /** Returns the live target sequence in stable registration order. */
    List<PatternProviderTarget> resolveTargets(
                                               AdaptivePatternProviderDispatchTarget target, IPatternDetails patternDetails);

    /** Returns whether the target can accept the supplied normalized inputs. */
    boolean acceptsInputs(PatternProviderTarget target, KeyCounter[] inputs);

    /** Performs one bounded input transfer and returns the number accepted. */
    long insertInputs(PatternProviderTarget target, KeyCounter[] inputs, long maximum);

    /** Performs one bounded output extraction and returns extracted key stacks. */
    List<GenericStack> extractOutputs(
                                      PatternProviderTarget target, long maximum, boolean simulate);

    /** Returns the route revision used to invalidate a connector reservation. */
    long revision();
}
