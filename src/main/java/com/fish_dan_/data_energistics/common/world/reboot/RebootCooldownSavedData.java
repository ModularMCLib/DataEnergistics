package com.fish_dan_.data_energistics.common.world.reboot;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.UUID;

/** A UUID's cooldown is shared across equipment, dimensions, player deaths and server restarts. */
public final class RebootCooldownSavedData extends SavedData {

    public static final int COOLDOWN_TICKS = 20 * 60 * 10;
    private static final String DATA_NAME = Data_Energistics.MODID + "_radix_reboot_cooldowns";
    private static final Factory<RebootCooldownSavedData> FACTORY = new Factory<>(RebootCooldownSavedData::new, RebootCooldownSavedData::load);
    private final Object2LongOpenHashMap<UUID> deadlines = new Object2LongOpenHashMap<>();

    public static RebootCooldownSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public int remaining(MinecraftServer server, UUID player) {
        long until = deadlines.getLong(player);
        if (until == 0) return 0;
        long remaining = until - server.overworld().getGameTime();
        if (remaining <= 0) {
            deadlines.removeLong(player);
            setDirty();
            return 0;
        }
        return (int) Math.min(COOLDOWN_TICKS, remaining);
    }

    public void begin(MinecraftServer server, UUID player) {
        deadlines.put(player, server.overworld().getGameTime() + COOLDOWN_TICKS);
        setDirty();
    }

    private static RebootCooldownSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new RebootCooldownSavedData();
        for (Tag value : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.hasUUID("player") || !entry.contains("until", Tag.TAG_LONG) || entry.getLong("until") <= 0) {
                Data_Energistics.LOGGER.warn("Ignoring invalid radix reboot cooldown entry in world data");
                continue;
            }
            data.deadlines.put(entry.getUUID("player"), entry.getLong("until"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var players = new ListTag();
        for (var entry : deadlines.object2LongEntrySet()) {
            var player = new CompoundTag();
            player.putUUID("player", entry.getKey());
            player.putLong("until", entry.getLongValue());
            players.add(player);
        }
        tag.put("players", players);
        return tag;
    }
}
