package com.fish_dan_.data_energistics.integration.magic.goety.packaged.storage;

import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.List;
import java.util.UUID;

/** Server-thread receipts of native consumption survive destruction of the altar block entity. */
public final class DarkAltarRecoveryLedger extends SavedData {

    private static final Factory<DarkAltarRecoveryLedger> FACTORY = new Factory<>(DarkAltarRecoveryLedger::new, DarkAltarRecoveryLedger::load);
    private final Object2ObjectOpenHashMap<UUID, CompoundTag> operations = new Object2ObjectOpenHashMap<>();

    public static DarkAltarRecoveryLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "data_energistics_dark_altar_recovery");
    }

    /** Establishes that a newly started operation has no consumption preceding these receipts. */
    public void begin(UUID operation) {
        if (this.operations.containsKey(operation)) return;
        var data = new CompoundTag();
        data.putBoolean("observed", true);
        write(operation, data);
    }

    /** Returns an isolated snapshot; cursor changes must be saved with write. */
    public CompoundTag read(UUID operation) {
        var data = this.operations.get(operation);
        return data == null ? new CompoundTag() : data.copy();
    }

    public void write(UUID operation, CompoundTag data) {
        if (data.equals(this.operations.get(operation))) return;
        this.operations.put(operation, data.copy());
        setDirty();
    }

    /** Copies only items the native ritual has actually removed, never recipe templates. */
    public void recordConsumed(UUID operation, List<ItemStack> consumed, HolderLookup.Provider registries) {
        var data = read(operation);
        if (data.getBoolean("completed")) return;
        ListTag receipts = data.getList("consumed", Tag.TAG_COMPOUND);
        if (consumed.size() < receipts.size()) return;
        for (int index = receipts.size(); index < consumed.size(); index++) {
            ItemStack stack = consumed.get(index);
            if (stack.isEmpty()) throw new IllegalStateException("Native Goety consumption receipt is empty");
            var entry = new CompoundTag();
            entry.put("input", stack.save(registries));
            // Goety emits a bucket before consulting crafting remainders. Transformed inputs
            // are never recreated: recovery returns only their physical native return entities.
            boolean transformed = stack.getItem() instanceof BucketItem bucket && !bucket.content.defaultFluidState().isEmpty() || stack.hasCraftingRemainingItem();
            entry.putBoolean("refund_input", !transformed);
            receipts.add(entry);
        }
        data.put("consumed", receipts);
        data.putBoolean("observed", true);
        write(operation, data);
    }

    /** Captures live receipts before native clear/stop, or after a tick that consumed ingredients. */
    public static void capture(DarkAltarBlockEntity altar) {
        if (!(altar.getLevel() instanceof ServerLevel level)) return;
        var owner = PackagedMachineClaims.get(level).owner(altar.getBlockPos());
        if (owner == null || altar.getCurrentRitualRecipe() == null && altar.consumedIngredients.isEmpty()) return;
        get(level).recordConsumed(owner, altar.consumedIngredients, level.registryAccess());
    }

    public void completed(UUID operation) {
        var data = read(operation);
        data.putBoolean("observed", true);
        data.putBoolean("completed", true);
        write(operation, data);
    }

    /** Called after successful collection or after the recovery cursor and physical inventories settle. */
    public void release(UUID operation) {
        if (this.operations.remove(operation) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        this.operations.forEach((operation, data) -> {
            var entry = new CompoundTag();
            entry.putUUID("operation", operation);
            entry.put("data", data.copy());
            entries.add(entry);
        });
        tag.put("operations", entries);
        return tag;
    }

    private static DarkAltarRecoveryLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        var ledger = new DarkAltarRecoveryLedger();
        for (var encoded : tag.getList("operations", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) encoded;
            if (!entry.hasUUID("operation") || !entry.contains("data", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid Dark Altar recovery entry");
            if (ledger.operations.putIfAbsent(entry.getUUID("operation"), entry.getCompound("data").copy()) != null) throw new IllegalArgumentException("Duplicate Dark Altar recovery operation");
        }
        return ledger;
    }
}
