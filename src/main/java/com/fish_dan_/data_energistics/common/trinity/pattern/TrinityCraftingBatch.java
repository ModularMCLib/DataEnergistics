package com.fish_dan_.data_energistics.common.trinity.pattern;

import com.fish_dan_.data_energistics.common.crafting.trinity.serialization.TrinityBigIntegerEncoding;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;

/**
 * Immutable group of adjacent identical crafting dispatches accepted by one Trinity pattern slot.
 *
 * <p>
 * The group references a slot-local definition instead of retaining another encoded stack. All input access uses
 * defensive copies because {@link ItemStack} is mutable.
 * </p>
 */
public final class TrinityCraftingBatch {

    /**
     * A crafting pattern always receives a complete 3 by 3 input snapshot.
     */
    public static final int INPUT_SLOT_COUNT = 9;

    private static final String COUNT_TAG = "count";
    private static final String DEFINITION_ID_TAG = "definition_id";
    private static final String INPUTS_TAG = "inputs";
    private static final String MERGEABLE_TAG = "mergeable";
    private static final String QUEUED_TICK_TAG = "queued_tick";
    private static final String ROUTE_TAG = "route";
    private static final String SLOT_TAG = "slot";
    private static final String STACK_TAG = "stack";

    private final BigInteger count;
    private final TrinityPatternDefinition definition;
    private final InputSignature inputs;
    private final boolean mergeable;
    private final long queuedTick;
    private final PatternRoute route;

    /**
     * Creates one resolved queue group.
     *
     * @param queuedTick tick on which every dispatch in the group was accepted
     * @param route      exact host/core/slot destination selected by the crafting plan
     * @param definition slot-local complete pattern definition
     * @param inputs     exactly nine input stacks in row-major order
     * @param count      positive number of identical adjacent dispatches represented by this group
     * @param mergeable  whether later identical dispatches may extend this group
     */
    private TrinityCraftingBatch(long queuedTick, PatternRoute route, TrinityPatternDefinition definition,
                                 ObjectList<ItemStack> inputs, BigInteger count, boolean mergeable) {
        this(queuedTick, route, definition, InputSignature.copyOf(inputs), count, mergeable);
    }

    private TrinityCraftingBatch(long queuedTick, PatternRoute route, TrinityPatternDefinition definition,
                                 InputSignature inputs, BigInteger count, boolean mergeable) {
        if (queuedTick < 0L) {
            throw new IllegalArgumentException("Queued tick must not be negative: " + queuedTick);
        }
        if (count.signum() <= 0) {
            throw new IllegalArgumentException("Queued crafting count must be positive: " + count);
        }
        TrinityBigIntegerEncoding.encode(count, "queued crafting count");
        this.queuedTick = queuedTick;
        this.route = route;
        this.definition = definition;
        this.inputs = inputs;
        this.count = count;
        this.mergeable = mergeable;
    }

    /**
     * Creates one queue group from a newly accepted dispatch or validated persisted state.
     *
     * @param queuedTick tick on which every dispatch in the group was accepted
     * @param route      exact host/core/slot destination
     * @param definition slot-local complete pattern definition
     * @param inputs     exactly nine row-major inputs
     * @param count      positive logical craft count
     * @param mergeable  whether later exact dispatches may extend the group
     * @return resolved counted group
     */
    public static TrinityCraftingBatch resolved(long queuedTick, PatternRoute route,
                                                TrinityPatternDefinition definition, ObjectList<ItemStack> inputs,
                                                long count, boolean mergeable) {
        return resolved(queuedTick, route, definition, inputs, BigInteger.valueOf(count), mergeable);
    }

    /** Creates one exact queue group without splitting its logical count into long-sized groups. */
    public static TrinityCraftingBatch resolved(long queuedTick, PatternRoute route,
                                                TrinityPatternDefinition definition, ObjectList<ItemStack> inputs,
                                                BigInteger count, boolean mergeable) {
        return new TrinityCraftingBatch(queuedTick, route, definition, inputs, count, mergeable);
    }

    static TrinityCraftingBatch resolved(long queuedTick,
                                         PatternRoute route,
                                         TrinityPatternDefinition definition,
                                         InputSignature inputs,
                                         long count,
                                         boolean mergeable) {
        return resolved(queuedTick, route, definition, inputs, BigInteger.valueOf(count), mergeable);
    }

    static TrinityCraftingBatch resolved(long queuedTick,
                                         PatternRoute route,
                                         TrinityPatternDefinition definition,
                                         InputSignature inputs,
                                         BigInteger count,
                                         boolean mergeable) {
        return new TrinityCraftingBatch(queuedTick, route, definition, inputs, count, mergeable);
    }

    /**
     * @return positive logical craft count as a long
     * @throws ArithmeticException when the group requires {@link #exactCount()}
     */
    public long count() {
        return this.count.longValueExact();
    }

    /** @return the complete positive logical craft count, including quantities beyond long */
    public BigInteger exactCount() {
        return this.count;
    }

    /**
     * @return slot-local definition ID referenced by this group
     */
    public long definitionId() {
        return this.definition.id();
    }

    /**
     * @return immutable referenced definition
     */
    public TrinityPatternDefinition definition() {
        return this.definition;
    }

    /**
     * @return defensive copies of all nine row-major crafting inputs
     */
    public ObjectList<ItemStack> inputs() {
        return this.inputs.copyStacks();
    }

    /**
     * @return whether this group may merge with a later exact dispatch
     */
    public boolean mergeable() {
        return this.mergeable;
    }

    /**
     * @return defensive copy of the encoded pattern referenced by this group
     */
    public ItemStack patternSnapshot() {
        return this.definition.pattern();
    }

    /**
     * @return tick on which this group was enqueued
     */
    public long queuedTick() {
        return this.queuedTick;
    }

    /**
     * @return immutable host/core/slot route that owns this group and its outputs
     */
    public PatternRoute route() {
        return this.route;
    }

    /**
     * Tests whether the currently installed definition and resolution exactly own this group.
     *
     * @param installedDefinition current slot definition
     * @return whether this group may execute against the supplied definition
     */
    public boolean matchesDefinition(TrinityPatternDefinition installedDefinition) {
        return this.definition == installedDefinition;
    }

    /**
     * Tests whether the currently installed pattern is the exact definition captured by this group.
     *
     * @param pattern currently installed encoded pattern
     * @return true when item, components, and count match
     */
    public boolean matchesPattern(ItemStack pattern) {
        return this.definition.matchesPattern(pattern);
    }

    /**
     * Projects a compatible later group's transferable count into one legacy long-sized request.
     *
     * @param later later adjacent group candidate
     * @return transferable legacy chunk, or zero when the merge key differs
     */
    long mergeableCount(TrinityCraftingBatch later) {
        return exactMergeableCount(later).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }

    BigInteger exactMergeableCount(TrinityCraftingBatch later) {
        if (!this.mergeable || !later.mergeable ||
                this.queuedTick != later.queuedTick || !this.route.equals(later.route) ||
                this.definition != later.definition ||
                this.count.add(later.count).bitLength() >= TrinityBigIntegerEncoding.MAX_BYTES * Byte.SIZE) {
            return BigInteger.ZERO;
        }
        return this.inputs.matches(later.inputs) ? later.count : BigInteger.ZERO;
    }

    /**
     * Combines a long-sized portion of a compatible later group into the exact retained tail.
     *
     * @param later      adjacent later group
     * @param laterCount positive count to transfer from the later group
     * @return one group containing the transferred logical count
     */
    TrinityCraftingBatch mergedWith(TrinityCraftingBatch later, long laterCount) {
        return mergedWith(later, BigInteger.valueOf(laterCount));
    }

    TrinityCraftingBatch mergedWith(TrinityCraftingBatch later, BigInteger laterCount) {
        if (laterCount.signum() <= 0 || laterCount.compareTo(exactMergeableCount(later)) > 0) {
            throw new IllegalArgumentException("Trinity crafting groups cannot merge count " + laterCount);
        }
        return withCount(this.count.add(laterCount));
    }

    /**
     * @param count replacement positive logical count
     * @return copy of this exact group with the replacement count
     */
    TrinityCraftingBatch withCount(long count) {
        return withCount(BigInteger.valueOf(count));
    }

    TrinityCraftingBatch withCount(BigInteger count) {
        return new TrinityCraftingBatch(
                this.queuedTick,
                this.route,
                this.definition,
                this.inputs,
                count,
                this.mergeable);
    }

    /**
     * @return detached immutable copy that shares only the immutable definition value
     */
    public TrinityCraftingBatch copy() {
        return new TrinityCraftingBatch(this.queuedTick, this.route, this.definition, this.inputs, this.count,
                this.mergeable);
    }

    CompoundTag writeToTag(HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();
        data.putByteArray(COUNT_TAG, TrinityBigIntegerEncoding.encode(this.count, "queued crafting count"));
        data.putLong(DEFINITION_ID_TAG, this.definition.id());
        data.putBoolean(MERGEABLE_TAG, this.mergeable);
        data.putLong(QUEUED_TICK_TAG, this.queuedTick);
        data.put(ROUTE_TAG, this.route.writeToTag());
        data.put(INPUTS_TAG, writeInputs(registries));
        return data;
    }

    static TrinityCraftingBatch readFromTag(CompoundTag data, TrinityPatternDefinition definition,
                                            HolderLookup.Provider registries) {
        if (!data.contains(DEFINITION_ID_TAG, Tag.TAG_LONG) ||
                !data.contains(MERGEABLE_TAG, Tag.TAG_BYTE) || !data.contains(QUEUED_TICK_TAG, Tag.TAG_LONG) ||
                !data.contains(ROUTE_TAG, Tag.TAG_COMPOUND) || !data.contains(INPUTS_TAG, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Queued crafting group is incomplete");
        }
        if (data.getLong(DEFINITION_ID_TAG) != definition.id()) {
            throw new IllegalArgumentException("Queued crafting group references the wrong definition");
        }
        return new TrinityCraftingBatch(
                data.getLong(QUEUED_TICK_TAG),
                PatternRoute.readFromTag(data.getCompound(ROUTE_TAG)),
                definition,
                readInputs(data.getList(INPUTS_TAG, Tag.TAG_COMPOUND), registries),
                TrinityBigIntegerEncoding.readTag(data, COUNT_TAG, "queued crafting count"),
                data.getBoolean(MERGEABLE_TAG));
    }

    private ListTag writeInputs(HolderLookup.Provider registries) {
        ListTag inputList = new ListTag();
        for (int slot = 0; slot < this.inputs.size(); slot++) {
            ItemStack input = this.inputs.stack(slot);
            if (input.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(SLOT_TAG, slot);
            entry.put(STACK_TAG, input.saveOptional(registries));
            inputList.add(entry);
        }
        return inputList;
    }

    private static ObjectList<ItemStack> readInputs(ListTag inputList, HolderLookup.Provider registries) {
        ObjectList<ItemStack> inputs = emptyInputs();
        boolean[] populatedSlots = new boolean[INPUT_SLOT_COUNT];
        for (int index = 0; index < inputList.size(); index++) {
            CompoundTag entry = inputList.getCompound(index);
            if (!entry.contains(SLOT_TAG, Tag.TAG_INT) || !entry.contains(STACK_TAG, Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Queued crafting input entry is incomplete");
            }
            int slot = entry.getInt(SLOT_TAG);
            if (slot < 0 || slot >= INPUT_SLOT_COUNT) {
                throw new IllegalArgumentException("Queued crafting input slot out of range: " + slot);
            }
            if (populatedSlots[slot]) {
                throw new IllegalArgumentException("Duplicate queued crafting input slot: " + slot);
            }
            populatedSlots[slot] = true;
            ItemStack input = ItemStack.parseOptional(registries, entry.getCompound(STACK_TAG));
            if (input.isEmpty()) {
                throw new IllegalArgumentException("Queued crafting input " + slot + " is empty");
            }
            inputs.set(slot, input);
        }
        validateInputs(inputs);
        return inputs;
    }

    private static ObjectList<ItemStack> emptyInputs() {
        ObjectArrayList<ItemStack> inputs = new ObjectArrayList<>(INPUT_SLOT_COUNT);
        for (int slot = 0; slot < INPUT_SLOT_COUNT; slot++) {
            inputs.add(ItemStack.EMPTY);
        }
        return inputs;
    }

    private static void validateInputs(ObjectList<ItemStack> inputs) {
        if (inputs.size() != INPUT_SLOT_COUNT) {
            throw new IllegalArgumentException(
                    "A queued crafting group requires exactly " + INPUT_SLOT_COUNT + " inputs, got " + inputs.size());
        }
        if (inputs.stream().allMatch(ItemStack::isEmpty)) {
            throw new IllegalArgumentException("A queued crafting group must contain at least one input");
        }
        for (int slot = 0; slot < inputs.size(); slot++) {
            ItemStack input = inputs.get(slot);
            if (!input.isEmpty() && input.getCount() > input.getMaxStackSize()) {
                throw new IllegalArgumentException(
                        "Queued crafting input " + slot + " exceeds its maximum stack size: " + input.getCount());
            }
        }
    }

    private static ObjectList<ItemStack> copyStacks(ObjectList<ItemStack> stacks) {
        ObjectArrayList<ItemStack> copy = new ObjectArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copy.add(stack.copy());
        }
        return new ObjectImmutableList<>(copy);
    }

    private static boolean stackListsMatch(ObjectList<ItemStack> first, ObjectList<ItemStack> second) {
        for (int slot = 0; slot < INPUT_SLOT_COUNT; slot++) {
            if (!ItemStack.matches(first.get(slot), second.get(slot))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Immutable exact input token shared by a host route binding and its queued batch.
     *
     * <p>
     * Stacks are captured once at the enqueue or persistence boundary. Public access still creates defensive copies,
     * so recipe implementations and events can never mutate queued state.
     * </p>
     */
    public static final class InputSignature {

        private final ObjectList<ItemStack> stacks;

        private InputSignature(ObjectList<ItemStack> stacks, boolean copyStacks) {
            validateInputs(stacks);
            this.stacks = copyStacks ? TrinityCraftingBatch.copyStacks(stacks) : new ObjectImmutableList<>(stacks);
        }

        /**
         * Captures an arbitrary caller-owned crafting grid using defensive stack copies.
         *
         * @param stacks exactly nine row-major inputs
         * @return immutable exact input signature
         */
        public static InputSignature copyOf(ObjectList<ItemStack> stacks) {
            return new InputSignature(stacks, true);
        }

        /** Captures the catalog's already-isolated stack copies without copying every stack a second time. */
        static InputSignature takeOwnership(ObjectList<ItemStack> stacks) {
            return new InputSignature(stacks, false);
        }

        private int size() {
            return this.stacks.size();
        }

        private ItemStack stack(int slot) {
            return this.stacks.get(slot);
        }

        ObjectList<ItemStack> copyStacks() {
            return TrinityCraftingBatch.copyStacks(this.stacks);
        }

        private boolean matches(InputSignature other) {
            return stackListsMatch(this.stacks, other.stacks);
        }
    }
}
