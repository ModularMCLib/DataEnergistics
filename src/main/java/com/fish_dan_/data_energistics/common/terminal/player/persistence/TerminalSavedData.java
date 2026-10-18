package com.fish_dan_.data_energistics.common.terminal.player.persistence;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

import java.util.UUID;

/**
 * World-scoped, UUID-owned terminal inventories.
 */
public final class TerminalSavedData extends SavedData {

    private static final Factory<TerminalSavedData> FACTORY = new Factory<>(TerminalSavedData::new, TerminalSavedData::load);
    private final Object2ObjectMap<UUID, TerminalPlayerState> players = new Object2ObjectLinkedOpenHashMap<>();

    public static TerminalSavedData get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Player terminal data requires the server thread");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "data_energistics_player_terminal");
    }

    public TerminalPlayerState player(UUID id) {
        return players.computeIfAbsent(id, unused -> {
            setDirty();
            return new TerminalPlayerState(this::setDirty);
        });
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : players.entrySet()) {
            CompoundTag value = entry.getValue().save(registries);
            value.putUUID("player", entry.getKey());
            list.add(value);
        }
        tag.put("players", list);
        return tag;
    }

    private static TerminalSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TerminalSavedData data = new TerminalSavedData();
        for (Tag value : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            UUID id = entry.getUUID("player");
            data.players.put(id, TerminalPlayerState.load(entry, registries, data::setDirty));
        }
        return data;
    }
}
