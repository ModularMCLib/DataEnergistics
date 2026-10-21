package com.fish_dan_.data_energistics.common.item.powered.sanctifier;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.UUID;

/** Server-thread ownership of thrown secondary blades, including entities in unloaded chunks. */
public final class SanctifierBladeRecovery extends SavedData {

    private static final Factory<SanctifierBladeRecovery> FACTORY = new Factory<>(SanctifierBladeRecovery::new, SanctifierBladeRecovery::load);
    private final Object2ObjectMap<UUID, UUID> owners = new Object2ObjectOpenHashMap<>();

    public static SanctifierBladeRecovery get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "data_energistics_sanctifier_blades");
    }

    /**
     * Resolves a returned blade before checking both the source item and its caster; switching weapons cannot bypass
     * it.
     */
    public boolean canCharge(ServerPlayer player, ItemStack stack) {
        synchronize(stack);
        return !stack.has(DEDataComponents.SANCTIFIER_BLADE.get()) && !owners.containsValue(player.getUUID());
    }

    /**
     * Called after the projectile was accepted by the world; the item component also synchronizes the missing model.
     */
    public void launch(ServerPlayer owner, ItemStack source, UUID blade) {
        if (!canCharge(owner, source)) throw new IllegalStateException("Sanctifier secondary blade is already deployed");
        owners.put(blade, owner.getUUID());
        source.set(DEDataComponents.SANCTIFIER_BLADE.get(), blade);
        setDirty();
    }

    public boolean belongsTo(UUID blade, UUID owner) {
        return owner.equals(owners.get(blade));
    }

    /**
     * Recovery is immediate and adds no cooldown; a source stack elsewhere settles on its next server inventory tick.
     */
    public void recover(UUID blade) {
        if (!owners.containsKey(blade)) return;
        owners.remove(blade);
        setDirty();
    }

    public void synchronize(ItemStack stack) {
        UUID blade = stack.get(DEDataComponents.SANCTIFIER_BLADE.get());
        if (blade != null && !owners.containsKey(blade)) stack.remove(DEDataComponents.SANCTIFIER_BLADE.get());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        owners.forEach((blade, owner) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Blade", blade);
            entry.putUUID("Owner", owner);
            list.add(entry);
        });
        tag.put("Blades", list);
        return tag;
    }

    private static SanctifierBladeRecovery load(CompoundTag tag, HolderLookup.Provider registries) {
        SanctifierBladeRecovery recovery = new SanctifierBladeRecovery();
        ListTag list = tag.getList("Blades", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (entry.hasUUID("Blade") && entry.hasUUID("Owner")) recovery.owners.put(entry.getUUID("Blade"), entry.getUUID("Owner"));
        }
        return recovery;
    }
}
