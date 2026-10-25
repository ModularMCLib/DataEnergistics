package com.fish_dan_.data_energistics.client.weapon.platform;

import com.fish_dan_.data_energistics.client.render.domain.DomainRenderCache;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainVisualSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomain;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class DomainClientState {

    private static final Object2ObjectOpenHashMap<ResourceKey<Level>, Long2ObjectOpenHashMap<ObjectList<StasisDomain>>> INDEX = new Object2ObjectOpenHashMap<>();
    private static final ObjectOpenHashSet<UUID> EXEMPT = new ObjectOpenHashSet<>();
    public static ObjectList<StasisDomain> domains = ObjectList.of();

    public static void accept(DomainVisualSnapshot snapshot) {
        INDEX.clear();
        EXEMPT.clear();
        domains = snapshot.domains();
        EXEMPT.addAll(snapshot.exemptPlayers());
        for (var domain : domains) {
            var world = INDEX.computeIfAbsent(domain.dimension(), ignored -> new Long2ObjectOpenHashMap<>());
            for (int x = domain.x() - domain.size() / 2; x <= domain.x() + domain.size() / 2; x++) for (int z = domain.z() - domain.size() / 2; z <= domain.z() + domain.size() / 2; z++) world.computeIfAbsent(ChunkPos.asLong(x, z), ignored -> new ObjectArrayList<>()).add(domain);
        }
        DomainRenderCache.retain();
    }

    public static @Nullable StasisDomain covering(@Nullable Level level, long chunk, @Nullable Entity entity) {
        if (level == null) return null;
        var world = INDEX.get(level.dimension());
        if (world == null) return null;
        var covering = world.get(chunk);
        if (covering == null) return null;
        for (var domain : covering) if (entity == null || !DomainStasis.exempt(entity, domain.owner())) return domain;
        return null;
    }

    public static boolean frozen(Level level, long chunk) {
        var world = INDEX.get(level.dimension());
        return world != null && world.containsKey(chunk);
    }

    public static boolean frozen(Entity entity) {
        if (EXEMPT.contains(entity.getUUID()) || entity instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        var world = INDEX.get(entity.level().dimension());
        if (world == null) return false;
        var covering = world.get(entity.chunkPosition().toLong());
        if (covering == null) return false;
        for (var domain : covering) if (!DomainStasis.exempt(entity, domain.owner())) return true;
        return false;
    }

    public static void clear() {
        INDEX.clear();
        EXEMPT.clear();
        domains = ObjectList.of();
        DomainRenderCache.clear();
    }
}
