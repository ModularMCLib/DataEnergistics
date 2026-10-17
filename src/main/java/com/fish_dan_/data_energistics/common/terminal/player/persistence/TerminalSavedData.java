package com.fish_dan_.data_energistics.common.terminal.player.persistence;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.UUID;

/**
 * World-scoped, UUID-owned terminal inventories; malformed entries retain their original data.
 */
public final class TerminalSavedData extends SavedData {

    private static final Factory<TerminalSavedData> FACTORY = new Factory<>(TerminalSavedData::new, TerminalSavedData::load);
    private final Object2ObjectMap<UUID, TerminalPlayerState> players = new Object2ObjectLinkedOpenHashMap<>();
    private final ObjectSet<UUID> quarantinedOwners = new ObjectOpenHashSet<>();
    private final ObjectList<CompoundTag> quarantined = new ObjectArrayList<>();

    public static TerminalSavedData get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Player terminal data requires the server thread");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "data_energistics_player_terminal");
    }

    public TerminalPlayerState player(UUID id) {
        if (quarantinedOwners.contains(id))
            throw new IllegalStateException("Player terminal inventory is quarantined: " + id);
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
        quarantined.forEach(value -> list.add(value.copy()));
        tag.put("players", list);
        return tag;
    }

    private static TerminalSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TerminalSavedData data = new TerminalSavedData();
        Object2ObjectMap<UUID, CompoundTag> originals = new Object2ObjectLinkedOpenHashMap<>();
        for (Tag value : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.hasUUID("player")) {
                data.quarantined.add(entry.copy());
                Data_Energistics.LOGGER.error("Preserved a player terminal inventory with an invalid owner UUID");
                continue;
            }
            UUID id = entry.getUUID("player");
            if (originals.containsKey(id)) {
                if (data.quarantinedOwners.add(id)) {
                    data.players.remove(id);
                    data.quarantined.add(originals.get(id));
                }
                data.quarantined.add(entry.copy());
                Data_Energistics.LOGGER.error("Preserved duplicate player terminal inventories for {}", id);
                continue;
            }
            originals.put(id, entry.copy());
            try {
                data.players.put(id, TerminalPlayerState.load(entry, registries, data::setDirty));
            } catch (RuntimeException error) {
                data.quarantinedOwners.add(id);
                data.quarantined.add(entry.copy());
                Data_Energistics.LOGGER.error("Preserved malformed player terminal inventory for {}", id, error);
            }
        }
        return data;
    }
}
