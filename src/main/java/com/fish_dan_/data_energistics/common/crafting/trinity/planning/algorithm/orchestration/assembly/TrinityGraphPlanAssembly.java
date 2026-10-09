package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.assembly;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;

/**
 * Complete plan payload after graph demand has been converted into deterministic execution structures.
 *
 * @param initialInputs        initial network inputs
 * @param patternFirings       aggregate firing counts by stable publication identity
 * @param stages               executable stage definitions
 * @param stageOrder           stable stage order
 * @param repeatBlocks         compressed cycle repeat blocks
 * @param minimumSeed          maximum seed reserve across cycle blocks
 * @param netChange            exact aggregate net change
 * @param stackRequests        exact byte-estimation stack volume
 * @param scheduleStates       total bounded search states
 * @param mipNanos             cycle MIP duration
 * @param quality              exact proof strength retained by the complete assembly
 * @param retainedSeed         internal restart balance required after every downstream stage
 * @param retainedSeedFinal    exact final balances observed on retained seed keys
 * @param seedRefinementPasses additional cycle solves used to prove terminal restart safety
 */
public record TrinityGraphPlanAssembly(
                                       Object2ObjectMap<AEKey, BigInteger> initialInputs,
                                       Object2ObjectMap<TrinityPatternIdentity, BigInteger> patternFirings,
                                       ObjectList<TrinityPlanStage> stages,
                                       IntList stageOrder,
                                       ObjectList<TrinityCycleRepeatBlock> repeatBlocks,
                                       Object2ObjectMap<AEKey, BigInteger> minimumSeed,
                                       Object2ObjectMap<AEKey, BigInteger> netChange,
                                       Object2ObjectMap<AEKey, BigInteger> stackRequests,
                                       int scheduleStates,
                                       long mipNanos,
                                       TrinityPlanQuality quality,
                                       Object2ObjectMap<AEKey, BigInteger> retainedSeed,
                                       Object2ObjectMap<AEKey, BigInteger> retainedSeedFinal,
                                       int seedRefinementPasses) {}
