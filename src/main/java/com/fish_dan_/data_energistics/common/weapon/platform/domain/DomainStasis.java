package com.fish_dan_.data_energistics.common.weapon.platform.domain;

import com.fish_dan_.data_energistics.client.weapon.platform.DomainClientState;
import com.fish_dan_.data_energistics.common.data.DEMobEffects;
import com.fish_dan_.data_energistics.common.weapon.platform.flight.FloatingGunFlight;
import com.fish_dan_.data_energistics.common.weapon.platform.projectile.FloatingGunProjectile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Shared spatial predicate for vanilla tick gates, native movement and integrations with their own schedulers. */
public final class DomainStasis {

    private DomainStasis() {}

    public static boolean frozen(Level level, BlockPos position) {
        return level instanceof ServerLevel server ? StasisDomainSavedData.get(server.getServer()).frozen(level, ChunkPos.asLong(position)) : DomainClientState.frozen(level, ChunkPos.asLong(position));
    }

    public static boolean frozen(Entity entity) {
        if (!entity.isAddedToLevel()) return false;
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator()) || entity instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2)) return false;
        if (!(entity.level() instanceof ServerLevel level)) return DomainClientState.frozen(entity);
        var domains = StasisDomainSavedData.get(level.getServer()).at(level.dimension(), entity.chunkPosition().toLong());
        if (domains == null) return false;
        for (var domain : domains) if (!exempt(entity, domain.owner())) return true;
        return false;
    }

    public static boolean exempt(Entity entity, UUID owner) {
        return entity.getUUID().equals(owner) || entity instanceof FloatingGunFlight gun && owner.equals(gun.ownerId()) || entity instanceof FloatingGunProjectile projectile && owner.equals(projectile.ownerId());
    }

    public static boolean defer(Entity target, DeferredImpact.Payload payload) {
        if (!(target.level() instanceof ServerLevel level) || !frozen(target)) return false;
        var data = StasisDomainSavedData.get(level.getServer());
        data.defer(target, payload);
        return true;
    }

    /** Called before native counters advance; the short marker resumes its ordinary expiry after thaw. */
    public static boolean freezeTick(Entity entity) {
        if (!frozen(entity)) return false;
        if (entity instanceof LivingEntity living && entity.level() instanceof ServerLevel && !living.hasEffect(DEMobEffects.RADIX_STASIS)) living.addEffect(new MobEffectInstance(DEMobEffects.RADIX_STASIS, 2, 0, false, false, true));
        return true;
    }
}
