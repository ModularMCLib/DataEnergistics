package com.fish_dan_.data_energistics.client.weapon.platform;

import com.fish_dan_.data_energistics.client.render.domain.DomainRenderCache;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainVisualSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomain;
import com.fish_dan_.data_energistics.integration.MOD;
import com.fish_dan_.data_energistics.integration.library.flywheel.domain.DomainVisualSubjects;

import net.minecraft.client.Minecraft;
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

    private record View(Object2ObjectOpenHashMap<ResourceKey<Level>, Long2ObjectOpenHashMap<ObjectList<StasisDomain>>> index, ObjectOpenHashSet<UUID> exemptions) {}

    private static volatile View view = new View(new Object2ObjectOpenHashMap<>(), new ObjectOpenHashSet<>());
    public static ObjectList<StasisDomain> domains = ObjectList.of();

    public static void accept(DomainVisualSnapshot snapshot) {
        var index = new Object2ObjectOpenHashMap<ResourceKey<Level>, Long2ObjectOpenHashMap<ObjectList<StasisDomain>>>();
        var exemptions = new ObjectOpenHashSet<>(snapshot.exemptPlayers());
        domains = snapshot.domains();
        for (var domain : domains) {
            var world = index.computeIfAbsent(domain.dimension(), ignored -> new Long2ObjectOpenHashMap<>());
            for (int x = domain.x() - domain.size() / 2; x <= domain.x() + domain.size() / 2; x++) for (int z = domain.z() - domain.size() / 2; z <= domain.z() + domain.size() / 2; z++) world.computeIfAbsent(ChunkPos.asLong(x, z), ignored -> new ObjectArrayList<>()).add(domain);
        }
        view = new View(index, exemptions);
        DomainRenderCache.retain();
    }

    public static @Nullable StasisDomain covering(@Nullable Level level, long chunk, @Nullable Entity entity) {
        if (level == null || level != Minecraft.getInstance().level) return null;
        var world = view.index.get(level.dimension());
        if (world == null) return null;
        var covering = world.get(chunk);
        if (covering == null) return null;
        for (var domain : covering) if (entity == null || !DomainStasis.exempt(entity, domain.owner())) return domain;
        return null;
    }

    public static boolean frozen(Level level, long chunk) {
        if (level != Minecraft.getInstance().level) return false;
        var world = view.index.get(level.dimension());
        return world != null && world.containsKey(chunk);
    }

    public static boolean frozen(Entity entity) {
        if (entity.level() != Minecraft.getInstance().level) return false;
        var current = view;
        if (current.exemptions.contains(entity.getUUID()) || entity instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        var world = current.index.get(entity.level().dimension());
        if (world == null) return false;
        var covering = world.get(entity.chunkPosition().toLong());
        if (covering == null) return false;
        for (var domain : covering) if (!DomainStasis.exempt(entity, domain.owner())) return true;
        return false;
    }

    public static void clear() {
        view = new View(new Object2ObjectOpenHashMap<>(), new ObjectOpenHashSet<>());
        domains = ObjectList.of();
        DomainRenderCache.clear();
        if (MOD.FLYWHEEL.isLoaded()) DomainVisualSubjects.clear();
    }
}
