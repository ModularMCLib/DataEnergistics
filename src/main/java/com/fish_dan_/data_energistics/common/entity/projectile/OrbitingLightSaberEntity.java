package com.fish_dan_.data_energistics.common.entity.projectile;

import com.fish_dan_.data_energistics.common.data.DEEntities;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.item.powered.LightSaberColorData;
import com.fish_dan_.data_energistics.common.item.powered.PoweredSwordItem;
import com.fish_dan_.data_energistics.common.item.powered.PoweredToolResearch;
import com.fish_dan_.data_energistics.common.item.powered.lightsaber.LightSaberOrbitEnergy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.UUID;

public final class OrbitingLightSaberEntity extends Entity {

    private static final EntityDataAccessor<Integer> CASTER = SynchedEntityData.defineId(OrbitingLightSaberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(OrbitingLightSaberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COUNT = SynchedEntityData.defineId(OrbitingLightSaberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(OrbitingLightSaberEntity.class, EntityDataSerializers.LONG);
    private final Object2LongMap<UUID> lastHits = new Object2LongOpenHashMap<>();
    private UUID ownerId;
    private UUID sourceId;
    private float damage;

    public OrbitingLightSaberEntity(EntityType<? extends OrbitingLightSaberEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void toggle(ServerPlayer caster, ItemStack stack) {
        LightSaberOrbitEnergy.Charge existing = LightSaberOrbitEnergy.find(stack);
        if (existing == null) existing = LightSaberOrbitEnergy.get(caster.server).findOwner(caster.getUUID());
        if (existing != null) {
            Entity active = caster.serverLevel().getEntity(existing.entity());
            if (active instanceof OrbitingLightSaberEntity orbit) orbit.discard();
            else LightSaberOrbitEnergy.get(caster.server).close(existing.id(), caster.server.overworld().getGameTime());
            return;
        }
        OrbitingLightSaberEntity entity = new OrbitingLightSaberEntity(DEEntities.ORBITING_LIGHT_SABER.get(), caster.level());
        entity.ownerId = caster.getUUID();
        entity.damage = (float) (PoweredSwordItem.getPanelAttackDamage(stack) * 0.5);
        entity.entityData.set(CASTER, caster.getId());
        entity.entityData.set(COLOR, LightSaberColorData.getBladeColor(stack));
        entity.entityData.set(COUNT, 4 + PoweredToolResearch.lightSaberOrbitLevel(stack));
        entity.entityData.set(START, caster.level().getGameTime());
        entity.setPos(caster.position());
        entity.sourceId = LightSaberOrbitEnergy.get(caster.server).begin(stack, caster, entity.getUUID()).id();
        if (!caster.level().addFreshEntity(entity)) entity.discard();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CASTER, -1);
        builder.define(COLOR, 0x31F7D3);
        builder.define(COUNT, 4);
        builder.define(START, 0L);
    }

    public int getCasterId() {
        return entityData.get(CASTER);
    }

    public int getBladeColor() {
        return entityData.get(COLOR);
    }

    public int getSwordCount() {
        return entityData.get(COUNT);
    }

    public float getOrbitAngle(float partialTick) {
        return ((level().getGameTime() - entityData.get(START)) * 6 + partialTick * 6) % 360;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        ServerPlayer caster = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        LightSaberOrbitEnergy bank = LightSaberOrbitEnergy.get(level.getServer());
        LightSaberOrbitEnergy.Charge charge = sourceId == null ? null : bank.getCharge(sourceId);
        if (caster == null || !caster.isAlive() || caster.isSpectator() || caster.level() != level || charge == null || !charge.active()) {
            discard();
            return;
        }
        entityData.set(CASTER, caster.getId());
        double cost = getSwordCount() * 80.0 / 20.0;
        if (charge.energy() < cost) {
            charge.setEnergy(0);
            discard();
            return;
        }
        charge.setEnergy(charge.energy() - cost);
        setPos(caster.position());
        long now = level.getGameTime();
        lastHits.values().removeIf((long hit) -> now - hit >= 20);
        for (int index = 0; index < getSwordCount(); index++) {
            double angle = Math.toRadians(getOrbitAngle(0) + 360.0 * index / getSwordCount());
            Vec3 blade = position().add(Math.cos(angle) * 2.2, 1.67, -Math.sin(angle) * 2.2);
            // The item model's 0.8 scale and ItemRenderer origin put the visible sword at Y=0.69..2.65.
            AABB bounds = new AABB(blade, blade).inflate(0.45, 0.98, 0.45);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds,
                    target -> target != caster && target.isAlive() && !target.isSpectator())) {
                if (lastHits.containsKey(target.getUUID()) || !caster.hasLineOfSight(target) || target instanceof Player player && !caster.canHarmPlayer(player)) continue;
                lastHits.put(target.getUUID(), now);
                target.hurt(new DamageSource(caster.damageSources().playerAttack(caster).typeHolder(), this, caster), damage);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        boolean unload = reason == RemovalReason.UNLOADED_TO_CHUNK || reason == RemovalReason.UNLOADED_WITH_PLAYER;
        if (!unload && level() instanceof ServerLevel level && sourceId != null) {
            LightSaberOrbitEnergy.get(level.getServer()).close(sourceId, level.getServer().overworld().getGameTime());
            ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
            if (owner != null) owner.getCooldowns().addCooldown(DEItems.DATA_LIGHT_SABER.get(), 900);
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        sourceId = tag.hasUUID("Source") ? tag.getUUID("Source") : null;
        damage = Math.max(0, tag.getFloat("Damage"));
        entityData.set(COLOR, tag.getInt("Color"));
        entityData.set(COUNT, Math.clamp(tag.getInt("Count"), 4, 8));
        entityData.set(START, tag.getLong("Start"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        if (sourceId != null) tag.putUUID("Source", sourceId);
        tag.putFloat("Damage", damage);
        tag.putInt("Color", getBladeColor());
        tag.putInt("Count", getSwordCount());
        tag.putLong("Start", entityData.get(START));
    }
}
