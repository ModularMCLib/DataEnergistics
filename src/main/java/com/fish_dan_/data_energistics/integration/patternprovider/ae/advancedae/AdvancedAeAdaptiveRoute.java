package com.fish_dan_.data_energistics.integration.patternprovider.ae.advancedae;

import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatch;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchContext;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchTarget;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorMode;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorRouteTargets;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Advanced AE directional-input route registered by the Advanced AE integration.
 *
 * <p>
 * Directional delivery, remainder buffering, and returned-item filtering are
 * all owned here. The adaptive provider core only supplies generic target
 * operations through the dispatch API.
 * </p>
 */
public final class AdvancedAeAdaptiveRoute implements AdaptivePatternProviderDispatch {

    private static final String NBT_SEND_LIST = "adaptive_advanced_send_list";
    private static final String NBT_SEND_DIRECTION = "adaptive_advanced_send_direction";
    private static final String NBT_SEND_POSITION = "adaptive_advanced_send_position";
    private static final String NBT_DIRECTION_MAP = "adaptive_advanced_direction_map";

    @Override
    public String legacyStateKey() {
        return NBT_SEND_LIST;
    }

    @Override
    public boolean usesSpecialBatchRoute(IPatternDetails patternDetails) {
        return true;
    }

    @Override
    public boolean handles(AdaptivePatternProviderDispatchContext context) {
        return AdvancedAeCompat.hasDirectionalInputs(context.patternDetails());
    }

    /** Dispatches one Advanced AE directional pattern. */
    @Override
    public boolean dispatch(AdaptivePatternProviderDispatchContext dispatchContext) {
        AdaptivePatternProviderDispatchTarget target = dispatchContext.target();
        IPatternDetails patternDetails = dispatchContext.patternDetails();
        KeyCounter[] inputHolder = dispatchContext.inputHolder();
        if (target.isBusy() || !target.isActive() || !target.hasPattern(patternDetails) || target.isCraftingLocked() || hasPendingInput(target)) {
            return false;
        }

        Level level = target.level();
        if (level == null) {
            return false;
        }

        ObjectArrayList<FallbackTarget> candidates = new ObjectArrayList<>();
        for (ConnectorLink binding : ConnectorRouteTargets.resolve(target, ConnectorMode.INPUT)) {
            BlockPos adjacentPos = binding.position();
            Direction adjacentFace = binding.side();
            ICraftingMachine craftingMachine = ICraftingMachine.of(level, adjacentPos, adjacentFace);
            if (craftingMachine != null && craftingMachine.acceptsPlans()) {
                if (craftingMachine.pushPattern(patternDetails, inputHolder, adjacentFace)) {
                    target.patternSuccess(patternDetails);
                    return true;
                }
                continue;
            }
            PatternProviderTarget adapter = target.externalTarget(level, adjacentPos, adjacentFace);
            if (adapter != null && !target.isBlocked(adapter)) {
                candidates.add(new FallbackTarget(adjacentPos, adjacentFace, adapter));
            }
        }

        if (!patternDetails.supportsPushInputsToExternalInventory()) {
            return false;
        }

        rotateCandidates(candidates, target.roundRobinIndex());
        for (int index = 0; index < candidates.size(); index++) {
            FallbackTarget candidate = candidates.get(index);
            if (pushDirectionalInputs(target, candidate.position(), candidate.side(), inputHolder, patternDetails)) {
                target.advanceRoundRobin(index + 1);
                return true;
            }
        }
        return false;
    }

    /** Returns whether a prior directional dispatch still has buffered input. */
    @Override
    public boolean hasPendingInput(AdaptivePatternProviderDispatchTarget target) {
        return !target.routeState(State.class, State::new).sendList.isEmpty();
    }

    /** Returns whether buffered directional input keeps the provider awake. */
    @Override
    public boolean hasWork(AdaptivePatternProviderDispatchTarget target) {
        return hasPendingInput(target);
    }

    /** Flushes buffered directional input against the selected adjacent target. */
    @Override
    public boolean tick(AdaptivePatternProviderDispatchTarget target, int ticksSinceLastCall) {
        return flushSendList(target);
    }

    /** Writes directional remainder state using the legacy compatible keys. */
    @Override
    public void writeState(
                           AdaptivePatternProviderDispatchTarget target,
                           CompoundTag tag,
                           HolderLookup.Provider registries) {
        State state = target.routeState(State.class, State::new);
        ListTag sendList = new ListTag();
        for (var entry : state.sendList.object2LongEntrySet()) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                sendList.add(GenericStack.writeTag(
                        registries, new GenericStack(entry.getKey(), entry.getLongValue())));
            }
        }
        tag.put(NBT_SEND_LIST, sendList);
        if (state.sendDirection != null) {
            tag.putByte(NBT_SEND_DIRECTION, (byte) state.sendDirection.get3DDataValue());
        } else {
            tag.remove(NBT_SEND_DIRECTION);
        }
        if (state.sendPosition != null) tag.putLong(NBT_SEND_POSITION, state.sendPosition.asLong());
        else tag.remove(NBT_SEND_POSITION);

        ListTag directionMap = new ListTag();
        for (var entry : state.directionMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            CompoundTag value = new CompoundTag();
            value.put("aekey", entry.getKey().toTagGeneric(registries));
            Direction direction = entry.getValue();
            value.putByte("dir", direction == null ? (byte) -1 : (byte) direction.get3DDataValue());
            directionMap.add(value);
        }
        tag.put(NBT_DIRECTION_MAP, directionMap);
    }

    /** Restores directional remainder state. */
    @Override
    public void readState(
                          AdaptivePatternProviderDispatchTarget target,
                          CompoundTag tag,
                          HolderLookup.Provider registries) {
        State state = target.routeState(State.class, State::new);
        state.sendList.clear();
        state.directionMap.clear();
        state.sendDirection = null;
        ListTag sendList = tag.getList(NBT_SEND_LIST, Tag.TAG_COMPOUND);
        for (int index = 0; index < sendList.size(); index++) {
            GenericStack stack = GenericStack.readTag(registries, sendList.getCompound(index));
            if (stack != null && stack.what() != null && stack.amount() > 0) {
                state.sendList.addTo(stack.what(), stack.amount());
            }
        }
        if (tag.contains(NBT_SEND_DIRECTION)) {
            state.sendDirection = Direction.from3DDataValue(tag.getByte(NBT_SEND_DIRECTION));
        }
        state.sendPosition = tag.contains(NBT_SEND_POSITION) ? BlockPos.of(tag.getLong(NBT_SEND_POSITION)) : null;
        ListTag directionMap = tag.getList(NBT_DIRECTION_MAP, Tag.TAG_COMPOUND);
        for (int index = 0; index < directionMap.size(); index++) {
            CompoundTag value = directionMap.getCompound(index);
            AEKey key = AEKey.fromTagGeneric(registries, value.getCompound("aekey"));
            if (key == null) {
                continue;
            }
            byte rawDirection = value.getByte("dir");
            state.directionMap.put(key, rawDirection == -1 ? null : Direction.from3DDataValue(rawDirection));
        }
    }

    /** Adds buffered directional inputs to provider drops. */
    @Override
    public void addDropsFast(AdaptivePatternProviderDispatchTarget target, ObjectList<ItemStack> drops) {
        State state = target.routeState(State.class, State::new);
        for (var entry : state.sendList.object2LongEntrySet()) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                entry.getKey().addDrops(entry.getLongValue(), drops, target.level(), target.providerPos());
            }
        }
    }

    /** Clears all buffered directional state. */
    @Override
    public void clearState(AdaptivePatternProviderDispatchTarget target) {
        target.clearRouteState();
    }

    /** Applies the optional Advanced AE returned-item filter. */
    @Override
    public boolean allowsReturnItem(AdaptivePatternProviderDispatchTarget target, AEKey key) {
        if (!target.isFilteredImportEnabled()) {
            return true;
        }
        if (!target.trackedCraftsFast().isEmpty() && target.trackedCraftsFast().contains(key)) {
            return true;
        }
        return target.outputCacheFast().contains(key);
    }

    private static boolean pushDirectionalInputs(
                                                 AdaptivePatternProviderDispatchTarget target,
                                                 BlockPos adjacentPos,
                                                 Direction primaryDirection,
                                                 KeyCounter[] inputHolder,
                                                 IPatternDetails patternDetails) {
        Level level = target.level();
        if (level == null) {
            return false;
        }
        Direction defaultSide = primaryDirection.getOpposite();
        Object2ObjectOpenHashMap<AEKey, PatternProviderTarget> targetsByKey = new Object2ObjectOpenHashMap<>();
        Object2ObjectOpenHashMap<AEKey, Direction> inputDirections = new Object2ObjectOpenHashMap<>();

        for (KeyCounter input : inputHolder) {
            AEKey firstKey = input.getFirstKey();
            if (firstKey == null) {
                continue;
            }
            Direction inputSide = AdvancedAeCompat.getInputSide(patternDetails, firstKey);
            Direction targetSide = inputSide == null ? defaultSide : inputSide;
            PatternProviderTarget adapter = target.externalTarget(level, adjacentPos, targetSide);
            targetsByKey.put(firstKey, adapter);
            inputDirections.put(firstKey, inputSide);
            if (!acceptsItem(adapter, input)) {
                return false;
            }
        }

        patternDetails.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
            PatternProviderTarget adapter = targetsByKey.get(what);
            long inserted = adapter == null ? 0 : adapter.insert(what, amount, Actionable.MODULATE);
            if (inserted < amount) {
                queueRemainder(target, what, amount - inserted, adjacentPos, primaryDirection, inputDirections.get(what));
            }
        });

        target.patternSuccess(patternDetails);
        State state = target.routeState(State.class, State::new);
        state.sendDirection = primaryDirection;
        Map<AEKey, Direction> patternDirections = AdvancedAeCompat.getDirectionMap(patternDetails);
        state.directionMap.clear();
        if (patternDirections != null && !patternDirections.isEmpty()) {
            state.directionMap.putAll(patternDirections);
        } else {
            state.directionMap.putAll(inputDirections);
        }
        flushSendList(target);
        target.saveChanges();
        return true;
    }

    private static boolean acceptsItem(@Nullable PatternProviderTarget target, KeyCounter counter) {
        if (target == null) {
            return false;
        }
        for (var entry : counter) {
            if (target.insert(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE) == 0) {
                return false;
            }
        }
        return true;
    }

    private static void queueRemainder(
                                       AdaptivePatternProviderDispatchTarget target,
                                       AEKey key,
                                       long amount,
                                       BlockPos position,
                                       Direction primaryDirection,
                                       @Nullable Direction inputSide) {
        if (key == null || amount <= 0) {
            return;
        }
        State state = target.routeState(State.class, State::new);
        if (state.sendDirection == null) {
            state.sendDirection = primaryDirection;
        }
        state.sendPosition = position;
        state.sendList.addTo(key, amount);
        state.directionMap.put(key, inputSide);
        target.alertDevice();
    }

    private static boolean flushSendList(AdaptivePatternProviderDispatchTarget target) {
        State state = target.routeState(State.class, State::new);
        if (state.sendList.isEmpty()) {
            state.sendDirection = null;
            state.sendPosition = null;
            state.directionMap.clear();
            return false;
        }
        if (state.sendDirection == null) {
            return false;
        }
        Level level = target.level();
        if (level == null) {
            return false;
        }
        BlockPos adjacentPos = state.sendPosition != null ? state.sendPosition : target.providerPos().relative(state.sendDirection);
        Direction defaultSide = state.sendDirection.getOpposite();
        boolean changed = false;
        var iterator = state.sendList.object2LongEntrySet().iterator();
        while (iterator.hasNext()) {
            Object2LongMap.Entry<AEKey> entry = iterator.next();
            AEKey key = entry.getKey();
            long remaining = entry.getLongValue();
            if (key == null || remaining <= 0) {
                iterator.remove();
                continue;
            }
            Direction inputSide = state.directionMap.get(key);
            Direction targetSide = inputSide == null ? defaultSide : inputSide;
            PatternProviderTarget adapter = target.externalTarget(level, adjacentPos, targetSide);
            if (adapter == null) {
                continue;
            }
            long inserted = adapter.insert(key, remaining, Actionable.MODULATE);
            if (inserted > 0) {
                remaining -= inserted;
                changed = true;
            }
            if (remaining <= 0) {
                iterator.remove();
                state.directionMap.remove(key);
            } else {
                entry.setValue(remaining);
            }
        }
        if (state.sendList.isEmpty()) {
            state.sendDirection = null;
            state.sendPosition = null;
            state.directionMap.clear();
        }
        if (changed) {
            target.saveChanges();
        }
        return changed;
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

    private record FallbackTarget(BlockPos position, Direction side, PatternProviderTarget target) {}

    private static final class State {

        private final Object2LongOpenHashMap<AEKey> sendList = new Object2LongOpenHashMap<>();
        private final Object2ObjectOpenHashMap<AEKey, Direction> directionMap = new Object2ObjectOpenHashMap<>();
        private @Nullable Direction sendDirection;
        private @Nullable BlockPos sendPosition;
    }
}
