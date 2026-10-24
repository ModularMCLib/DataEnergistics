package com.fish_dan_.data_energistics.common.world.reboot;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.UUID;

/** A UUID's cooldown is shared across equipment, dimensions, player deaths and server restarts. */
public final class RebootCooldownSavedData extends SavedData {

    public static final int COOLDOWN_TICKS = 20 * 60 * 10;
    private static final String DATA_NAME = Data_Energistics.MODID + "_radix_reboot_cooldowns";
    private static final Codec<RebootCooldownSavedData> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC,
            Codec.LONG.validate(value -> value > 0 ? DataResult.success(value) : DataResult.error(() -> "Reboot deadline must be positive")))
            .fieldOf("players").codec().xmap(entries -> {
                var data = new RebootCooldownSavedData();
                data.deadlines.putAll(entries);
                return data;
            }, data -> data.deadlines);
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
        return CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }
}
