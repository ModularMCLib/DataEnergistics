package com.fish_dan_.data_energistics.integration.ae.ae2cs.patternprovider;

import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatch;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchContext;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchTarget;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorMode;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorRouteTargets;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/**
 * AE2 Crystal Science routes registered by the optional integration.
 *
 * <p>
 * The route owns both resonating pattern delivery and the optional target
 * storage pull. It only consumes the generic runtime target exposed by the
 * adaptive provider API.
 * </p>
 */
public final class Ae2CrystalScienceAdaptiveRoute implements AdaptivePatternProviderDispatch {

    @Override
    public boolean usesSpecialBatchRoute(IPatternDetails patternDetails) {
        return true;
    }

    @Override
    public boolean handles(AdaptivePatternProviderDispatchContext context) {
        return Ae2CrystalScienceCompat.isResonatingPattern(context.patternDetails());
    }

    @Override
    public boolean hasWork(AdaptivePatternProviderDispatchTarget target) {
        return target.isSelected() && target.isPullModeEnabled();
    }

    /** Dispatches one resonating pattern request. */
    @Override
    public boolean dispatch(AdaptivePatternProviderDispatchContext dispatchContext) {
        AdaptivePatternProviderDispatchTarget context = dispatchContext.target();
        IPatternDetails patternDetails = dispatchContext.patternDetails();
        KeyCounter[] inputHolder = dispatchContext.inputHolder();
        if (context.isBusy() || !context.isActive() || !context.hasPattern(patternDetails) || context.isCraftingLocked()) {
            return false;
        }
        if (!(context.level() instanceof ServerLevel level)) {
            return false;
        }

        KeyCounter[] remaining = copyKeyCounters(inputHolder);
        ObjectArrayList<MarkedInput> markedInputs = new ObjectArrayList<>();
        List<GenericStack> sparseInputs = Ae2CrystalScienceCompat.getSparseInputs(patternDetails);
        for (int sparseIndex = 0; sparseIndex < sparseInputs.size(); sparseIndex++) {
            GenericStack sparseInput = sparseInputs.get(sparseIndex);
            if (sparseInput == null) {
                continue;
            }
            Ae2CrystalScienceCompat.ResolvedTarget target = Ae2CrystalScienceCompat.resolveTarget(patternDetails, sparseIndex);
            if (target == null) {
                continue;
            }
            if (!removeFromRemaining(remaining, sparseInput.what(), sparseInput.amount())) {
                return false;
            }
            markedInputs.add(new MarkedInput(
                    sparseInput.what(),
                    sparseInput.amount(),
                    new ResolvedTarget(target.position(), target.face())));
        }

        for (MarkedInput markedInput : markedInputs) {
            PatternProviderTarget target = context.resolvedTarget(
                    markedInput.target().position(), markedInput.target().face(), level);
            if (target == null || context.isBlocked(target)) {
                return false;
            }
            if (target.insert(markedInput.key(), markedInput.amount(), Actionable.SIMULATE) < markedInput.amount()) {
                return false;
            }
        }

        PatternProviderTarget fallbackTarget = null;
        if (!isEmpty(remaining)) {
            if (!patternDetails.supportsPushInputsToExternalInventory()) {
                return false;
            }
            ObjectArrayList<FallbackTarget> candidates = new ObjectArrayList<>();
            for (ConnectorLink binding : ConnectorRouteTargets.resolve(context, ConnectorMode.INPUT)) {
                Direction side = binding.side().getOpposite();
                PatternProviderTarget target = context.externalTarget(
                        level, binding.position(), binding.side());
                if (target != null && !context.isBlocked(target)) {
                    candidates.add(new FallbackTarget(side, target));
                }
            }
            rotateCandidates(candidates, context.roundRobinIndex());
            for (int index = 0; index < candidates.size(); index++) {
                FallbackTarget candidate = candidates.get(index);
                if (acceptsAll(candidate.target(), remaining)) {
                    fallbackTarget = candidate.target();
                    context.advanceRoundRobin(index + 1);
                    break;
                }
            }
            if (fallbackTarget == null) {
                return false;
            }
        }

        for (MarkedInput markedInput : markedInputs) {
            PatternProviderTarget target = context.resolvedTarget(
                    markedInput.target().position(), markedInput.target().face(), level);
            if (target == null || target.insert(markedInput.key(), markedInput.amount(), Actionable.MODULATE) < markedInput.amount()) {
                return false;
            }
        }
        if (fallbackTarget != null) {
            PatternProviderTarget target = fallbackTarget;
            patternDetails.pushInputsToExternalInventory(remaining, (what, amount) -> {
                if (target.insert(what, amount, Actionable.MODULATE) < amount) {
                    throw new IllegalStateException("Fallback target refused resonating pattern input.");
                }
            });
        }
        context.patternSuccess(patternDetails);
        return true;
    }

    /** Performs one bounded target-storage pull for the optional AE2CS setting. */
    @Override
    public boolean tick(AdaptivePatternProviderDispatchTarget context, int ticksSinceLastCall) {
        if (!context.isSelected() || !context.isPullModeEnabled() || !context.isActive()) {
            return false;
        }
        if (!(context.level() instanceof ServerLevel level)) {
            return false;
        }

        MEStorage networkStorage = context.networkStorage();
        var returnInventory = context.returnInventory();
        final int maxKeysPerTick = 32;
        int scanned = 0;
        for (ConnectorLink binding : ConnectorRouteTargets.resolve(context, ConnectorMode.PULL)) {
            BlockPos adjacentPos = binding.position();
            Direction adjacentFace = binding.side();
            if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(adjacentPos.getX()), SectionPos.blockToSectionCoord(adjacentPos.getZ())) || context.isPatternProviderAttachment(level, adjacentPos, adjacentFace)) {
                continue;
            }

            MEStorage externalStorage = Ae2CrystalScienceCompat.getAdjacentMeStorage(
                    level, adjacentPos, null, adjacentFace);
            if (externalStorage == null) {
                continue;
            }

            for (var stack : externalStorage.getAvailableStacks()) {
                if (scanned++ >= maxKeysPerTick) {
                    return false;
                }

                AEKey key = stack.getKey();
                long available = stack.getLongValue();
                if (key == null || available <= 0) {
                    continue;
                }

                long request = Math.min(available, 4000L);
                long canInsertIntoNetwork = networkStorage == null ? 0 : networkStorage.insert(key, request, Actionable.SIMULATE, context.actionSource());
                long remainingRequest = request - canInsertIntoNetwork;
                long canBuffer = remainingRequest <= 0 ? 0 : returnInventory.insert(
                        key, remainingRequest, Actionable.SIMULATE, context.actionSource());
                long pullAmount = canInsertIntoNetwork + canBuffer;
                if (pullAmount <= 0) {
                    continue;
                }

                long extracted = externalStorage.extract(
                        key, pullAmount, Actionable.MODULATE, context.actionSource());
                if (extracted <= 0) {
                    continue;
                }

                long insertedIntoNetwork = networkStorage == null ? 0 : networkStorage.insert(key, extracted, Actionable.MODULATE, context.actionSource());
                long leftover = extracted - insertedIntoNetwork;
                long buffered = leftover <= 0 ? 0 : returnInventory.insert(key, leftover, Actionable.MODULATE, context.actionSource());
                leftover -= buffered;
                if (leftover > 0) {
                    externalStorage.insert(key, leftover, Actionable.MODULATE, context.actionSource());
                }
                context.saveChanges();
                return true;
            }
        }
        return false;
    }

    private static KeyCounter[] copyKeyCounters(KeyCounter[] inputHolder) {
        KeyCounter[] copy = new KeyCounter[inputHolder.length];
        for (int index = 0; index < inputHolder.length; index++) {
            copy[index] = new KeyCounter();
            for (var entry : inputHolder[index]) {
                copy[index].add(entry.getKey(), entry.getLongValue());
            }
        }
        return copy;
    }

    private static boolean removeFromRemaining(KeyCounter[] remaining, AEKey key, long amount) {
        long toRemove = amount;
        for (KeyCounter counter : remaining) {
            long available = counter.get(key);
            if (available <= 0) {
                continue;
            }
            long taken = Math.min(available, toRemove);
            counter.remove(key, taken);
            toRemove -= taken;
            if (toRemove <= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEmpty(KeyCounter[] counters) {
        for (KeyCounter counter : counters) {
            if (!counter.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean acceptsAll(PatternProviderTarget target, KeyCounter[] counters) {
        for (KeyCounter counter : counters) {
            for (var entry : counter) {
                if (target.insert(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE) < entry.getLongValue()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void rotateCandidates(List<FallbackTarget> candidates, int roundRobinIndex) {
        if (candidates.isEmpty()) {
            return;
        }
        int offset = Math.floorMod(roundRobinIndex, candidates.size());
        if (offset == 0) {
            return;
        }
        var head = new ObjectArrayList<>(candidates.subList(0, offset));
        candidates.subList(0, offset).clear();
        candidates.addAll(head);
    }

    public record ResolvedTarget(GlobalPos position, Direction face) {}

    public record FallbackTarget(Direction direction, PatternProviderTarget target) {}

    private record MarkedInput(AEKey key, long amount, ResolvedTarget target) {}
}
