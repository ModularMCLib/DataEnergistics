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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

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

    private static final String NBT_SEND_LIST = "resonating_send_list";

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
        return hasPendingInput(target) || target.isSelected() && target.isPullModeEnabled();
    }

    @Override
    public boolean hasPendingInput(AdaptivePatternProviderDispatchTarget target) {
        return !state(target).pending.isEmpty();
    }

    /** Dispatches one resonating pattern request. */
    @Override
    public boolean dispatch(AdaptivePatternProviderDispatchContext dispatchContext) {
        AdaptivePatternProviderDispatchTarget context = dispatchContext.target();
        IPatternDetails patternDetails = dispatchContext.patternDetails();
        KeyCounter[] inputHolder = dispatchContext.inputHolder();
        if (context.isBusy() || hasPendingInput(context) || !context.isActive() ||
                !context.hasPattern(patternDetails) || context.isCraftingLocked()) {
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
            if (target.insert(markedInput.key(), markedInput.amount(), Actionable.SIMULATE) <= 0L) {
                return false;
            }
        }

        PatternProviderTarget fallbackTarget = null;
        BlockPos fallbackPosition = null;
        Direction fallbackFace = null;
        if (!isEmpty(remaining)) {
            if (!patternDetails.supportsPushInputsToExternalInventory()) {
                return false;
            }
            ObjectArrayList<FallbackTarget> candidates = new ObjectArrayList<>();
            for (ConnectorLink binding : ConnectorRouteTargets.resolve(context, ConnectorMode.INPUT)) {
                PatternProviderTarget target = context.externalTarget(
                        level, binding.position(), binding.side());
                if (target != null && !context.isBlocked(target)) {
                    candidates.add(new FallbackTarget(binding.position(), binding.side(), target));
                }
            }
            rotateCandidates(candidates, context.roundRobinIndex());
            for (int index = 0; index < candidates.size(); index++) {
                FallbackTarget candidate = candidates.get(index);
                if (acceptsAny(candidate.target(), remaining)) {
                    fallbackTarget = candidate.target();
                    fallbackPosition = candidate.position();
                    fallbackFace = candidate.face();
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
            long inserted = target == null || context.isBlocked(target) ? 0L : target.insert(
                    markedInput.key(), markedInput.amount(), Actionable.MODULATE);
            if (inserted < markedInput.amount()) {
                queueRemainder(
                        context,
                        markedInput.target(),
                        markedInput.key(),
                        markedInput.amount() - inserted);
            }
        }
        if (fallbackTarget != null) {
            PatternProviderTarget target = fallbackTarget;
            ResolvedTarget resolvedFallbackTarget = new ResolvedTarget(
                    GlobalPos.of(level.dimension(), fallbackPosition), fallbackFace);
            patternDetails.pushInputsToExternalInventory(remaining, (what, amount) -> {
                long inserted = target.insert(what, amount, Actionable.MODULATE);
                if (inserted < amount) {
                    queueRemainder(context, resolvedFallbackTarget, what, amount - inserted);
                }
            });
        }
        context.patternSuccess(patternDetails);
        if (hasPendingInput(context)) {
            context.saveChanges();
            context.alertDevice();
        }
        return true;
    }

    /** Performs one bounded target-storage pull for the optional AE2CS setting. */
    @Override
    public boolean tick(AdaptivePatternProviderDispatchTarget context, int ticksSinceLastCall) {
        if (!(context.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!context.isActive()) {
            return false;
        }

        boolean worked = flushPending(context, level);
        if (!context.isSelected() || !context.isPullModeEnabled()) {
            if (worked) {
                context.saveChanges();
            }
            return worked;
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
                    if (worked) {
                        context.saveChanges();
                    }
                    return worked;
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
        if (worked) {
            context.saveChanges();
        }
        return worked;
    }

    @Override
    public void writeState(
            AdaptivePatternProviderDispatchTarget target,
            CompoundTag tag,
            HolderLookup.Provider registries) {
        ListTag sendList = new ListTag();
        for (PendingSend pending : state(target).pending) {
            if (pending.amount() <= 0L) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", pending.target().position().dimension().location().toString());
            entry.putLong("pos", pending.target().position().pos().asLong());
            entry.putByte("face", (byte) pending.target().face().get3DDataValue());
            entry.put("stack", GenericStack.writeTag(
                    registries, new GenericStack(pending.key(), pending.amount())));
            sendList.add(entry);
        }
        if (sendList.isEmpty()) {
            tag.remove(NBT_SEND_LIST);
        } else {
            tag.put(NBT_SEND_LIST, sendList);
        }
    }

    @Override
    public void readState(
            AdaptivePatternProviderDispatchTarget target,
            CompoundTag tag,
            HolderLookup.Provider registries) {
        State state = state(target);
        state.pending.clear();
        if (!tag.contains(NBT_SEND_LIST, Tag.TAG_LIST)) {
            return;
        }

        ListTag sendList = tag.getList(NBT_SEND_LIST, Tag.TAG_COMPOUND);
        for (int index = 0; index < sendList.size(); index++) {
            CompoundTag entry = sendList.getCompound(index);
            String dimensionId = entry.getString("dimension");
            if (dimensionId.isBlank()) {
                throw new IllegalArgumentException("Resonating pending input is missing its dimension");
            }
            ResourceKey<Level> dimension = ResourceKey.create(
                    Registries.DIMENSION, ResourceLocation.parse(dimensionId));
            byte rawFace = entry.getByte("face");
            if (rawFace < 0 || rawFace >= Direction.values().length) {
                throw new IllegalArgumentException("Invalid resonating pending input direction: " + rawFace);
            }
            GenericStack stack = GenericStack.readTag(registries, entry.getCompound("stack"));
            if (stack == null || stack.what() == null || stack.amount() <= 0L) {
                continue;
            }
            state.pending.add(new PendingSend(
                    new ResolvedTarget(
                            GlobalPos.of(dimension, BlockPos.of(entry.getLong("pos"))),
                            Direction.from3DDataValue(rawFace)),
                    stack.what(),
                    stack.amount()));
        }
    }

    @Override
    public void addDropsFast(
            AdaptivePatternProviderDispatchTarget target,
            ObjectList<ItemStack> drops) {
        for (PendingSend pending : state(target).pending) {
            if (pending.amount() > 0L) {
                pending.key().addDrops(
                        pending.amount(), drops, target.level(), target.providerPos());
            }
        }
    }

    @Override
    public void clearState(AdaptivePatternProviderDispatchTarget target) {
        target.clearRouteState();
    }

    private static State state(AdaptivePatternProviderDispatchTarget target) {
        return target.routeState(State.class, State::new);
    }

    private static void queueRemainder(
            AdaptivePatternProviderDispatchTarget target,
            ResolvedTarget resolvedTarget,
            AEKey key,
            long amount) {
        if (key == null || amount <= 0L) {
            return;
        }
        State state = state(target);
        for (int index = 0; index < state.pending.size(); index++) {
            PendingSend pending = state.pending.get(index);
            if (pending.target().equals(resolvedTarget) && pending.key().equals(key)) {
                state.pending.set(index, new PendingSend(
                        resolvedTarget, key, Math.addExact(pending.amount(), amount)));
                return;
            }
        }
        state.pending.add(new PendingSend(resolvedTarget, key, amount));
    }

    private static boolean flushPending(
            AdaptivePatternProviderDispatchTarget context,
            ServerLevel sourceLevel) {
        State state = state(context);
        if (state.pending.isEmpty()) {
            return false;
        }

        boolean changed = false;
        var iterator = state.pending.listIterator();
        while (iterator.hasNext()) {
            PendingSend pending = iterator.next();
            PatternProviderTarget target = context.resolvedTarget(
                    pending.target().position(), pending.target().face(), sourceLevel);
            if (target == null || context.isBlocked(target)) {
                continue;
            }
            long inserted = target.insert(
                    pending.key(), pending.amount(), Actionable.MODULATE);
            if (inserted <= 0L) {
                continue;
            }
            changed = true;
            long remaining = pending.amount() - Math.min(inserted, pending.amount());
            if (remaining <= 0L) {
                iterator.remove();
            } else {
                iterator.set(new PendingSend(pending.target(), pending.key(), remaining));
            }
        }
        return changed;
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
            for (var entry : counter) {
                if (entry.getLongValue() > 0L) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean acceptsAny(PatternProviderTarget target, KeyCounter[] counters) {
        for (KeyCounter counter : counters) {
            for (var entry : counter) {
                if (entry.getLongValue() > 0L &&
                        target.insert(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE) <= 0L) {
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

    public record FallbackTarget(BlockPos position, Direction face, PatternProviderTarget target) {}

    private record MarkedInput(AEKey key, long amount, ResolvedTarget target) {}

    private record PendingSend(ResolvedTarget target, AEKey key, long amount) {}

    private static final class State {

        private final ObjectArrayList<PendingSend> pending = new ObjectArrayList<>();
    }
}
