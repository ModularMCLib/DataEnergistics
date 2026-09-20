package com.fish_dan_.data_energistics.common.crafting.packaged.execution;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorPolicy;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedBatchPattern;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedRecipeCatalog;
import com.fish_dan_.data_energistics.common.crafting.packaged.reusable.PackagedReusableState;
import com.fish_dan_.data_energistics.common.crafting.pattern.EncodedPatternRecipeReference;
import com.fish_dan_.data_energistics.item.patternprovider.PackagedRecoveryItem;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;
import com.fish_dan_.data_energistics.world.packaged.PackagedRecoveryStore;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Per-provider operations and independent machine-family policies. World objects are resolved afresh each tick. */
public final class PackagedDispatchState {

    private static final int TICK_OPERATIONS = 32;
    private final ObjectList<PackagedOperationState> operations = new ObjectArrayList<>();
    private final Object2IntOpenHashMap<ResourceLocation> cursors = new Object2IntOpenHashMap<>();
    private final Object2ObjectLinkedOpenHashMap<ResourceLocation, ConnectorPolicy> policies = new Object2ObjectLinkedOpenHashMap<>();
    private int tickCursor;
    private @Nullable UUID recoveryReceipt;
    private PackagedReusableState reusable = new PackagedReusableState();

    public PackagedReusableState reusable() {
        return this.reusable;
    }

    public int pendingOperations() {
        return this.operations.size() + this.reusable.pendingOperations();
    }

    public boolean hasWork() {
        return this.recoveryReceipt == null && (!this.operations.isEmpty() || this.reusable.hasWork());
    }

    /** Freezes the source and escrows its exact assets before vanilla drop conversion can truncate long counts. */
    public ItemStack prepareRecoveryDrop(ServerLevel level, BlockPos origin, String kind, PatternProviderReturnInventory returns) {
        if (this.recoveryReceipt == null) {
            if (this.operations.isEmpty() && returns.isEmpty() && !this.reusable.hasState()) return ItemStack.EMPTY;
            var state = new CompoundTag();
            save(state, level.registryAccess());
            var receipt = UUID.randomUUID();
            PackagedRecoveryStore.get(level).deposit(receipt, origin, kind, state, returns.writeToTag(level.registryAccess()));
            this.recoveryReceipt = receipt;
            this.reusable.freeze();
            returns.clear();
        }
        return PackagedRecoveryItem.create(level, origin, this.recoveryReceipt);
    }

    /** Restores once into an empty same-kind host at the original position. Failed checks never consume the receipt. */
    public boolean restoreRecovery(ServerLevel level, BlockPos origin, String kind, ItemStack receipt, PatternProviderReturnInventory returns) {
        UUID id = PackagedRecoveryItem.receipt(level, origin, receipt);
        if (id == null || !returns.isEmpty() || (!this.operations.isEmpty() || this.reusable.hasState()) && !id.equals(this.recoveryReceipt)) return false;
        var store = PackagedRecoveryStore.get(level);
        var payload = store.inspect(id, origin, kind);
        if (payload == null) return false;
        var restored = load(payload.getCompound("state"), level.registryAccess());
        var encodedReturns = payload.getList("returns", Tag.TAG_COMPOUND);
        if (restored.recoveryReceipt != null || encodedReturns.size() > returns.size()) throw new IllegalArgumentException("Invalid recovery destination state");
        for (int slot = 0; slot < encodedReturns.size(); slot++) {
            var entry = encodedReturns.getCompound(slot);
            if (!entry.isEmpty()) {
                var stack = GenericStack.readTag(level.registryAccess(), entry);
                if (stack == null || stack.amount() <= 0) throw new IllegalArgumentException("Invalid packaged recovery return amount");
            }
        }
        this.operations.clear();
        this.operations.addAll(restored.operations);
        this.policies.clear();
        this.policies.putAll(restored.policies);
        this.cursors.clear();
        this.cursors.putAll(restored.cursors);
        this.tickCursor = 0;
        this.recoveryReceipt = null;
        this.reusable = restored.reusable;
        returns.readFromTag(encodedReturns, level.registryAccess());
        store.redeem(id);
        return true;
    }

    public void ensureCanClear() {
        if ((!this.operations.isEmpty() || this.reusable.hasState()) && this.recoveryReceipt == null) throw new IllegalStateException("Cannot erase packaged tasks before recovery handoff");
    }

    public ConnectorPolicy policy(ResourceLocation group) {
        return this.policies.getOrDefault(group, ConnectorPolicy.ROUND_ROBIN);
    }

    public void policy(ResourceLocation group, ConnectorPolicy policy) {
        this.policies.put(group, policy);
    }

    /** Read-only capacity preparation; commit revalidates and owns the fixed machine and complete scaled envelope. */
    public @Nullable CountedCraftingAdmission prepareBatch(ServerLevel level, PackagedRecipeCatalog catalog,
                                                           IPatternDetails pattern, KeyCounter[] prototype, long requestedCount,
                                                           ObjectList<ConnectorLink> remote, ObjectList<ConnectorLink> adjacent,
                                                           @Nullable ConnectorPolicy routePolicy, BooleanSupplier available, Runnable success) {
        if (requestedCount <= 0) throw new IllegalArgumentException("Packaged batch count must be positive");
        if (this.recoveryReceipt != null || !available.getAsBoolean()) return null;
        var stack = pattern.getDefinition().getReadOnlyStack();
        var reference = EncodedPatternRecipeReference.get(stack);
        var recipe = EncodedPatternRecipeReference.getProcessingRecipeId(stack);
        if (reference == null || recipe == null) return null;
        long bounded = requestedCount;
        for (var input : prototype) for (var entry : input) {
            if (entry.getLongValue() <= 0) throw new IllegalArgumentException("Invalid packaged input prototype");
            bounded = Math.min(bounded, Long.MAX_VALUE / entry.getLongValue());
        }
        for (var output : pattern.getOutputs()) bounded = Math.min(bounded, Long.MAX_VALUE / output.amount());
        var claims = PackagedMachineClaims.get(level);
        for (var links : ObjectList.of(remote, adjacent)) {
            for (var adapter : catalog.forType(reference.recipeTypeId())) {
                var candidates = candidates(links);
                if (candidates.isEmpty()) continue;
                var routing = routePolicy == null ? policy(adapter.id()) : routePolicy;
                int start = routing == ConnectorPolicy.ROUND_ROBIN ? Math.floorMod(this.cursors.getInt(adapter.id()), candidates.size()) : 0;
                for (int offset = 0; offset < candidates.size(); offset++) {
                    int index = (start + offset) % candidates.size();
                    var link = candidates.get(index);
                    if (!level.isLoaded(link.position()) || !adapter.recognizes(level, link.position()) || !claims.available(link.position())) continue;
                    long count = adapter.batchCapacity(level, link.position(), link.side(), recipe, pattern, prototype, bounded);
                    if (count < 0 || count > bounded) throw new IllegalStateException("Invalid packaged machine batch capacity");
                    if (count == 0) continue;
                    IPatternDetails batch = count == 1 ? pattern : new PackagedBatchPattern(pattern, count);
                    var inputs = scaledInputs(prototype, count);
                    var preparation = adapter.prepare(level, link.position(), link.side(), recipe, batch, inputs);
                    if (preparation == null) continue;
                    var occupied = adapter.occupiedPositions(level, link.position(), preparation);
                    if (occupied.stream().anyMatch(position -> !level.isLoaded(position) || !claims.available(position))) continue;
                    int next = (index + 1) % candidates.size();
                    return new CountedCraftingAdmission() {

                        private boolean attempted;
                        private boolean transferred;

                        @Override
                        public long count() {
                            return count;
                        }

                        @Override
                        public boolean hasTransferredInputOwnership() {
                            return transferred;
                        }

                        @Override
                        public boolean commit(KeyCounter[] supplied) {
                            if (attempted || supplied != prototype) throw new IllegalStateException("Packaged admission must commit its original prototype once");
                            attempted = true;
                            if (recoveryReceipt != null || !available.getAsBoolean() || !level.isLoaded(link.position()) ||
                                    !adapter.recognizes(level, link.position()) || !claims.available(link.position()))
                                return false;
                            var actualInputs = scaledInputs(supplied, count);
                            if (!sameInputs(inputs, actualInputs)) return false;
                            long currentCapacity = adapter.batchCapacity(level, link.position(), link.side(), recipe, pattern, supplied, count);
                            if (currentCapacity != count) return false;
                            var current = adapter.prepare(level, link.position(), link.side(), recipe, batch, actualInputs);
                            if (current == null) return false;
                            var positions = adapter.occupiedPositions(level, link.position(), current);
                            if (positions.stream().anyMatch(position -> !level.isLoaded(position))) return false;
                            PackagedOutputMatching.save(batch, current, level.registryAccess());
                            var operation = new PackagedOperationState(adapter.id(), recipe, link.position(), link.side(), current, actualInputs, positions);
                            if (!claims.acquireAll(positions, operation.id())) return false;
                            transferred = true;
                            operations.add(operation);
                            for (var input : supplied) input.clear();
                            cursors.put(adapter.id(), next);
                            success.run();
                            return true;
                        }
                    };
                }
            }
        }
        return null;
    }

    private static KeyCounter[] scaledInputs(KeyCounter[] prototype, long count) {
        var inputs = new KeyCounter[prototype.length];
        for (int i = 0; i < inputs.length; i++) {
            inputs[i] = new KeyCounter();
            for (var entry : prototype[i]) inputs[i].add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), count));
        }
        return inputs;
    }

    private static boolean sameInputs(KeyCounter[] expected, KeyCounter[] actual) {
        for (int i = 0; i < expected.length; i++) {
            for (var entry : expected[i]) if (actual[i].get(entry.getKey()) != entry.getLongValue()) return false;
            for (var entry : actual[i]) if (expected[i].get(entry.getKey()) != entry.getLongValue()) return false;
        }
        return true;
    }

    private static ObjectList<ConnectorLink> candidates(ObjectList<ConnectorLink> links) {
        var candidates = new ObjectArrayList<ConnectorLink>();
        var seen = new LongOpenHashSet();
        for (var link : links) {
            if (link.mode().supportsInput() && seen.add(link.position().asLong())) candidates.add(link);
        }
        return candidates;
    }

    public boolean dispatch(ServerLevel level, PackagedRecipeCatalog catalog, IPatternDetails pattern,
                            KeyCounter[] inputs, ObjectList<ConnectorLink> remote, ObjectList<ConnectorLink> adjacent) {
        if (this.recoveryReceipt != null) return false;
        var stack = pattern.getDefinition().getReadOnlyStack();
        var reference = EncodedPatternRecipeReference.get(stack);
        var recipe = EncodedPatternRecipeReference.getProcessingRecipeId(stack);
        if (reference == null || recipe == null) return false;
        var claims = PackagedMachineClaims.get(level);
        for (var adapter : catalog.forType(reference.recipeTypeId())) {
            if (dispatchGroup(level, adapter, claims, recipe, pattern, inputs, remote))
                return true;
        }
        for (var adapter : catalog.forType(reference.recipeTypeId())) {
            if (dispatchGroup(level, adapter, claims, recipe, pattern, inputs, adjacent))
                return true;
        }
        return false;
    }

    private boolean dispatchGroup(ServerLevel level, PackagedMachineAdapter adapter, PackagedMachineClaims claims,
                                  ResourceLocation recipe, IPatternDetails pattern, KeyCounter[] inputs,
                                  ObjectList<ConnectorLink> links) {
        // Keep unloaded links in the ordering so loading a chunk cannot move the round-robin cursor.
        var candidates = candidates(links);
        if (candidates.isEmpty()) return false;
        int start = policy(adapter.id()) == ConnectorPolicy.ROUND_ROBIN ? Math.floorMod(this.cursors.getInt(adapter.id()), candidates.size()) : 0;
        for (int offset = 0; offset < candidates.size(); offset++) {
            int index = (start + offset) % candidates.size();
            var link = candidates.get(index);
            if (!level.isLoaded(link.position()) || !adapter.recognizes(level, link.position())) continue;
            if (!claims.available(link.position())) continue;
            var preparation = adapter.prepare(level, link.position(), link.side(), recipe, pattern, inputs);
            if (preparation == null) continue;
            var occupied = adapter.occupiedPositions(level, link.position(), preparation);
            if (occupied.stream().anyMatch(position -> !level.isLoaded(position))) continue;
            PackagedOutputMatching.save(pattern, preparation, level.registryAccess());
            var operation = new PackagedOperationState(adapter.id(), recipe, link.position(), link.side(), preparation, inputs, occupied);
            if (!claims.acquireAll(operation.occupiedPositions(), operation.id())) continue;
            this.operations.add(operation);
            for (var input : inputs) input.clear();
            this.cursors.put(adapter.id(), (index + 1) % candidates.size());
            return true;
        }
        return false;
    }

    public boolean tick(ServerLevel level, PackagedRecipeCatalog catalog, MEStorage returns, IActionSource source) {
        if (this.recoveryReceipt != null) return false;
        int work = Math.min(TICK_OPERATIONS, this.operations.size());
        boolean changed = this.reusable.tick(level, returns, source);
        while (work-- > 0 && !this.operations.isEmpty()) {
            this.tickCursor = Math.floorMod(this.tickCursor, this.operations.size());
            var operation = this.operations.get(this.tickCursor);
            var adapter = catalog.adapter(operation.adapterId());
            var claims = PackagedMachineClaims.get(level);
            if (claims.structureRemoved(operation.id())) {
                changed |= operation.retireRemovedStructure();
            } else if (adapter != null && claims.acquireAll(operation.occupiedPositions(), operation.id())) {
                changed |= operation.advance(level, adapter);
            }
            changed |= operation.flush(returns, source);
            if (operation.settled()) {
                if (claims.structureRemoved(operation.id())) claims.acknowledgeRemoval(operation.id());
                else claims.releaseAll(operation.occupiedPositions(), operation.id());
                this.operations.remove(this.tickCursor);
                changed = true;
            } else {
                this.tickCursor++;
            }
        }
        return changed;
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("reusable", this.reusable.save(registries));
        if (this.recoveryReceipt != null) tag.putUUID("recovery_receipt", this.recoveryReceipt);
        var tasks = new ListTag();
        for (var operation : this.operations) tasks.add(operation.save(registries));
        tag.put("operations", tasks);
        var groups = new ListTag();
        var ids = new ObjectArrayList<>(this.cursors.keySet());
        for (var id : this.policies.keySet()) if (!ids.contains(id)) ids.add(id);
        for (var id : ids) {
            var group = new CompoundTag();
            group.putString("id", id.toString());
            group.putString("policy", policy(id).name());
            group.putInt("cursor", this.cursors.getInt(id));
            groups.add(group);
        }
        tag.put("groups", groups);
    }

    public static PackagedDispatchState load(CompoundTag tag, HolderLookup.Provider registries) {
        var state = new PackagedDispatchState();
        if (tag.contains("reusable", Tag.TAG_COMPOUND)) state.reusable = PackagedReusableState.load(tag.getCompound("reusable"), registries);
        if (tag.contains("recovery_receipt")) {
            if (!tag.hasUUID("recovery_receipt")) throw new IllegalArgumentException("Invalid packaged recovery receipt");
            state.recoveryReceipt = tag.getUUID("recovery_receipt");
            state.reusable.freeze();
        }
        var positions = new LongOpenHashSet();
        var tasks = tag.getList("operations", Tag.TAG_COMPOUND);
        for (int index = 0; index < tasks.size(); index++) {
            var operation = PackagedOperationState.load(tasks.getCompound(index), registries);
            for (var occupied : operation.occupiedPositions()) {
                if (!positions.add(occupied.asLong())) throw new IllegalArgumentException("Overlapping packaged operation targets");
            }
            state.operations.add(operation);
        }
        var groups = tag.getList("groups", Tag.TAG_COMPOUND);
        for (int index = 0; index < groups.size(); index++) {
            var group = groups.getCompound(index);
            var id = ResourceLocation.parse(group.getString("id"));
            int cursor = group.getInt("cursor");
            if (cursor < 0 || state.cursors.containsKey(id)) throw new IllegalArgumentException("Invalid packaged group cursor");
            state.cursors.put(id, cursor);
            state.policies.put(id, ConnectorPolicy.valueOf(group.getString("policy")));
        }
        return state;
    }
}
