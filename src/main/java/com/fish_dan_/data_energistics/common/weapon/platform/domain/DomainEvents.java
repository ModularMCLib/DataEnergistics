package com.fish_dan_.data_energistics.common.weapon.platform.domain;

import com.fish_dan_.data_energistics.common.data.DEMobEffects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class DomainEvents {

    @SubscribeEvent
    public void tick(ServerTickEvent.Pre event) {
        StasisDomainSavedData.get(event.getServer()).tick(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void entity(EntityTickEvent.Pre event) {
        var entity = event.getEntity();
        if (!DomainStasis.frozen(entity)) return;
        if (entity instanceof LivingEntity living && entity.level() instanceof ServerLevel && !living.hasEffect(DEMobEffects.RADIX_STASIS)) living.addEffect(new MobEffectInstance(DEMobEffects.RADIX_STASIS, 2, 0, false, false, true));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void damage(LivingIncomingDamageEvent event) {
        if (DomainStasis.defer(event.getEntity(), new DeferredImpact.Damage(DeferredSource.capture(event.getSource()), event.getAmount(), false))) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void died(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) StasisDomainSavedData.get(level.getServer()).discard(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) StasisDomainSavedData.get(level.getServer()).endOwner(level.getServer(), event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        StasisDomainSavedData.release(event.getServer());
    }

    @SubscribeEvent
    public void unloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) StasisDomainSavedData.get(level.getServer()).unload(chunk);
    }

    @SubscribeEvent
    public void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (DomainStasis.frozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void rightBlock(PlayerInteractEvent.RightClickBlock event) {
        if (DomainStasis.frozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void rightItem(PlayerInteractEvent.RightClickItem event) {
        if (DomainStasis.frozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void entityInteract(PlayerInteractEvent.EntityInteract event) {
        if (DomainStasis.frozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void breakBlock(BlockEvent.BreakEvent event) {
        if (DomainStasis.frozen(event.getPlayer())) event.setCanceled(true);
    }
}
