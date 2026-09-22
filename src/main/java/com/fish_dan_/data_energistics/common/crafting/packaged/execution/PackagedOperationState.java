package com.fish_dan_.data_energistics.common.crafting.packaged.execution;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;
import com.fish_dan_.data_energistics.world.packaged.PackagedRecoveryJournal;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.UUID;

/** Durable ownership of one accepted input envelope and all physically recovered outputs. */
public final class PackagedOperationState implements PackagedMachineOperation {

    private final UUID id;
    private final ResourceLocation adapterId;
    private final ResourceLocation recipeId;
    private final BlockPos position;
    private final ObjectList<BlockPos> occupiedPositions;
    private final Direction face;
    private final CompoundTag progress;
    private final Object2ObjectMap<AEKey, BigInteger> inputs;
    private final Object2ObjectMap<AEKey, BigInteger> outputs;
    private boolean complete;
    private boolean machineReleased;
    private @Nullable ServerLevel activeLevel;
    private boolean changed;
    private long nextErrorLog;

    public PackagedOperationState(ResourceLocation adapterId, ResourceLocation recipeId, BlockPos position,
                                  Direction face, CompoundTag preparation, KeyCounter[] inputs) {
        this(adapterId, recipeId, position, face, preparation, inputs, ObjectList.of(position));
    }

    public PackagedOperationState(ResourceLocation adapterId, ResourceLocation recipeId, BlockPos position,
                                  Direction face, CompoundTag preparation, KeyCounter[] inputs, ObjectList<BlockPos> occupiedPositions) {
        this(UUID.randomUUID(), adapterId, recipeId, position, face, preparation.copy(),
                PackagedAmounts.capture(inputs), new Object2ObjectLinkedOpenHashMap<>(), false, occupiedPositions);
    }

    private PackagedOperationState(UUID id, ResourceLocation adapterId, ResourceLocation recipeId,
                                   BlockPos position, Direction face, CompoundTag progress,
                                   Object2ObjectMap<AEKey, BigInteger> inputs,
                                   Object2ObjectMap<AEKey, BigInteger> outputs, boolean complete,
                                   ObjectList<BlockPos> occupiedPositions) {
        this.id = id;
        this.adapterId = adapterId;
        this.recipeId = recipeId;
        this.position = position.immutable();
        var distinct = new LongOpenHashSet();
        var positions = new ObjectArrayList<BlockPos>();
        for (var occupied : occupiedPositions) {
            if (distinct.add(occupied.asLong())) positions.add(occupied.immutable());
        }
        if (!distinct.contains(position.asLong())) throw new IllegalArgumentException("Packaged reservation must include its main machine");
        this.occupiedPositions = ObjectLists.unmodifiable(positions);
        this.face = face;
        this.progress = progress;
        this.inputs = inputs;
        this.outputs = outputs;
        this.complete = complete;
    }

    /** Exceptions are logged here; the operation remains retryable on the next machine tick. */
    public boolean advance(ServerLevel level, PackagedMachineAdapter adapter) {
        if (this.complete || !level.isLoaded(this.position)) return false;
        this.activeLevel = level;
        this.changed = false;
        try {
            if (!adapter.id().equals(this.adapterId)) throw new IllegalArgumentException("Packaged adapter changed identity");
            return adapter.advance(this) || this.changed;
        } catch (RuntimeException exception) {
            if (level.getGameTime() >= this.nextErrorLog) {
                Data_Energistics.LOGGER.error("Packaged operation {} ({}, recipe {}, at {}) remains pending", this.id, this.adapterId, this.recipeId, this.position, exception);
                this.nextErrorLog = level.getGameTime() + 1200;
            }
            return true;
        } finally {
            this.activeLevel = null;
        }
    }

    public boolean flush(MEStorage returnInventory, IActionSource source) {
        boolean worked = false;
        var iterator = this.outputs.object2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long offered = entry.getValue().min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
            long accepted = returnInventory.insert(entry.getKey(), offered, Actionable.MODULATE, source);
            if (accepted < 0 || accepted > offered) throw new IllegalStateException("Invalid packaged return insertion");
            if (accepted == 0) continue;
            var remaining = entry.getValue().subtract(BigInteger.valueOf(accepted));
            if (remaining.signum() == 0) iterator.remove();
            else entry.setValue(remaining);
            worked = true;
        }
        return worked;
    }

    @Override
    public UUID id() {
        return this.id;
    }

    public ResourceLocation adapterId() {
        return this.adapterId;
    }

    public ObjectList<BlockPos> occupiedPositions() {
        return this.occupiedPositions;
    }

    @Override
    public ResourceLocation recipeId() {
        return this.recipeId;
    }

    @Override
    public BlockPos position() {
        return this.position;
    }

    @Override
    public Direction face() {
        return this.face;
    }

    @Override
    public CompoundTag progress() {
        return this.progress;
    }

    public boolean settled() {
        return this.complete && this.outputs.isEmpty();
    }

    /** Native completion before external output transfer, used by the reusable input custody bridge. */
    public boolean completed() {
        return this.complete;
    }

    public boolean machineReleased() {
        return this.machineReleased;
    }

    /** Server-thread recovery of detached custody; outputs stay owned here after world claims end. */
    public boolean recoverDetached(ServerLevel level, PackagedMachineAdapter adapter) {
        if (this.machineReleased) return false;
        PackagedMachineClaims.get(level).retireOperation(this.id);
        if (this.occupiedPositions.stream().anyMatch(position -> !level.isLoaded(position))) return false;
        boolean changed = recoverRemoved(level, adapter);
        if (this.complete) {
            releaseMachine(level);
            return true;
        }
        return changed;
    }

    /** Releases physical ownership once, only after native work has finished or been recovered. */
    public void releaseMachine(ServerLevel level) {
        if (this.machineReleased) return;
        if (!this.complete) throw new IllegalStateException("Cannot release an unfinished packaged machine");
        var claims = PackagedMachineClaims.get(level);
        if (claims.structureRemoved(this.id)) claims.acknowledgeRemoval(this.id);
        else claims.releaseAll(this.occupiedPositions, this.id);
        PackagedRecoveryJournal.get(level).release(this.id);
        this.machineReleased = true;
    }

    /** Immutable evidence of actual collected assets; reading does not transfer or duplicate ownership. */
    public ObjectList<GenericStack> collectedOutputs() {
        var result = new ObjectArrayList<GenericStack>();
        this.outputs.forEach((key, amount) -> result.add(new GenericStack(key, amount.longValueExact())));
        return ObjectLists.unmodifiable(result);
    }

    /** Retires a dismantled structure, returning only undelivered inputs and already collected outputs. */
    public boolean retireRemovedStructure() {
        if (this.complete) return false;
        this.inputs.forEach((key, amount) -> this.outputs.merge(key, amount, BigInteger::add));
        this.inputs.clear();
        this.complete = true;
        return true;
    }

    /** Recovers adapter-owned world assets before settling a removed structure. */
    public boolean recoverRemoved(ServerLevel level, PackagedMachineAdapter adapter) {
        if (this.complete) return false;
        this.activeLevel = level;
        this.changed = false;
        try {
            if (adapter.recoverRemoved(this)) return retireRemovedStructure();
            return this.changed;
        } catch (RuntimeException exception) {
            if (level.getGameTime() >= this.nextErrorLog) {
                Data_Energistics.LOGGER.error("Packaged recovery {} ({}, recipe {}, at {}) remains pending", this.id, this.adapterId, this.recipeId, this.position, exception);
                this.nextErrorLog = level.getGameTime() + 1200;
            }
            return true;
        } finally {
            this.activeLevel = null;
        }
    }

    @Override
    public ServerLevel level() {
        if (this.activeLevel == null) throw new IllegalStateException("Packaged operation used outside its tick");
        return this.activeLevel;
    }

    @Override
    public BigInteger available(AEKey key) {
        return this.inputs.getOrDefault(key, BigInteger.ZERO);
    }

    @Override
    public void delivered(AEKey key, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Delivered amount must be positive");
        var remaining = available(key).subtract(BigInteger.valueOf(amount));
        if (remaining.signum() < 0) throw new IllegalStateException("Packaged input overdraft");
        if (remaining.signum() == 0) this.inputs.remove(key);
        else this.inputs.put(key, remaining);
        changed();
    }

    @Override
    public void returned(AEKey key, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Returned amount must be positive");
        this.outputs.merge(key, BigInteger.valueOf(amount), BigInteger::add);
        changed();
    }

    @Override
    public void changed() {
        this.changed = true;
    }

    @Override
    public void complete() {
        if (!this.inputs.isEmpty()) throw new IllegalStateException("Packaged operation completed with unassigned inputs");
        this.complete = true;
        changed();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putUUID("id", this.id);
        tag.putString("adapter", this.adapterId.toString());
        tag.putString("recipe", this.recipeId.toString());
        tag.putLong("position", this.position.asLong());
        tag.putLongArray("occupied", this.occupiedPositions.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putString("face", this.face.getName());
        tag.put("progress", this.progress.copy());
        tag.put("inputs", PackagedAmounts.save(this.inputs, registries));
        tag.put("outputs", PackagedAmounts.save(this.outputs, registries));
        tag.putBoolean("complete", this.complete);
        tag.putBoolean("machine_released", this.machineReleased);
        return tag;
    }

    public static PackagedOperationState load(CompoundTag tag, HolderLookup.Provider registries) {
        var face = Direction.byName(tag.getString("face"));
        if (face == null) throw new IllegalArgumentException("Invalid packaged target face");
        var inputs = PackagedAmounts.load(tag.getList("inputs", Tag.TAG_COMPOUND), registries);
        boolean complete = tag.getBoolean("complete");
        var occupied = new ObjectArrayList<BlockPos>();
        if (!tag.contains("occupied", Tag.TAG_LONG_ARRAY)) throw new IllegalArgumentException("Missing packaged operation reservations");
        for (long position : tag.getLongArray("occupied")) occupied.add(BlockPos.of(position));
        if (complete && !inputs.isEmpty()) throw new IllegalArgumentException("Completed packaged task still owns inputs");
        var operation = new PackagedOperationState(tag.getUUID("id"), ResourceLocation.parse(tag.getString("adapter")),
                ResourceLocation.parse(tag.getString("recipe")), BlockPos.of(tag.getLong("position")), face,
                tag.getCompound("progress").copy(), inputs,
                PackagedAmounts.load(tag.getList("outputs", Tag.TAG_COMPOUND), registries), complete, occupied);
        operation.machineReleased = tag.getBoolean("machine_released");
        if (operation.machineReleased && !complete) throw new IllegalArgumentException("Released packaged machine still has unfinished work");
        return operation;
    }
}
