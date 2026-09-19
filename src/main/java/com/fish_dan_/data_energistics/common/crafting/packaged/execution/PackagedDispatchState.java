package com.fish_dan_.data_energistics.common.crafting.packaged.execution;

import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorPolicy;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedRecipeCatalog;
import com.fish_dan_.data_energistics.common.crafting.pattern.EncodedPatternRecipeReference;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Per-provider operations and independent machine-family policies. World objects are resolved afresh each tick. */
public final class PackagedDispatchState {

    private static final int TICK_OPERATIONS = 32;
    private final ObjectList<PackagedOperationState> operations = new ObjectArrayList<>();
    private final Object2IntOpenHashMap<ResourceLocation> cursors = new Object2IntOpenHashMap<>();
    private final Object2ObjectLinkedOpenHashMap<ResourceLocation, ConnectorPolicy> policies = new Object2ObjectLinkedOpenHashMap<>();
    private int tickCursor;

    public int pendingOperations() {
        return this.operations.size();
    }

    public int failedOperations() {
        int failed = 0;
        for (var operation : this.operations) if (operation.failure() != null) failed++;
        return failed;
    }

    public boolean hasWork() {
        return !this.operations.isEmpty();
    }

    public ConnectorPolicy policy(ResourceLocation group) {
        return this.policies.getOrDefault(group, ConnectorPolicy.ROUND_ROBIN);
    }

    public void policy(ResourceLocation group, ConnectorPolicy policy) {
        this.policies.put(group, policy);
    }

    public boolean dispatch(ServerLevel level, PackagedRecipeCatalog catalog, IPatternDetails pattern,
                            KeyCounter[] inputs, ObjectList<ConnectorLink> remote, ObjectList<ConnectorLink> adjacent) {
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
        var candidates = new ObjectArrayList<ConnectorLink>();
        var seen = new LongOpenHashSet();
        for (var link : links) {
            // Keep unloaded links in the ordering so loading a chunk cannot move the round-robin cursor.
            if (link.mode().supportsInput() && seen.add(link.position().asLong()))
                candidates.add(link);
        }
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
        int work = Math.min(TICK_OPERATIONS, this.operations.size());
        boolean changed = false;
        while (work-- > 0 && !this.operations.isEmpty()) {
            this.tickCursor = Math.floorMod(this.tickCursor, this.operations.size());
            var operation = this.operations.get(this.tickCursor);
            var adapter = catalog.adapter(operation.adapterId());
            var claims = PackagedMachineClaims.get(level);
            if (adapter != null && claims.acquireAll(operation.occupiedPositions(), operation.id())) {
                changed |= operation.advance(level, adapter);
            }
            changed |= operation.flush(returns, source);
            if (operation.settled()) {
                claims.releaseAll(operation.occupiedPositions(), operation.id());
                this.operations.remove(this.tickCursor);
                changed = true;
            } else {
                this.tickCursor++;
            }
        }
        return changed;
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
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
