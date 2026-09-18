package com.fish_dan_.data_energistics.common.trinity.pattern;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingCustodyCensus;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdLookup;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.custody.ReusableCustodyAggregation;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.custody.ReusableCustodyArchive;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Host;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.TrinityReusableSlot;
import com.fish_dan_.data_energistics.common.crafting.trinity.serialization.TrinityBigIntegerEncoding;
import com.fish_dan_.data_energistics.common.trinity.core.TrinityPatternCoreTier;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Default state implementation for {@link TrinityPatternCore}.
 *
 * <p>
 * The owning block entity supplies decoding, recipe identity resolution, and typed change callbacks. Each physical
 * slot owns its stable definition table, counted FIFO, and counted pending outputs; this core owns only cross-slot
 * publication, sparse indexes, refund transactions, and atomic persistence.
 * </p>
 */
public final class PersistentTrinityPatternCore implements TrinityPatternCore {

    private static final int CURRENT_STATE_VERSION = 6;
    private static final int REUSABLE_STATE_VERSION = 5;
    private static final int COUNTED_STATE_VERSION = 4;
    private static final int POWER_OF_TWO_CAPACITY_STATE_VERSION = 3;
    private static final String AMOUNT_TAG = "amount";
    private static final String CORE_ID_TAG = "core_id";
    private static final String HOST_ID_TAG = "host_id";
    private static final String ITEMS_TAG = "items";
    private static final String PATTERN_CAPACITY_TAG = "pattern_capacity";
    private static final String PATTERN_REFUNDS_TAG = "patterns";
    private static final String PROTOTYPE_TAG = "prototype";
    private static final String REFUND_OUTBOX_TAG = "refund_outbox";
    private static final String RETAINED_REFUNDS_TAG = "retained";
    private static final String SLOT_TAG = "slot";
    private static final String SLOTS_TAG = "slots";
    private static final String STACK_TAG = "stack";
    private static final String VERSION_TAG = "version";
    private static final String REUSABLE_SLOTS_TAG = "reusable_slots";
    private static final String CUSTODY_ARCHIVE_TAG = "reusable_custody_archive";

    private final int patternCapacity;
    private final PatternDecoder decoder;
    private final TrinityPatternRecipeIdLookup recipeIdResolvers;
    private final TrinityPatternSlot.ChangeListener changeListener;
    private final ObjectList<TrinityPatternSlot> slots;
    /**
     * Constant-time slot lookup for stable occupied-directory entries.
     */
    private Int2ObjectMap<CachedPattern> cachedPatterns = new Int2ObjectOpenHashMap<>();
    private Int2ObjectMap<TrinityReusableSlot> reusableSlots = new Int2ObjectOpenHashMap<>();
    private ReusableCustodyArchive custodyArchive = new ReusableCustodyArchive();
    private final ReusableCustodyAggregation custodyAggregation = new ReusableCustodyAggregation();
    /**
     * Slots retaining an encoded pattern, ordered for reload and persistence without a capacity scan.
     */
    private IntAVLTreeSet occupiedPatternSlots = new IntAVLTreeSet();
    /** Derived sparse work indexes shared by queue, output and reusable-session mutations. */
    private final TrinityPatternCoreWorkIndexes workIndexes = new TrinityPatternCoreWorkIndexes();
    /**
     * Installed patterns cleared for refund but not yet confirmed by an external destination.
     */
    private ObjectList<PatternRefundEntry> patternRefundOutbox = new ObjectArrayList<>();
    /**
     * Host-isolated FIFO entries cleared for refund but not yet confirmed by an external destination.
     */
    private Object2ObjectMap<UUID, ObjectArrayList<RetainedRefundEntry>> retainedRefundOutboxByHost = new Object2ObjectRBTreeMap<>();
    private final InternalInventory patternInventory = new PatternInventory();

    private UUID coreId;
    private long revision;
    private long stateRevision;
    /**
     * Latest structurally immutable occupied directory; runtime bindings change through stable entries.
     */
    private PatternCacheSnapshot patternCacheSnapshot = new PatternCacheSnapshot(0L, ObjectList.of());
    /**
     * Latest immutable occupied-slot index, replaced only with the pattern directory.
     */
    private IntList occupiedPatternSlotSnapshot = IntList.of();
    @Nullable
    private CoreRefundTransaction activeRefundTransaction;
    private PersistentTrinityPatternCore.@Nullable ReversiblePatternRefundTransaction activePatternRefundTransaction;

    /**
     * Creates a fresh pattern core with explicit identity resolution and typed changes.
     *
     * @param patternCapacity   one of the three physical P-core capacities
     * @param decoder           level-aware supported-pattern decoder
     * @param recipeIdResolvers registry used to resolve stable recipe identities
     * @param changeListener    typed owner callback
     */
    public PersistentTrinityPatternCore(int patternCapacity, PatternDecoder decoder,
                                        TrinityPatternRecipeIdLookup recipeIdResolvers,
                                        TrinityPatternSlot.ChangeListener changeListener) {
        this(patternCapacity, UUID.randomUUID(), decoder, recipeIdResolvers, changeListener);
    }

    /**
     * Creates a pattern core with explicit identity, resolver registry, and typed changes.
     *
     * @param patternCapacity   one of the three physical P-core capacities
     * @param coreId            persistent identity
     * @param decoder           level-aware supported-pattern decoder
     * @param recipeIdResolvers registry used to resolve stable recipe identities
     * @param changeListener    typed owner callback
     */
    public PersistentTrinityPatternCore(int patternCapacity, UUID coreId, PatternDecoder decoder,
                                        TrinityPatternRecipeIdLookup recipeIdResolvers,
                                        TrinityPatternSlot.ChangeListener changeListener) {
        validateCapacity(patternCapacity);
        this.patternCapacity = patternCapacity;
        this.coreId = coreId;
        this.decoder = decoder;
        this.recipeIdResolvers = recipeIdResolvers;
        this.changeListener = changeListener;
        this.slots = new ObjectArrayList<>(patternCapacity);
        for (int slot = 0; slot < patternCapacity; slot++) {
            this.slots.add(newSlot(slot));
        }
    }

    @Override
    public UUID coreId() {
        return this.coreId;
    }

    @Override
    public int patternCapacity() {
        return this.patternCapacity;
    }

    @Override
    public TrinityPatternSlot patternSlot(int slot) {
        return slot(slot);
    }

    @Override
    public long revision() {
        return this.revision;
    }

    @Override
    public PatternCacheSnapshot patternCacheSnapshot() {
        return this.patternCacheSnapshot;
    }

    @Nullable
    @Override
    public CachedPattern cachedPattern(int slot) {
        checkSlot(slot);
        return this.cachedPatterns.get(slot);
    }

    @Override
    public IntList occupiedPatternSlots() {
        return this.occupiedPatternSlotSnapshot;
    }

    @Override
    public InternalInventory patternInventory() {
        return this.patternInventory;
    }

    @Override
    public ItemStack pattern(int slot) {
        return slot(slot).pattern();
    }

    @Override
    public boolean trySetPattern(int slot, ItemStack pattern) {
        ensureNoActiveRefundTransaction();
        return slot(slot).trySetPattern(pattern);
    }

    @Nullable
    @Override
    public IMolecularAssemblerSupportedPattern decodedPattern(int slot) {
        return slot(slot).decodedPattern();
    }

    @Override
    public void refreshPatternCache(int slot) {
        ensureNoActiveRefundTransaction();
        slot(slot).refreshPatternCache();
    }

    @Override
    public void refreshAllPatternCaches() {
        ensureNoActiveRefundTransaction();
        for (int slot : this.occupiedPatternSlots) {
            this.slots.get(slot).refreshPatternCache();
        }
    }

    @Override
    public void ensurePatternCachesCurrent() {
        ensureNoActiveRefundTransaction();
    }

    @Override
    public boolean enqueueBatch(PatternRoute route, ItemStack patternSnapshot, ObjectList<ItemStack> inputs, long queuedTick) {
        ensureNoActiveRefundTransaction();
        validateOwnedRoute(route);
        TrinityReusableSlot reusable = this.reusableSlots.get(route.slot());
        if (reusable != null && reusable.hasWork()) {
            return false;
        }
        ensurePersistentRevisionAvailable();
        return slot(route.slot()).enqueue(route, patternSnapshot, new ObjectArrayList<>(inputs), queuedTick);
    }

    @Override
    public boolean enqueueBatch(PatternRoute route,
                                CachedPattern expectedPattern,
                                long expectedRuntimeBindingRevision,
                                TrinityCraftingBatch.InputSignature inputs,
                                long queuedTick,
                                long count) {
        return enqueueBatch(route, expectedPattern, expectedRuntimeBindingRevision, inputs, queuedTick, BigInteger.valueOf(count));
    }

    @Override
    public boolean enqueueBatch(PatternRoute route,
                                CachedPattern expectedPattern,
                                long expectedRuntimeBindingRevision,
                                TrinityCraftingBatch.InputSignature inputs,
                                long queuedTick,
                                BigInteger count) {
        ensureNoActiveRefundTransaction();
        validateOwnedRoute(route);
        TrinityPatternSlot slot = slot(route.slot());
        TrinityReusableSlot reusable = this.reusableSlots.get(route.slot());
        if (reusable != null && reusable.hasWork()) {
            return false;
        }
        if (this.cachedPatterns.get(route.slot()) != expectedPattern ||
                expectedPattern.runtimeBindingRevision() != expectedRuntimeBindingRevision) {
            return false;
        }
        ensurePersistentRevisionAvailable();
        return slot.enqueueCached(route, expectedPattern.definition(), inputs, queuedTick, count);
    }

    @Override
    public ObjectList<TrinityCraftingBatch> queuedBatches(int slot) {
        return slot(slot).queuedBatches();
    }

    @Override
    public Int2ObjectMap<TrinityReusableSlot> reusableSlots() {
        return Int2ObjectMaps.unmodifiable(new Int2ObjectOpenHashMap<>(this.reusableSlots));
    }

    public @Nullable TrinityReusableSlot reusableSlot(int slot) {
        return this.reusableSlots.get(slot);
    }

    /** Evidence follows the physical core even after a slot's old route and its CLOSED endpoint are replaced. */
    public ReusableCraftingCustodyCensus reusableCustody(String cpuOwner) {
        ObjectList<ReusableCraftingCustodyCensus> sources = new ObjectArrayList<>(this.reusableSlots.size() + 1);
        sources.add(this.custodyArchive.census(cpuOwner));
        for (TrinityReusableSlot slot : this.reusableSlots.values()) {
            sources.add(slot.endpoint().reusableCustody(cpuOwner));
        }
        return this.custodyAggregation.census(cpuOwner, true, sources);
    }

    public @Nullable ReusableCraftingAdmission prepareReusable(PatternRoute route, ReusableCraftingRequest request, long tick, Host host) {
        ensureNoActiveRefundTransaction();
        validateOwnedRoute(route);
        if (slot(route.slot()).hasQueuedWork()) {
            return null;
        }
        TrinityReusableSlot existing = this.reusableSlots.get(route.slot());
        if (existing != null && existing.hasWork() && (!existing.route().equals(route) || existing.closeRequested())) {
            return null;
        }
        TrinityReusableSlot target = existing == null || !existing.route().equals(route) ? new TrinityReusableSlot(route) : existing;
        ReusableCraftingAdmission prepared = target.endpoint().prepare(request, tick, host);
        if (prepared == null) {
            return null;
        }
        return new ReusableCraftingAdmission() {

            @Override
            public long count() {
                return prepared.count();
            }

            @Override
            public ObjectList<SlotStack> physicalInputsFast() {
                return prepared.physicalInputsFast();
            }

            @Override
            public boolean replay() {
                return prepared.replay();
            }

            @Override
            public boolean hasTransferredInputOwnership() {
                return prepared.hasTransferredInputOwnership();
            }

            @Override
            public boolean commit(KeyCounter[] delivery) {
                if (slot(route.slot()).hasQueuedWork() || reusableSlots.get(route.slot()) != existing || target.closeRequested() && target.hasWork()) {
                    return false;
                }
                try {
                    return prepared.commit(delivery);
                } finally {
                    if (prepared.hasTransferredInputOwnership()) {
                        target.clearCloseRequest();
                        if (existing != null && target != existing) {
                            custodyArchive.retain(existing.endpoint().acknowledgedCustody());
                        }
                        reusableSlots.put(route.slot(), target);
                        reusableStateChanged(route.slot());
                    }
                }
            }
        };
    }

    /** Records changes to the same persistent core state and publishes its combined legacy/reusable work index. */
    public void reusableStateChanged(int slot) {
        ensureNoActiveRefundTransaction();
        markPersistentChanged(slot);
        updateSlotWorkIndexes(slot);
    }

    @Override
    public int queuedBatchCount(int slot) {
        return slot(slot).queuedBatchCount();
    }

    @Override
    public int queuedBatchCount() {
        int count = 0;
        for (int slot : this.workIndexes.queuedSlots()) {
            count = Math.addExact(count, this.slots.get(slot).queuedBatchCount());
        }
        return count;
    }

    @Override
    public int executeReadyBatches(long currentTick, BatchExecutor executor) {
        ensureNoActiveRefundTransaction();
        validateCurrentTick(currentTick);
        int completedGroups = 0;
        for (int slotIndex : this.workIndexes.queuedSlots().toIntArray()) {
            completedGroups = Math.addExact(
                    completedGroups,
                    executeReadyBatchesInSlot(slotIndex, currentTick, executor));
        }
        return completedGroups;
    }

    @Override
    public int executeReadyBatches(int slot, long currentTick, BatchExecutor executor) {
        ensureNoActiveRefundTransaction();
        checkSlot(slot);
        validateCurrentTick(currentTick);
        return executeReadyBatchesInSlot(slot, currentTick, executor);
    }

    @Override
    public ObjectList<TrinityItemAmount> pendingOutputs(PatternRoute route) {
        validateOwnedRoute(route);
        return slot(route.slot()).pendingOutputs(route);
    }

    @Override
    public void appendPendingOutputs(PatternRoute route, ObjectList<TrinityItemAmount> outputs) {
        ensureNoActiveRefundTransaction();
        validateOwnedRoute(route);
        slot(route.slot()).appendPendingOutputs(route, new ObjectArrayList<>(outputs));
    }

    @Override
    public TrinityPatternOutputRouter.PendingOutputCursor openPendingOutputCursor(PatternRoute route) {
        ensureNoActiveRefundTransaction();
        validateOwnedRoute(route);
        return slot(route.slot()).openPendingOutputCursor(route);
    }

    @Override
    public IntList pendingOutputSlots(UUID hostId) {
        return this.workIndexes.pendingOutputSlots(hostId);
    }

    @Override
    public IntList workingSlots(UUID hostId) {
        return this.workIndexes.workingSlots(hostId);
    }

    @Override
    public boolean isSlotWorking(UUID hostId, int slot) {
        checkSlot(slot);
        return this.workIndexes.isSlotWorking(hostId, slot);
    }

    @Override
    public boolean hasWork() {
        return this.workIndexes.hasWork();
    }

    @Override
    public boolean hasWork(UUID hostId) {
        return this.workIndexes.hasWork(hostId);
    }

    @Override
    public boolean hasPendingRefund(UUID hostId) {
        ObjectArrayList<RetainedRefundEntry> entries = this.retainedRefundOutboxByHost.get(hostId);
        return entries != null && !entries.isEmpty();
    }

    @Override
    public PatternRefundTransaction preparePatternRefund() {
        if (this.activeRefundTransaction != null || this.activePatternRefundTransaction != null) {
            throw new IllegalStateException("Trinity pattern core " + this.coreId + " is already processing a refund");
        }
        if (hasWork()) {
            return new ReversiblePatternRefundTransaction(-1L, ObjectList.of(), ObjectList.of(), 0, true);
        }
        ObjectArrayList<PatternRefundSlot> capturedSlots = new ObjectArrayList<>(this.occupiedPatternSlots.size());
        ObjectArrayList<PatternRefundEntry> offeredPatterns = new ObjectArrayList<>(this.patternRefundOutbox);
        int outboxSize = offeredPatterns.size();
        for (int slot : this.occupiedPatternSlots) {
            TrinityPatternSlot patternSlot = this.slots.get(slot);
            ItemStack pattern = patternSlot.pattern();
            if (!pattern.isEmpty()) {
                capturedSlots.add(new PatternRefundSlot(slot, patternSlot.revision(), pattern));
                offeredPatterns.add(new PatternRefundEntry(slot, pattern));
            }
        }
        ReversiblePatternRefundTransaction transaction = new ReversiblePatternRefundTransaction(
                this.stateRevision,
                new ObjectImmutableList<>(capturedSlots),
                new ObjectImmutableList<>(offeredPatterns),
                outboxSize,
                false);
        this.activePatternRefundTransaction = transaction;
        return transaction;
    }

    @Override
    public RefundTransaction prepareRefund() {
        requestReusableClosure(null);
        return beginRefund(captureRefundState(null));
    }

    @Override
    public RefundTransaction prepareRefund(UUID hostId) {
        requestReusableClosure(hostId);
        return beginRefund(captureRefundState(hostId));
    }

    @Override
    public boolean tryRefundAll(TrinityRefundDelivery delivery) {
        RefundTransaction transaction = prepareRefund();
        ObjectList<TrinityItemAmount> refundable = transaction.refundableItems();
        boolean committed = false;
        try {
            if (refundable.isEmpty() || !delivery.prepare(refundable)) {
                transaction.rollback();
                return false;
            }
            if (!transaction.commit()) {
                transaction.rollback();
                return false;
            }
            committed = true;
            ObjectList<TrinityItemAmount> undelivered = refundable;
            try {
                undelivered = delivery.deliver(refundable);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Trinity pattern core {} refund delivery failed after queued state was committed",
                        this.coreId,
                        exception);
            }
            transaction.complete(undelivered);
            return undelivered.isEmpty();
        } catch (RuntimeException exception) {
            if (committed) {
                try {
                    transaction.complete(refundable);
                } catch (RuntimeException completionFailure) {
                    exception.addSuppressed(completionFailure);
                }
            } else {
                transaction.rollback();
            }
            Data_Energistics.LOGGER.error(
                    "Failed to prepare Trinity pattern core {} refund delivery; retained queued state",
                    this.coreId,
                    exception);
            return false;
        }
    }

    @Override
    public void writeToTag(CompoundTag data, HolderLookup.Provider registries) {
        writeCurrentSchemaHeader(data);
        data.putUUID(CORE_ID_TAG, this.coreId);
        data.putInt(PATTERN_CAPACITY_TAG, this.patternCapacity);
        ListTag slotEntries = new ListTag();
        for (int slot : persistentSlots()) {
            slotEntries.add(this.slots.get(slot).writeToTag(registries));
        }
        data.put(SLOTS_TAG, slotEntries);
        data.put(REFUND_OUTBOX_TAG, writeRefundOutbox(registries));
        data.put(REUSABLE_SLOTS_TAG, writeReusableSlots(registries, false));
        data.put(CUSTODY_ARCHIVE_TAG, this.custodyArchive.writeToTag());
    }

    /**
     * Replaces persisted core state with the exact queued-input and pending-output snapshot that remains after
     * installed patterns become independent mining drops.
     *
     * <p>
     * The resulting state keeps the core identity, capacity, route-owned work, and queue definitions, while
     * omitting every installed pattern. That preserves refundability without allowing a restored core to execute a
     * retained batch before a new valid pattern is installed.
     * </p>
     *
     * @param data       destination block-entity state
     * @param registries item component registry access
     */
    public void writeRetainedWorkToTag(CompoundTag data, HolderLookup.Provider registries) {
        ensureNoActiveRefundTransaction();
        IntAVLTreeSet retainedSlots = new IntAVLTreeSet(this.workIndexes.queuedSlots());
        retainedSlots.addAll(this.workIndexes.pendingOutputSlots());
        if (retainedSlots.isEmpty() &&
                this.patternRefundOutbox.isEmpty() &&
                this.retainedRefundOutboxByHost.isEmpty() && this.reusableSlots.isEmpty() && this.custodyArchive.isEmpty()) {
            clearCoreStateTags(data);
            return;
        }

        writeCurrentSchemaHeader(data);
        data.putUUID(CORE_ID_TAG, this.coreId);
        data.putInt(PATTERN_CAPACITY_TAG, this.patternCapacity);
        ListTag slotEntries = new ListTag();
        for (int slot : retainedSlots) {
            slotEntries.add(this.slots.get(slot).writeRetainedWorkToTag(registries));
        }
        data.put(SLOTS_TAG, slotEntries);
        data.put(REFUND_OUTBOX_TAG, writeRefundOutbox(registries));
        data.put(REUSABLE_SLOTS_TAG, writeReusableSlots(registries, true));
        data.put(CUSTODY_ARCHIVE_TAG, this.custodyArchive.writeToTag());
    }

    @Override
    public void hydrateFromTag(CompoundTag data, HolderLookup.Provider registries) {
        hydrateFromTagAndReportMigration(data, registries);
    }

    /**
     * Hydrates a pristine core and reports a 3.1.3 upgrade or unavailable pattern work moved into the refund outbox.
     *
     * @param data       source block-entity state
     * @param registries item component registry access
     * @return whether the accepted state must be persisted in its current form
     */
    public boolean hydrateFromTagAndReportMigration(CompoundTag data, HolderLookup.Provider registries) {
        if (this.stateRevision != 0L || this.revision != 0L || !persistentSlots().isEmpty()) {
            throw new IllegalStateException("Only a pristine Trinity pattern core may hydrate persisted state");
        }
        return applyPersistedState(data, registries, false);
    }

    @Override
    public void readFromTag(CompoundTag data, HolderLookup.Provider registries) {
        readFromTagAndReportMigration(data, registries);
    }

    /**
     * Restores a live core and reports a 3.1.3 upgrade or unavailable pattern work moved into the refund outbox.
     *
     * @param data       source block-entity state
     * @param registries item component registry access
     * @return whether the accepted state must be persisted in its current form
     */
    public boolean readFromTagAndReportMigration(CompoundTag data, HolderLookup.Provider registries) {
        return applyPersistedState(data, registries, true);
    }

    private boolean applyPersistedState(CompoundTag data,
                                        HolderLookup.Provider registries,
                                        boolean notifyChanges) {
        ensureNoActiveRefundTransaction();
        if (!data.hasUUID(CORE_ID_TAG)) {
            if (containsCoreState(data)) {
                throw new IllegalArgumentException("Persisted Trinity pattern core state is missing its UUID");
            }
            return false;
        }
        int version = validatePersistedSchema(data);
        int persistedCapacity = validatePersistedCapacity(data, version);
        UUID loadedId = data.getUUID(CORE_ID_TAG);
        Int2ObjectMap<TrinityReusableSlot> loadedReusable = readReusableSlots(data, registries, loadedId, version);
        ReusableCustodyArchive loadedCustody = readCustodyArchive(data, loadedId, version);
        boolean identityChanged = !this.coreId.equals(loadedId);
        Int2ObjectMap<TrinityPatternSlot> loadedSlots = readSlots(requiredCompoundList(data, SLOTS_TAG), registries, loadedId);
        for (TrinityReusableSlot reusable : loadedReusable.values()) {
            TrinityPatternSlot restoredSlot = loadedSlots.get(reusable.route().slot());
            reusable.validateRestoredPublication(restoredSlot == null ? null : restoredSlot.decodedPattern(), registries);
        }
        RefundOutbox loadedOutbox = readRefundOutbox(data, registries);
        validatePersistedSlotBounds(loadedSlots, loadedOutbox, persistedCapacity);
        InvalidPatternWorkMigration invalidPatternWorkMigration = migrateInvalidLoadedPatternWork(
                loadedSlots,
                loadedOutbox);
        loadedOutbox = invalidPatternWorkMigration.outbox();

        IntAVLTreeSet loadedOccupiedSlots = occupiedSlots(loadedSlots.values());
        IntAVLTreeSet changedPatternSlots = changedPatternSlots(loadedSlots, loadedOccupiedSlots);
        boolean patternDirectoryChanged = !changedPatternSlots.isEmpty();
        long nextDirectoryRevision = patternDirectoryChanged ? Math.incrementExact(this.revision) : this.revision;
        long nextStateRevision = Math.incrementExact(this.stateRevision);
        TrinityPatternCoreWorkIndexes.Snapshot loadedIndexes = this.workIndexes.snapshot(
                new ObjectArrayList<>(loadedSlots.values()), loadedReusable);
        IntAVLTreeSet changedSlots = persistentSlots();
        changedSlots.addAll(loadedSlots.keySet());
        IntAVLTreeSet changedWorkSlots = new IntAVLTreeSet();
        changedWorkSlots.addAll(this.reusableSlots.keySet());
        changedWorkSlots.addAll(loadedReusable.keySet());
        ObjectArrayList<SlotApplication> slotApplications = new ObjectArrayList<>(changedSlots.size());
        for (int slot : changedSlots) {
            TrinityPatternSlot loadedSlot = loadedSlots.computeIfAbsent(slot, this::newDetachedSlot);
            TrinityPatternSlot targetSlot = this.slots.get(slot);
            targetSlot.ensureCanApplyValidatedState(loadedSlot);
            slotApplications.add(new SlotApplication(targetSlot, loadedSlot));
            if (!targetSlot.matchesWorkMembership(loadedSlot)) {
                changedWorkSlots.add(slot);
            }
        }

        Int2ObjectMap<CachedPattern> nextCachedPatterns = new Int2ObjectOpenHashMap<>(this.cachedPatterns);
        ObjectArrayList<CachedRebind> preparedRebinds = new ObjectArrayList<>(loadedOccupiedSlots.size());
        IntAVLTreeSet changedRuntimeBindings = new IntAVLTreeSet();
        for (int slot : changedPatternSlots) {
            TrinityPatternSlot loadedSlot = loadedSlots.get(slot);
            if (loadedSlot.pattern().isEmpty()) {
                nextCachedPatterns.remove(slot);
            } else {
                nextCachedPatterns.put(slot, new CachedPattern(
                        slot,
                        loadedSlot.requiredInstalledDefinition(),
                        loadedSlot.decodedPattern()));
            }
        }
        for (int slot : loadedOccupiedSlots) {
            if (!changedPatternSlots.contains(slot)) {
                CachedPattern cached = nextCachedPatterns.get(slot);
                if (cached == null) {
                    throw new IllegalStateException("Missing cached Trinity pattern for retained slot " + slot);
                }
                TrinityPatternSlot loadedSlot = loadedSlots.get(slot);
                CachedPattern.PreparedRebind prepared = cached.prepareRebind(
                        loadedSlot.requiredInstalledDefinition(),
                        loadedSlot.decodedPattern());
                preparedRebinds.add(new CachedRebind(cached, prepared));
                if (prepared.semanticChange()) {
                    changedRuntimeBindings.add(slot);
                }
            }
        }

        IntList nextOccupiedSlotSnapshot = this.occupiedPatternSlotSnapshot;
        PatternCacheSnapshot nextPatternCacheSnapshot = this.patternCacheSnapshot;
        if (patternDirectoryChanged) {
            nextOccupiedSlotSnapshot = IntList.of(loadedOccupiedSlots.toIntArray());
            ObjectArrayList<CachedPattern> orderedPatterns = new ObjectArrayList<>(nextOccupiedSlotSnapshot.size());
            for (int slot : nextOccupiedSlotSnapshot) {
                CachedPattern cached = nextCachedPatterns.get(slot);
                if (cached == null) {
                    throw new IllegalStateException("Missing cached Trinity pattern for occupied slot " + slot);
                }
                orderedPatterns.add(cached);
            }
            nextPatternCacheSnapshot = new PatternCacheSnapshot(nextDirectoryRevision, orderedPatterns);
        }

        for (SlotApplication application : slotApplications) {
            application.target().applyValidatedState(application.loaded());
        }
        for (CachedRebind rebind : preparedRebinds) {
            rebind.target().commitRebind(rebind.prepared());
        }
        this.cachedPatterns = nextCachedPatterns;
        this.occupiedPatternSlots = loadedOccupiedSlots;
        this.occupiedPatternSlotSnapshot = nextOccupiedSlotSnapshot;
        this.patternCacheSnapshot = nextPatternCacheSnapshot;
        this.revision = nextDirectoryRevision;
        this.workIndexes.apply(loadedIndexes);
        this.patternRefundOutbox = loadedOutbox.patterns();
        this.retainedRefundOutboxByHost = loadedOutbox.retainedByHost();
        this.reusableSlots = loadedReusable;
        this.custodyArchive = loadedCustody;
        this.coreId = loadedId;
        this.stateRevision = nextStateRevision;
        for (int reusableSlot : loadedReusable.keySet()) {
            updateSlotWorkIndexes(reusableSlot);
        }
        if (notifyChanges && !identityChanged) {
            for (int slot : changedPatternSlots) {
                notifyChange(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.CATALOG));
            }
            for (int slot : changedRuntimeBindings) {
                notifyChange(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.RUNTIME_BINDING));
            }
            for (int slot : changedWorkSlots) {
                notifyChange(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.WORK));
            }
        }
        return version != CURRENT_STATE_VERSION || invalidPatternWorkMigration.migrated();
    }

    private IntAVLTreeSet changedPatternSlots(Int2ObjectMap<TrinityPatternSlot> loadedSlots,
                                              IntSet loadedOccupiedSlots) {
        IntAVLTreeSet changed = new IntAVLTreeSet(this.occupiedPatternSlots);
        changed.addAll(loadedOccupiedSlots);
        IntAVLTreeSet retained = new IntAVLTreeSet(this.occupiedPatternSlots);
        retained.retainAll(loadedOccupiedSlots);
        changed.removeAll(retained);
        for (int slot : retained) {
            if (!ItemStack.matches(this.slots.get(slot).pattern(), loadedSlots.get(slot).pattern())) {
                changed.add(slot);
            }
        }
        return changed;
    }

    /**
     * Moves work owned by semantically unavailable installed patterns into an isolated parsed refund outbox before
     * any live core state is replaced.
     */
    private static InvalidPatternWorkMigration migrateInvalidLoadedPatternWork(
                                                                               Int2ObjectMap<TrinityPatternSlot> loadedSlots,
                                                                               RefundOutbox loadedOutbox) {
        ObjectArrayList<InvalidPatternWorkCapture> captures = new ObjectArrayList<>();
        for (int slotIndex : new IntAVLTreeSet(loadedSlots.keySet())) {
            TrinityPatternSlot slot = loadedSlots.get(slotIndex);
            if (!hasInvalidPatternWork(slot)) {
                continue;
            }
            TrinityPatternSlot.WorkState work = slot.captureWorkState();
            ObjectArrayList<RetainedRefundOffer> offers = new ObjectArrayList<>();
            appendWorkRefundOffers(slotIndex, work, null, offers);
            captures.add(new InvalidPatternWorkCapture(slot, offers));
        }
        if (captures.isEmpty()) {
            return new InvalidPatternWorkMigration(loadedOutbox, false);
        }

        RefundOutbox migratedOutbox = new RefundOutbox(
                loadedOutbox.patterns(),
                loadedOutbox.retainedByHost());
        for (InvalidPatternWorkCapture capture : captures) {
            appendRetainedRefundEntries(migratedOutbox.retainedByHost(), capture.offers());
        }
        for (InvalidPatternWorkCapture capture : captures) {
            capture.slot().clearRefundableWork(null);
        }
        return new InvalidPatternWorkMigration(migratedOutbox, true);
    }

    private static boolean hasInvalidPatternWork(TrinityPatternSlot slot) {
        return !slot.pattern().isEmpty() &&
                slot.decodedPattern() == null &&
                (slot.hasQueuedWork() || slot.hasPendingOutputs());
    }

    /** Collects work in the same deterministic order used by ordinary retained-state refunds. */
    private static void appendWorkRefundOffers(int slot,
                                               TrinityPatternSlot.WorkState work,
                                               @Nullable UUID hostFilter,
                                               ObjectList<RetainedRefundOffer> destination) {
        for (TrinityCraftingBatch batch : work.batches()) {
            if (hostFilter != null && !hostFilter.equals(batch.route().hostId())) {
                continue;
            }
            for (ItemStack input : batch.inputs()) {
                if (input.isEmpty()) {
                    continue;
                }
                for (TrinityItemAmount item : TrinityItemAmount.multiply(input, batch.exactCount())) {
                    destination.add(new RetainedRefundOffer(
                            batch.route().hostId(),
                            new RetainedRefundEntry(slot, item)));
                }
            }
        }
        for (var entry : work.pendingOutputs().object2ObjectEntrySet()) {
            if (hostFilter != null && !hostFilter.equals(entry.getKey().hostId())) {
                continue;
            }
            for (TrinityItemAmount item : entry.getValue()) {
                destination.add(new RetainedRefundOffer(
                        entry.getKey().hostId(),
                        new RetainedRefundEntry(slot, item)));
            }
        }
    }

    private RefundState captureRefundState(@Nullable UUID routeHostId) {
        ObjectArrayList<RetainedRefundOffer> offeredEntries = new ObjectArrayList<>();
        appendRetainedOutboxOffers(routeHostId, offeredEntries);
        int existingOutboxEntryCount = offeredEntries.size();
        ObjectArrayList<TrinityPatternSlot.WorkState> capturedSlots = new ObjectArrayList<>(this.patternCapacity);
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            TrinityPatternSlot.WorkState captured = this.slots.get(slot).captureWorkState();
            capturedSlots.add(captured);
            appendWorkRefundOffers(slot, captured, routeHostId, offeredEntries);
        }
        return new RefundState(
                routeHostId,
                this.stateRevision,
                new ObjectImmutableList<>(capturedSlots),
                new ObjectImmutableList<>(offeredEntries),
                existingOutboxEntryCount);
    }

    private RefundTransaction beginRefund(RefundState captured) {
        if (this.activeRefundTransaction != null || this.activePatternRefundTransaction != null) {
            throw new IllegalStateException("Trinity pattern core " + this.coreId + " already has an active refund");
        }
        CoreRefundTransaction transaction = new CoreRefundTransaction(captured);
        this.activeRefundTransaction = transaction;
        return transaction;
    }

    private void clearRefundState(@Nullable UUID routeHostId) {
        for (TrinityPatternSlot slot : this.slots) {
            slot.clearRefundableWork(routeHostId);
        }
        rebuildWorkIndexes();
    }

    private boolean matchesRefundState(RefundState captured) {
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            if (!this.slots.get(slot).matchesWorkState(captured.slots().get(slot))) {
                return false;
            }
        }
        return true;
    }

    private void restoreRefundState(RefundState captured) {
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            this.slots.get(slot).restoreWorkState(captured.slots().get(slot));
        }
        rebuildWorkIndexes();
    }

    private void appendRetainedOutboxOffers(@Nullable UUID hostId, ObjectList<RetainedRefundOffer> offers) {
        if (hostId != null) {
            ObjectArrayList<RetainedRefundEntry> entries = this.retainedRefundOutboxByHost.get(hostId);
            if (entries != null) {
                for (RetainedRefundEntry entry : entries) {
                    offers.add(new RetainedRefundOffer(hostId, entry));
                }
            }
            return;
        }
        for (Object2ObjectMap.Entry<UUID, ObjectArrayList<RetainedRefundEntry>> group : this.retainedRefundOutboxByHost.object2ObjectEntrySet()) {
            for (RetainedRefundEntry entry : group.getValue()) {
                offers.add(new RetainedRefundOffer(group.getKey(), entry));
            }
        }
    }

    private static Object2ObjectMap<UUID, ObjectArrayList<RetainedRefundEntry>> copyRetainedRefundOutbox(
                                                                                                         Object2ObjectMap<UUID, ObjectArrayList<RetainedRefundEntry>> source) {
        Object2ObjectRBTreeMap<UUID, ObjectArrayList<RetainedRefundEntry>> copy = new Object2ObjectRBTreeMap<>();
        for (Object2ObjectMap.Entry<UUID, ObjectArrayList<RetainedRefundEntry>> group : source.object2ObjectEntrySet()) {
            copy.put(group.getKey(), new ObjectArrayList<>(group.getValue()));
        }
        return copy;
    }

    private static void appendRetainedRefundEntries(
                                                    Object2ObjectMap<UUID, ObjectArrayList<RetainedRefundEntry>> outbox,
                                                    ObjectList<RetainedRefundOffer> offers) {
        for (RetainedRefundOffer offer : offers) {
            outbox.computeIfAbsent(offer.hostId(), ignored -> new ObjectArrayList<>()).add(offer.entry());
        }
    }

    private void appendRetainedRefundEntries(ObjectList<RetainedRefundOffer> offers) {
        appendRetainedRefundEntries(this.retainedRefundOutboxByHost, offers);
        for (RetainedRefundOffer offer : offers) {
            markPersistentChanged(offer.entry().slot());
        }
    }

    private void removeRetainedRefundEntriesFromTail(ObjectList<RetainedRefundOffer> offers) {
        for (int index = offers.size() - 1; index >= 0; index--) {
            RetainedRefundOffer offer = offers.get(index);
            ObjectArrayList<RetainedRefundEntry> entries = this.retainedRefundOutboxByHost.get(offer.hostId());
            if (entries == null || entries.isEmpty()) {
                throw new IllegalStateException("Missing Trinity retained refund outbox entry for host " + offer.hostId());
            }
            int tailIndex = entries.size() - 1;
            RetainedRefundEntry actual = entries.get(tailIndex);
            if (!actual.matches(offer.entry())) {
                throw new IllegalStateException("Trinity retained refund outbox order changed for host " + offer.hostId());
            }
            entries.remove(tailIndex);
            if (entries.isEmpty()) {
                this.retainedRefundOutboxByHost.remove(offer.hostId(), entries);
            }
            markPersistentChanged(offer.entry().slot());
        }
    }

    private void completeRetainedRefund(ObjectList<RetainedRefundOffer> offers, ObjectList<TrinityItemAmount> undelivered) {
        int undeliveredStart = validateRetainedUndeliveredSuffix(offers, undelivered);
        for (int index = 0; index < undeliveredStart; index++) {
            removeDeliveredRetainedRefund(offers.get(index));
        }
        if (undelivered.isEmpty()) {
            return;
        }
        RetainedRefundOffer firstUndelivered = offers.get(undeliveredStart);
        TrinityItemAmount remaining = undelivered.getFirst();
        if (!firstUndelivered.entry().item().equals(remaining)) {
            replaceFirstRetainedRefund(firstUndelivered, remaining);
        }
    }

    private void removeDeliveredRetainedRefund(RetainedRefundOffer offer) {
        ObjectArrayList<RetainedRefundEntry> entries = this.retainedRefundOutboxByHost.get(offer.hostId());
        if (entries == null || entries.isEmpty()) {
            throw new IllegalStateException("Missing delivered Trinity retained refund for host " + offer.hostId());
        }
        RetainedRefundEntry actual = entries.getFirst();
        if (!actual.matches(offer.entry())) {
            throw new IllegalStateException("Trinity retained refund order changed for host " + offer.hostId());
        }
        entries.removeFirst();
        if (entries.isEmpty()) {
            this.retainedRefundOutboxByHost.remove(offer.hostId(), entries);
        }
        markPersistentChanged(offer.entry().slot());
    }

    private void replaceFirstRetainedRefund(RetainedRefundOffer offer, TrinityItemAmount remaining) {
        ObjectArrayList<RetainedRefundEntry> entries = this.retainedRefundOutboxByHost.get(offer.hostId());
        if (entries == null || entries.isEmpty() || !entries.getFirst().matches(offer.entry())) {
            throw new IllegalStateException("Trinity retained refund order changed for host " + offer.hostId());
        }
        entries.set(0, new RetainedRefundEntry(offer.entry().slot(), remaining));
        markPersistentChanged(offer.entry().slot());
    }

    private static int validateRetainedUndeliveredSuffix(ObjectList<RetainedRefundOffer> offers,
                                                         ObjectList<TrinityItemAmount> undelivered) {
        if (undelivered.size() > offers.size()) {
            throw new IllegalArgumentException("Trinity retained refund delivery returned more items than it received");
        }
        int start = offers.size() - undelivered.size();
        if (undelivered.isEmpty()) {
            return start;
        }
        TrinityItemAmount expectedFirst = offers.get(start).entry().item();
        TrinityItemAmount actualFirst = undelivered.getFirst();
        if (!expectedFirst.key().equals(actualFirst.key()) ||
                actualFirst.exactAmount().compareTo(expectedFirst.exactAmount()) > 0) {
            throw new IllegalArgumentException("Trinity retained refund delivery returned an invalid remaining suffix");
        }
        for (int index = 1; index < undelivered.size(); index++) {
            if (!offers.get(start + index).entry().item().equals(undelivered.get(index))) {
                throw new IllegalArgumentException("Trinity retained refund delivery changed remaining item order");
            }
        }
        return start;
    }

    private void appendPatternRefundEntries(ObjectList<PatternRefundEntry> entries) {
        for (PatternRefundEntry entry : entries) {
            this.patternRefundOutbox.add(entry);
            markPersistentChanged(entry.slot());
        }
    }

    private void removePatternRefundEntriesFromTail(ObjectList<PatternRefundEntry> entries) {
        for (int index = entries.size() - 1; index >= 0; index--) {
            PatternRefundEntry expected = entries.get(index);
            if (this.patternRefundOutbox.isEmpty()) {
                throw new IllegalStateException("Missing Trinity installed-pattern refund outbox entry");
            }
            int tailIndex = this.patternRefundOutbox.size() - 1;
            PatternRefundEntry actual = this.patternRefundOutbox.get(tailIndex);
            if (!actual.matches(expected)) {
                throw new IllegalStateException("Trinity installed-pattern refund outbox order changed");
            }
            this.patternRefundOutbox.remove(tailIndex);
            markPersistentChanged(expected.slot());
        }
    }

    private void completePatternRefund(ObjectList<PatternRefundEntry> offers, ObjectList<ItemStack> undelivered) {
        int undeliveredStart = validatePatternUndeliveredSuffix(offers, undelivered);
        for (int index = 0; index < undeliveredStart; index++) {
            PatternRefundEntry expected = offers.get(index);
            if (this.patternRefundOutbox.isEmpty()) {
                throw new IllegalStateException("Missing delivered Trinity installed-pattern refund");
            }
            PatternRefundEntry actual = this.patternRefundOutbox.getFirst();
            if (!actual.matches(expected)) {
                throw new IllegalStateException("Trinity installed-pattern refund order changed");
            }
            this.patternRefundOutbox.removeFirst();
            markPersistentChanged(expected.slot());
        }
    }

    private static int validatePatternUndeliveredSuffix(ObjectList<PatternRefundEntry> offers, ObjectList<ItemStack> undelivered) {
        if (undelivered.size() > offers.size()) {
            throw new IllegalArgumentException("Trinity pattern refund delivery returned more patterns than it received");
        }
        int start = offers.size() - undelivered.size();
        for (int index = 0; index < undelivered.size(); index++) {
            if (!offers.get(start + index).matches(undelivered.get(index))) {
                throw new IllegalArgumentException("Trinity pattern refund delivery changed remaining pattern order");
            }
        }
        return start;
    }

    private Int2ObjectMap<TrinityPatternSlot> readSlots(
                                                        ListTag entries,
                                                        HolderLookup.Provider registries,
                                                        UUID loadedId) {
        Int2ObjectOpenHashMap<TrinityPatternSlot> loaded = new Int2ObjectOpenHashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            TrinityPatternSlot slot = TrinityPatternSlot.readFromTag(
                    entries.getCompound(index), this.decoder, this.recipeIdResolvers, change -> {}, registries);
            checkSlot(slot.index());
            if (!slot.hasPersistentState()) {
                throw new IllegalArgumentException("Empty persisted Trinity pattern slot " + slot.index());
            }
            if (loaded.putIfAbsent(slot.index(), slot) != null) {
                throw new IllegalArgumentException("Duplicate persisted Trinity pattern slot " + slot.index());
            }
            for (TrinityCraftingBatch batch : slot.queuedBatches()) {
                validatePersistedRoute(batch.route(), loadedId, slot.index(), "queued group");
            }
            for (PatternRoute route : slot.pendingOutputRoutes()) {
                validatePersistedRoute(route, loadedId, slot.index(), "pending output");
            }
        }
        return loaded;
    }

    private TrinityPatternSlot newDetachedSlot(int slot) {
        return new TrinityPatternSlot(slot, this.decoder, this.recipeIdResolvers, change -> {});
    }

    private void validateOwnedRoute(PatternRoute route) {
        checkSlot(route.slot());
        if (!this.coreId.equals(route.coreId())) {
            throw new IllegalArgumentException(
                    "Pattern route core " + route.coreId() + " does not match " + this.coreId);
        }
    }

    private void validatePersistedRoute(PatternRoute route, UUID loadedId, int expectedSlot, String kind) {
        checkSlot(route.slot());
        if (!loadedId.equals(route.coreId()) || route.slot() != expectedSlot) {
            throw new IllegalArgumentException(
                    "Persisted " + kind + " route " + route + " does not match core " + loadedId +
                            " slot " + expectedSlot);
        }
    }

    private int validatePersistedCapacity(CompoundTag data, int version) {
        if (!data.contains(PATTERN_CAPACITY_TAG, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Persisted Trinity pattern core state is missing its capacity");
        }
        int persistedCapacity = data.getInt(PATTERN_CAPACITY_TAG);
        if (version >= COUNTED_STATE_VERSION && persistedCapacity == this.patternCapacity ||
                version == POWER_OF_TWO_CAPACITY_STATE_VERSION &&
                        TrinityPatternCoreTier.matchesPowerOfTwoCapacity(persistedCapacity, this.patternCapacity)) {
            return persistedCapacity;
        }
        throw new IllegalArgumentException(
                "Persisted Trinity pattern core capacity " + persistedCapacity +
                        " does not match block capacity " + this.patternCapacity);
    }

    /**
     * Ensures every persisted slot and refund fits its saved capacity, including the pre-upgrade 3.1.3 index range.
     */
    private static void validatePersistedSlotBounds(Int2ObjectMap<TrinityPatternSlot> slots,
                                                    RefundOutbox refundOutbox,
                                                    int persistedCapacity) {
        for (int slot : slots.keySet()) {
            validatePersistedSlotBound(slot, persistedCapacity, "slot");
        }
        for (PatternRefundEntry entry : refundOutbox.patterns()) {
            validatePersistedSlotBound(entry.slot(), persistedCapacity, "pattern refund");
        }
        for (ObjectList<RetainedRefundEntry> entries : refundOutbox.retainedByHost().values()) {
            for (RetainedRefundEntry entry : entries) {
                validatePersistedSlotBound(entry.slot(), persistedCapacity, "retained refund");
            }
        }
    }

    private static void validatePersistedSlotBound(int slot, int persistedCapacity, String stateKind) {
        if (slot < 0 || slot >= persistedCapacity) {
            throw new IllegalArgumentException(
                    "Persisted Trinity " + stateKind + " slot " + slot +
                            " is outside persisted capacity " + persistedCapacity);
        }
    }

    private static void writeCurrentSchemaHeader(CompoundTag data) {
        data.putInt(VERSION_TAG, CURRENT_STATE_VERSION);
    }

    private ListTag writeReusableSlots(HolderLookup.Provider registries, boolean removingPattern) {
        ListTag entries = new ListTag();
        for (int slot : new IntAVLTreeSet(this.reusableSlots.keySet())) {
            entries.add(this.reusableSlots.get(slot).writeToTag(registries, removingPattern));
        }
        return entries;
    }

    private static ReusableCustodyArchive readCustodyArchive(CompoundTag data, UUID coreId, int version) {
        if (version < REUSABLE_STATE_VERSION) {
            if (data.contains(CUSTODY_ARCHIVE_TAG)) {
                throw new IllegalArgumentException("Legacy core schema cannot contain a custody archive");
            }
            return new ReusableCustodyArchive();
        }
        return ReusableCustodyArchive.readFromTag(requiredCompoundList(data, CUSTODY_ARCHIVE_TAG), "trinity-core:" + coreId + "/slot:");
    }

    private Int2ObjectMap<TrinityReusableSlot> readReusableSlots(CompoundTag data, HolderLookup.Provider registries,
                                                                 UUID loadedId, int version) {
        Int2ObjectMap<TrinityReusableSlot> restored = new Int2ObjectOpenHashMap<>();
        if (version < REUSABLE_STATE_VERSION) {
            if (data.contains(REUSABLE_SLOTS_TAG)) {
                throw new IllegalArgumentException("Legacy core schema cannot contain reusable session assets");
            }
            return restored;
        }
        for (Tag encoded : requiredCompoundList(data, REUSABLE_SLOTS_TAG)) {
            TrinityReusableSlot reusable = TrinityReusableSlot.readFromTag((CompoundTag) encoded, registries);
            PatternRoute route = reusable.route();
            validatePersistedRoute(route, loadedId, route.slot(), "reusable session");
            if (restored.put(route.slot(), reusable) != null) {
                throw new IllegalArgumentException("Duplicate persisted reusable core slot");
            }
        }
        return restored;
    }

    private void requestReusableClosure(@Nullable UUID hostId) {
        for (TrinityReusableSlot reusable : this.reusableSlots.values()) {
            if (reusable.hasWork() && (hostId == null || hostId.equals(reusable.route().hostId()))) {
                reusable.requestClose();
                markPersistentChanged(reusable.route().slot());
            }
        }
    }

    private static void clearCoreStateTags(CompoundTag data) {
        data.remove(VERSION_TAG);
        data.remove(CORE_ID_TAG);
        data.remove(PATTERN_CAPACITY_TAG);
        data.remove(SLOTS_TAG);
        data.remove(REFUND_OUTBOX_TAG);
        data.remove(REUSABLE_SLOTS_TAG);
        data.remove(CUSTODY_ARCHIVE_TAG);
    }

    private static int validatePersistedSchema(CompoundTag data) {
        if (!data.contains(VERSION_TAG, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Persisted Trinity pattern core state requires an integer version");
        }
        int version = data.getInt(VERSION_TAG);
        if (version != POWER_OF_TWO_CAPACITY_STATE_VERSION && version != COUNTED_STATE_VERSION &&
                version != REUSABLE_STATE_VERSION && version != CURRENT_STATE_VERSION) {
            throw new IllegalArgumentException("Unsupported Trinity pattern core state version " + version);
        }
        if (!data.contains(SLOTS_TAG)) {
            throw new IllegalArgumentException("Persisted Trinity pattern core state is missing slots");
        }
        if (!data.contains(REFUND_OUTBOX_TAG)) {
            throw new IllegalArgumentException(
                    "Persisted Trinity pattern core state is missing its refund outbox");
        }
        return version;
    }

    private static boolean containsCoreState(CompoundTag data) {
        return data.contains(VERSION_TAG) || data.contains(PATTERN_CAPACITY_TAG) || data.contains(SLOTS_TAG) ||
                data.contains(REFUND_OUTBOX_TAG) || data.contains(REUSABLE_SLOTS_TAG) || data.contains(CUSTODY_ARCHIVE_TAG);
    }

    private static ListTag requiredCompoundList(CompoundTag data, String tagName) {
        if (!(data.get(tagName) instanceof ListTag entries) ||
                !entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException(
                    "Persisted Trinity pattern core state requires compound list '" + tagName + "'");
        }
        return entries;
    }

    private CompoundTag writeRefundOutbox(HolderLookup.Provider registries) {
        CompoundTag outbox = new CompoundTag();
        ListTag patternEntries = new ListTag();
        for (PatternRefundEntry entry : this.patternRefundOutbox) {
            CompoundTag entryData = new CompoundTag();
            entryData.putInt(SLOT_TAG, entry.slot());
            entryData.put(STACK_TAG, entry.pattern().saveOptional(registries));
            patternEntries.add(entryData);
        }
        outbox.put(PATTERN_REFUNDS_TAG, patternEntries);

        ListTag retainedGroups = new ListTag();
        for (Object2ObjectMap.Entry<UUID, ObjectArrayList<RetainedRefundEntry>> group : this.retainedRefundOutboxByHost.object2ObjectEntrySet()) {
            if (group.getValue().isEmpty()) {
                throw new IllegalStateException("Trinity retained refund outbox contains an empty host group");
            }
            CompoundTag groupData = new CompoundTag();
            groupData.putUUID(HOST_ID_TAG, group.getKey());
            ListTag items = new ListTag();
            for (RetainedRefundEntry entry : group.getValue()) {
                CompoundTag itemData = new CompoundTag();
                itemData.putInt(SLOT_TAG, entry.slot());
                itemData.put(PROTOTYPE_TAG, entry.item().key().toStack(1).saveOptional(registries));
                itemData.putByteArray(AMOUNT_TAG,
                        TrinityBigIntegerEncoding.encode(entry.item().exactAmount(), "retained crafting refund"));
                items.add(itemData);
            }
            groupData.put(ITEMS_TAG, items);
            retainedGroups.add(groupData);
        }
        outbox.put(RETAINED_REFUNDS_TAG, retainedGroups);
        return outbox;
    }

    private RefundOutbox readRefundOutbox(CompoundTag data, HolderLookup.Provider registries) {
        if (!(data.get(REFUND_OUTBOX_TAG) instanceof CompoundTag outbox)) {
            throw new IllegalArgumentException("Persisted Trinity pattern core state is missing its refund outbox");
        }
        requireExactKeys(outbox, "Trinity refund outbox", PATTERN_REFUNDS_TAG, RETAINED_REFUNDS_TAG);

        ListTag patternEntries = requiredCompoundList(outbox, PATTERN_REFUNDS_TAG);
        ObjectArrayList<PatternRefundEntry> patterns = new ObjectArrayList<>(patternEntries.size());
        for (int index = 0; index < patternEntries.size(); index++) {
            patterns.add(readPatternRefundEntry(patternEntries.getCompound(index), registries));
        }

        ListTag retainedGroups = requiredCompoundList(outbox, RETAINED_REFUNDS_TAG);
        Object2ObjectRBTreeMap<UUID, ObjectArrayList<RetainedRefundEntry>> retainedByHost = new Object2ObjectRBTreeMap<>();
        for (int index = 0; index < retainedGroups.size(); index++) {
            CompoundTag groupData = retainedGroups.getCompound(index);
            requireExactKeys(groupData, "Trinity retained refund group", HOST_ID_TAG, ITEMS_TAG);
            if (!groupData.hasUUID(HOST_ID_TAG)) {
                throw new IllegalArgumentException("Trinity retained refund group is missing its host UUID");
            }
            UUID hostId = groupData.getUUID(HOST_ID_TAG);
            ListTag items = requiredCompoundList(groupData, ITEMS_TAG);
            if (items.isEmpty()) {
                throw new IllegalArgumentException("Trinity retained refund group must not be empty");
            }
            ObjectArrayList<RetainedRefundEntry> entries = new ObjectArrayList<>(items.size());
            for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
                entries.add(readRetainedRefundEntry(items.getCompound(itemIndex), registries));
            }
            if (retainedByHost.putIfAbsent(hostId, entries) != null) {
                throw new IllegalArgumentException("Duplicate Trinity retained refund host " + hostId);
            }
        }
        return new RefundOutbox(patterns, retainedByHost);
    }

    private PatternRefundEntry readPatternRefundEntry(CompoundTag data, HolderLookup.Provider registries) {
        requireExactKeys(data, "Trinity pattern refund entry", SLOT_TAG, STACK_TAG);
        if (!data.contains(SLOT_TAG, Tag.TAG_INT) || !data.contains(STACK_TAG, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Trinity pattern refund entry is incomplete");
        }
        int slot = data.getInt(SLOT_TAG);
        checkSlot(slot);
        ItemStack pattern = ItemStack.parseOptional(registries, data.getCompound(STACK_TAG));
        if (pattern.isEmpty() || pattern.getCount() != 1) {
            throw new IllegalArgumentException("Trinity pattern refund entry requires one encoded pattern");
        }
        return new PatternRefundEntry(slot, pattern);
    }

    private RetainedRefundEntry readRetainedRefundEntry(CompoundTag data, HolderLookup.Provider registries) {
        requireExactKeys(data, "Trinity retained refund entry", SLOT_TAG, PROTOTYPE_TAG, AMOUNT_TAG);
        if (!data.contains(SLOT_TAG, Tag.TAG_INT) || !data.contains(PROTOTYPE_TAG, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Trinity retained refund entry is incomplete");
        }
        int slot = data.getInt(SLOT_TAG);
        checkSlot(slot);
        ItemStack prototype = ItemStack.parseOptional(registries, data.getCompound(PROTOTYPE_TAG));
        if (prototype.isEmpty() || prototype.getCount() != 1) {
            throw new IllegalArgumentException("Trinity retained refund entry requires one item prototype");
        }
        return new RetainedRefundEntry(slot, new TrinityItemAmount(AEItemKey.of(prototype),
                TrinityBigIntegerEncoding.readTag(data, AMOUNT_TAG, "retained crafting refund")));
    }

    private static void requireExactKeys(CompoundTag data, String description, String... requiredKeys) {
        ObjectSet<String> actualKeys = new ObjectOpenHashSet<>(data.getAllKeys());
        ObjectSet<String> expectedKeys = ObjectSet.of(requiredKeys);
        if (!actualKeys.equals(expectedKeys)) {
            throw new IllegalArgumentException(description + " has unexpected fields " + actualKeys);
        }
    }

    private static void validateCapacity(int patternCapacity) {
        if (!TrinityPatternCoreTier.supportsPatternCapacity(patternCapacity)) {
            throw new IllegalArgumentException(
                    "Unsupported Trinity pattern core capacity " + patternCapacity);
        }
    }

    private TrinityPatternSlot slot(int slot) {
        checkSlot(slot);
        return this.slots.get(slot);
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= this.patternCapacity) {
            throw new IllegalArgumentException(
                    "Trinity pattern core slot out of range: " + slot + " for capacity " + this.patternCapacity);
        }
    }

    private static void validateCurrentTick(long currentTick) {
        if (currentTick < 0L) {
            throw new IllegalArgumentException("Current tick must not be negative: " + currentTick);
        }
    }

    private int executeReadyBatchesInSlot(int slotIndex, long currentTick, BatchExecutor executor) {
        TrinityPatternSlot slot = this.slots.get(slotIndex);
        int completedGroups = 0;
        while (slot.hasQueuedWork()) {
            TrinityCraftingBatch batch = slot.readyHead(currentTick);
            if (batch == null) {
                break;
            }
            BatchExecutionResult result = executor.execute(slotIndex, batch);
            if (!result.completed()) {
                break;
            }
            slot.completeHead(batch, new ObjectArrayList<>(result.countedOutputs()));
            completedGroups = Math.incrementExact(completedGroups);
        }
        return completedGroups;
    }

    private TrinityPatternSlot newSlot(int slot) {
        return new TrinityPatternSlot(slot, this.decoder, this.recipeIdResolvers, this::onSlotChanged);
    }

    private void ensureNoActiveRefundTransaction() {
        if (this.activeRefundTransaction != null || this.activePatternRefundTransaction != null) {
            throw new IllegalStateException("Trinity pattern core " + this.coreId + " is processing a refund");
        }
    }

    private void onSlotChanged(TrinityPatternSlot.Change change) {
        switch (change.kind()) {
            case CATALOG -> {
                TrinityReusableSlot reusable = this.reusableSlots.get(change.slot());
                if (reusable != null && reusable.hasWork()) {
                    reusable.requestClose();
                }
                updateCachedPattern(change.slot());
                markCatalogChanged(change.slot());
            }
            case RUNTIME_BINDING -> updateRuntimeBinding(change.slot());
            case WORK -> updateSlotWorkIndexes(change.slot());
            case PERSISTENT -> markPersistentChanged(change.slot());
        }
    }

    private void updateCachedPattern(int slot) {
        ItemStack pattern = this.slots.get(slot).pattern();
        if (pattern.isEmpty()) {
            this.occupiedPatternSlots.remove(slot);
            this.cachedPatterns.remove(slot);
            return;
        }
        this.occupiedPatternSlots.add(slot);
        IMolecularAssemblerSupportedPattern decoded = this.slots.get(slot).decodedPattern();
        TrinityPatternDefinition definition = this.slots.get(slot).requiredInstalledDefinition();
        this.cachedPatterns.put(slot, new CachedPattern(slot, definition, decoded));
    }

    private void updateRuntimeBinding(int slot) {
        CachedPattern cached = this.cachedPatterns.get(slot);
        if (cached == null) {
            throw new IllegalStateException(
                    "Runtime binding changed for unoccupied Trinity pattern slot " + slot);
        }
        IMolecularAssemblerSupportedPattern decoded = this.slots.get(slot).decodedPattern();
        TrinityPatternDefinition definition = this.slots.get(slot).requiredInstalledDefinition();
        boolean semanticChange = cached.rebind(definition, decoded);
        TrinityReusableSlot reusable = this.reusableSlots.get(slot);
        if (semanticChange && reusable != null && reusable.hasWork()) {
            reusable.requestClose();
            markPersistentChanged(slot);
        }
        boolean invalidWorkMigrated = migrateInvalidRuntimePatternWork(slot);
        if (semanticChange || invalidWorkMigrated) {
            notifyChange(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.RUNTIME_BINDING));
        }
    }

    /**
     * Atomically replaces one unavailable pattern slot's live work with a durable, host-scoped refund ledger.
     */
    private boolean migrateInvalidRuntimePatternWork(int slotIndex) {
        TrinityPatternSlot slot = this.slots.get(slotIndex);
        if (!hasInvalidPatternWork(slot)) {
            return false;
        }

        TrinityPatternSlot.WorkState capturedWork = slot.captureWorkState();
        ObjectArrayList<RetainedRefundOffer> offers = new ObjectArrayList<>();
        appendWorkRefundOffers(slotIndex, capturedWork, null, offers);
        Object2ObjectMap<UUID, ObjectArrayList<RetainedRefundEntry>> previousOutbox = this.retainedRefundOutboxByHost;
        Object2ObjectMap<UUID, ObjectArrayList<RetainedRefundEntry>> migratedOutbox = copyRetainedRefundOutbox(previousOutbox);
        appendRetainedRefundEntries(migratedOutbox, offers);

        this.retainedRefundOutboxByHost = migratedOutbox;
        try {
            slot.clearRefundableWork(null);
            return true;
        } catch (RuntimeException exception) {
            this.retainedRefundOutboxByHost = previousOutbox;
            if (!slot.matchesWorkState(capturedWork)) {
                try {
                    slot.restoreWorkState(capturedWork);
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                    Data_Energistics.LOGGER.error(
                            "Failed to restore invalid Trinity pattern slot {} in core {} after refund migration failed",
                            slotIndex,
                            this.coreId,
                            rollbackFailure);
                }
            }
            try {
                rebuildWorkIndexes();
            } catch (RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
                Data_Energistics.LOGGER.error(
                        "Failed to rebuild Trinity pattern core {} work indexes after refund migration failed",
                        this.coreId,
                        rollbackFailure);
            }
            throw exception;
        }
    }

    private void markCatalogChanged(int slot) {
        this.revision = Math.incrementExact(this.revision);
        rebuildPatternCacheSnapshot();
        notifyChange(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.CATALOG));
    }

    private void rebuildPatternCacheSnapshot() {
        this.occupiedPatternSlotSnapshot = IntList.of(this.occupiedPatternSlots.toIntArray());
        ObjectArrayList<CachedPattern> orderedPatterns = new ObjectArrayList<>(this.occupiedPatternSlotSnapshot.size());
        for (int slot : this.occupiedPatternSlotSnapshot) {
            orderedPatterns.add(this.cachedPatterns.get(slot));
        }
        this.patternCacheSnapshot = new PatternCacheSnapshot(
                this.revision,
                orderedPatterns);
    }

    private IntAVLTreeSet persistentSlots() {
        return this.workIndexes.persistentSlots(this.occupiedPatternSlots);
    }

    private void updateSlotWorkIndexes(int slot) {
        this.workIndexes.updateSlot(slot, this.slots, this.reusableSlots, this::notifyChange);
    }

    private void rebuildWorkIndexes() {
        this.workIndexes.apply(this.workIndexes.snapshot(this.slots, this.reusableSlots));
    }

    private static IntAVLTreeSet occupiedSlots(Iterable<TrinityPatternSlot> sourceSlots) {
        IntAVLTreeSet occupied = new IntAVLTreeSet();
        for (TrinityPatternSlot slot : sourceSlots) {
            if (!slot.pattern().isEmpty()) {
                occupied.add(slot.index());
            }
        }
        return occupied;
    }

    private void markPersistentChanged(int slot) {
        this.stateRevision = Math.incrementExact(this.stateRevision);
        notifyChange(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.PERSISTENT));
    }

    private void ensurePersistentRevisionAvailable() {
        if (this.stateRevision == Long.MAX_VALUE) {
            throw new ArithmeticException("Trinity pattern core state revision overflow");
        }
    }

    private void notifyChange(TrinityPatternSlot.Change change) {
        try {
            this.changeListener.onChanged(change);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity pattern core {} retained committed slot {} {} state after its owner callback failed",
                    this.coreId,
                    change.slot(),
                    change.kind(),
                    exception);
        }
    }

    /**
     * One detached invalid-pattern slot and the ordered refund entries derived from its work snapshot.
     */
    private record InvalidPatternWorkCapture(TrinityPatternSlot slot,
                                             ObjectList<RetainedRefundOffer> offers) {

        private InvalidPatternWorkCapture {
            offers = new ObjectImmutableList<>(offers);
        }
    }

    /** Result of normalizing semantically invalid loaded patterns before live state is replaced. */
    private record InvalidPatternWorkMigration(RefundOutbox outbox, boolean migrated) {}

    /**
     * Immutable private capture used to restore a core after a coordinated host refund aborts.
     */
    private record RefundState(@Nullable UUID routeHostId,
                               long stateRevision,
                               ObjectList<TrinityPatternSlot.WorkState> slots,
                               ObjectList<RetainedRefundOffer> offeredEntries,
                               int existingOutboxEntryCount) {}

    /**
     * One installed pattern awaiting an acknowledged player/world delivery.
     */
    private record PatternRefundEntry(int slot, ItemStack pattern) {

        private PatternRefundEntry {
            if (slot < 0) {
                throw new IllegalArgumentException("Trinity pattern refund slot must not be negative: " + slot);
            }
            if (pattern.isEmpty() || pattern.getCount() != 1) {
                throw new IllegalArgumentException("Trinity pattern refund entry requires one encoded pattern");
            }
            pattern = pattern.copy();
        }

        private boolean matches(PatternRefundEntry other) {
            return this.slot == other.slot && ItemStack.matches(this.pattern, other.pattern);
        }

        private boolean matches(ItemStack other) {
            return ItemStack.matches(this.pattern, other);
        }
    }

    /**
     * One host-owned input or pending output awaiting an acknowledged external delivery.
     */
    private record RetainedRefundEntry(int slot, TrinityItemAmount item) {

        private RetainedRefundEntry {
            if (slot < 0) {
                throw new IllegalArgumentException("Trinity retained refund slot must not be negative: " + slot);
            }
        }

        private boolean matches(RetainedRefundEntry other) {
            return this.slot == other.slot && this.item.equals(other.item);
        }
    }

    /**
     * Associates a durable retained refund entry with the host that owns its retry queue.
     */
    private record RetainedRefundOffer(UUID hostId, RetainedRefundEntry entry) {}

    /**
     * Fully parsed, isolated durable refund queues that can be atomically applied to a core.
     */
    private record RefundOutbox(ObjectList<PatternRefundEntry> patterns,
                                Object2ObjectMap<UUID, ObjectArrayList<RetainedRefundEntry>> retainedByHost) {

        private RefundOutbox {
            patterns = new ObjectArrayList<>(patterns);
            retainedByHost = copyRetainedRefundOutbox(retainedByHost);
        }
    }

    /**
     * Immutable pattern capture guarded by the core and exact physical-slot revisions.
     */
    private record PatternRefundSlot(int slot, long slotRevision, ItemStack pattern) {

        private PatternRefundSlot {
            pattern = pattern.copy();
        }
    }

    /**
     * Detached sparse-index snapshot validated before an atomic load or refund restore mutates live state.
     */
    private record SlotApplication(TrinityPatternSlot target, TrinityPatternSlot loaded) {}

    private record CachedRebind(CachedPattern target, CachedPattern.PreparedRebind prepared) {}

    /**
     * Reversible state transition for one core participating in an aggregate host refund.
     */
    private final class CoreRefundTransaction implements RefundTransaction {

        private final RefundState captured;
        private boolean committed;
        private boolean closed;
        private long committedStateRevision = -1L;

        private CoreRefundTransaction(RefundState captured) {
            this.captured = captured;
        }

        @Override
        public ObjectList<TrinityItemAmount> refundableItems() {
            return this.captured.offeredEntries().stream().map(offer -> offer.entry().item()).collect(ObjectImmutableList.toList());
        }

        @Override
        public boolean commit() {
            if (this.closed || this.committed || stateChangedSincePreparation() || !matchesRefundState(this.captured)) {
                this.closed = true;
                release();
                return false;
            }
            try {
                appendRetainedRefundEntries(this.captured.offeredEntries().subList(
                        this.captured.existingOutboxEntryCount(), this.captured.offeredEntries().size()));
                clearRefundState(this.captured.routeHostId());
                this.committed = true;
                this.committedStateRevision = PersistentTrinityPatternCore.this.stateRevision;
                return true;
            } catch (RuntimeException exception) {
                try {
                    restoreRefundState(this.captured);
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                    Data_Energistics.LOGGER.error(
                            "Failed to restore Trinity pattern core {} after refund commit failure",
                            PersistentTrinityPatternCore.this.coreId,
                            rollbackFailure);
                }
                try {
                    removeRetainedRefundEntriesFromTail(this.captured.offeredEntries().subList(
                            this.captured.existingOutboxEntryCount(), this.captured.offeredEntries().size()));
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                    Data_Energistics.LOGGER.error(
                            "Failed to remove Trinity pattern core {} retained refund ledger after commit failure",
                            PersistentTrinityPatternCore.this.coreId,
                            rollbackFailure);
                }
                this.closed = true;
                release();
                throw exception;
            }
        }

        @Override
        public void complete(ObjectList<TrinityItemAmount> undeliveredItems) {
            if (this.closed || PersistentTrinityPatternCore.this.activeRefundTransaction != this) {
                return;
            }
            if (!this.committed) {
                this.closed = true;
                release();
                throw new IllegalStateException("Trinity retained refund was completed before commit");
            }
            try {
                completeRetainedRefund(this.captured.offeredEntries(), undeliveredItems);
            } finally {
                this.committed = false;
                this.closed = true;
                release();
            }
        }

        @Override
        public void rollback() {
            if (this.closed) {
                return;
            }
            try {
                if (this.committed) {
                    if (PersistentTrinityPatternCore.this.stateRevision == this.committedStateRevision) {
                        restoreRefundState(this.captured);
                        removeRetainedRefundEntriesFromTail(this.captured.offeredEntries().subList(
                                this.captured.existingOutboxEntryCount(), this.captured.offeredEntries().size()));
                    } else {
                        Data_Energistics.LOGGER.error(
                                "Cannot roll back Trinity pattern core {} refund because queued state changed after commit",
                                PersistentTrinityPatternCore.this.coreId);
                    }
                }
            } finally {
                this.committed = false;
                this.closed = true;
                release();
            }
        }

        private boolean stateChangedSincePreparation() {
            return PersistentTrinityPatternCore.this.stateRevision != this.captured.stateRevision();
        }

        private void release() {
            if (PersistentTrinityPatternCore.this.activeRefundTransaction == this) {
                PersistentTrinityPatternCore.this.activeRefundTransaction = null;
            }
        }
    }

    /**
     * Reversible all-or-nothing installed-pattern removal for one core.
     */
    private final class ReversiblePatternRefundTransaction implements PatternRefundTransaction {

        private final long capturedStateRevision;
        private final ObjectList<PatternRefundSlot> capturedSlots;
        private final ObjectList<PatternRefundEntry> offeredEntries;
        private final int existingOutboxEntryCount;
        private final boolean blockedByWork;
        private boolean committed;
        private boolean closed;
        private long committedStateRevision = -1L;

        private ReversiblePatternRefundTransaction(long capturedStateRevision,
                                                   ObjectList<PatternRefundSlot> capturedSlots,
                                                   ObjectList<PatternRefundEntry> offeredEntries,
                                                   int existingOutboxEntryCount,
                                                   boolean blockedByWork) {
            this.capturedStateRevision = capturedStateRevision;
            this.capturedSlots = capturedSlots;
            this.offeredEntries = offeredEntries;
            this.existingOutboxEntryCount = existingOutboxEntryCount;
            this.blockedByWork = blockedByWork;
        }

        @Override
        public ObjectList<ItemStack> patterns() {
            return this.offeredEntries.stream().map(entry -> entry.pattern().copy()).collect(ObjectImmutableList.toList());
        }

        @Override
        public boolean isEmpty() {
            return !this.blockedByWork && this.offeredEntries.isEmpty();
        }

        @Override
        public boolean isBlockedByWork() {
            return this.blockedByWork;
        }

        @Override
        public boolean commit() {
            if (this.closed || this.committed || this.blockedByWork ||
                    PersistentTrinityPatternCore.this.stateRevision != this.capturedStateRevision || hasWork()) {
                close();
                return false;
            }
            for (PatternRefundSlot captured : this.capturedSlots) {
                TrinityPatternSlot current = PersistentTrinityPatternCore.this.slots.get(captured.slot());
                if (current.revision() != captured.slotRevision() ||
                        !ItemStack.matches(current.pattern(), captured.pattern())) {
                    close();
                    return false;
                }
            }
            ObjectList<PatternRefundSlot> clearedSlots = new ObjectArrayList<>(this.capturedSlots.size());
            try {
                appendPatternRefundEntries(this.offeredEntries.subList(
                        this.existingOutboxEntryCount, this.offeredEntries.size()));
                for (PatternRefundSlot captured : this.capturedSlots) {
                    if (!PersistentTrinityPatternCore.this.slot(captured.slot()).trySetPattern(ItemStack.EMPTY)) {
                        throw new IllegalStateException("Failed to clear captured Trinity pattern slot " + captured.slot());
                    }
                    clearedSlots.add(captured);
                }
                this.committed = true;
                this.committedStateRevision = PersistentTrinityPatternCore.this.stateRevision;
                return true;
            } catch (RuntimeException exception) {
                try {
                    restorePatterns(clearedSlots);
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                    Data_Energistics.LOGGER.error(
                            "Failed to restore Trinity pattern core {} after pattern refund commit failure",
                            PersistentTrinityPatternCore.this.coreId,
                            rollbackFailure);
                }
                try {
                    removePatternRefundEntriesFromTail(this.offeredEntries.subList(
                            this.existingOutboxEntryCount, this.offeredEntries.size()));
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                    Data_Energistics.LOGGER.error(
                            "Failed to remove Trinity pattern core {} installed-pattern refund ledger after commit failure",
                            PersistentTrinityPatternCore.this.coreId,
                            rollbackFailure);
                }
                close();
                throw exception;
            }
        }

        @Override
        public void complete(ObjectList<ItemStack> undeliveredPatterns) {
            if (this.closed || PersistentTrinityPatternCore.this.activePatternRefundTransaction != this) {
                return;
            }
            if (!this.committed) {
                close();
                throw new IllegalStateException("Trinity pattern refund was completed before commit");
            }
            try {
                completePatternRefund(this.offeredEntries, undeliveredPatterns);
            } finally {
                this.committed = false;
                close();
            }
        }

        @Override
        public void rollback() {
            if (this.closed) {
                return;
            }
            try {
                if (this.committed) {
                    if (PersistentTrinityPatternCore.this.stateRevision == this.committedStateRevision) {
                        restorePatterns();
                        removePatternRefundEntriesFromTail(this.offeredEntries.subList(
                                this.existingOutboxEntryCount, this.offeredEntries.size()));
                    } else {
                        Data_Energistics.LOGGER.error(
                                "Cannot roll back Trinity pattern core {} pattern refund because core state changed after commit",
                                PersistentTrinityPatternCore.this.coreId);
                    }
                }
            } finally {
                this.committed = false;
                close();
            }
        }

        private void restorePatterns() {
            restorePatterns(this.capturedSlots);
        }

        private void restorePatterns(ObjectList<PatternRefundSlot> slotsToRestore) {
            for (PatternRefundSlot captured : slotsToRestore) {
                ItemStack current = PersistentTrinityPatternCore.this.pattern(captured.slot());
                if (!current.isEmpty()) {
                    throw new IllegalStateException("Cannot restore Trinity pattern slot " + captured.slot() +
                            " because it changed during a pattern refund");
                }
                if (!PersistentTrinityPatternCore.this.slot(captured.slot()).trySetPattern(captured.pattern())) {
                    throw new IllegalStateException("Failed to restore Trinity pattern slot " + captured.slot());
                }
            }
        }

        private void close() {
            this.closed = true;
            if (PersistentTrinityPatternCore.this.activePatternRefundTransaction == this) {
                PersistentTrinityPatternCore.this.activePatternRefundTransaction = null;
            }
        }
    }

    /**
     * Fixed-size AE2 menu inventory backed by stable slot models.
     */
    private final class PatternInventory extends AppEngInternalInventory {

        private PatternInventory() {
            super(PersistentTrinityPatternCore.this.patternCapacity);
        }

        @Override
        public int getSlotLimit(int slot) {
            checkSlot(slot);
            return 1;
        }

        @Override
        public int size() {
            return PersistentTrinityPatternCore.this.patternCapacity;
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            return pattern(slotIndex);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return PersistentTrinityPatternCore.this.slot(slot).acceptsPattern(stack);
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            trySetPattern(slotIndex, stack);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            checkSlot(slot);
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack current = PersistentTrinityPatternCore.this.slots.get(slot).pattern();
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!simulate) {
                trySetPattern(slot, ItemStack.EMPTY);
            }
            return current;
        }
    }
}
