package com.fish_dan_.data_energistics.common.entity.projectile;

import com.fish_dan_.data_energistics.common.data.DEEntities;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.item.powered.LightSaberColorData;
import com.fish_dan_.data_energistics.common.item.powered.PoweredSwordItem;
import com.fish_dan_.data_energistics.common.item.powered.PoweredToolResearch;
import com.fish_dan_.data_energistics.common.item.powered.lightsaber.LightSaberOrbitEnergy;
import com.fish_dan_.data_energistics.common.item.powered.lightsaber.LightSaberOrbitGeometry;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
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

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.UUID;

public final class OrbitingLightSaberEntity extends Entity {

    private static final EntityDataAccessor<Integer> CASTER = SynchedEntityData.defineId(OrbitingLightSaberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(OrbitingLightSaberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COUNT = SynchedEntityData.defineId(OrbitingLightSaberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RINGS = SynchedEntityData.defineId(OrbitingLightSaberEntity.class, EntityDataSerializers.INT);
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
        entity.entityData.set(RINGS, PoweredToolResearch.has(stack, PoweredToolResearch.DUAL_ORBIT) ? 2 : 1);
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
        builder.define(RINGS, 1);
        builder.define(START, 0L);
    }

    public int getBladeColor() {
        return entityData.get(COLOR);
    }

    public int getSwordCount() {
        return entityData.get(COUNT) * getRingCount();
    }

    public int getRingCount() {
        return entityData.get(RINGS);
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
        int perRing = entityData.get(COUNT);
        for (int ring = 0; ring < getRingCount(); ring++) {
            for (int index = 0; index < perRing; index++) {
                double angle = (ring == 0 ? 1 : -1) * getOrbitAngle(0) + 360.0 * index / perRing;
                AABB bounds = LightSaberOrbitGeometry.sweptBounds(position(), angle, ring);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds,
                        target -> target != caster && target.isAlive() && !target.isSpectator())) {
                    if (lastHits.containsKey(target.getUUID()) || !LightSaberOrbitGeometry.intersects(target.getBoundingBox(), position(), angle, ring) || !caster.hasLineOfSight(target) || target instanceof Player player && !caster.canHarmPlayer(player)) continue;
                    lastHits.put(target.getUUID(), now);
                    target.hurt(new DamageSource(caster.damageSources().playerAttack(caster).typeHolder(), this, caster), damage);
                }
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
        OrbitState state = OrbitState.CODEC.parse(registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
        ownerId = state.owner;
        sourceId = state.source;
        damage = state.damage;
        entityData.set(COLOR, state.color);
        entityData.set(COUNT, state.count);
        entityData.set(RINGS, state.rings);
        entityData.set(START, state.start);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        OrbitState state = new OrbitState(ownerId, sourceId, damage, getBladeColor(), entityData.get(COUNT), getRingCount(), entityData.get(START));
        tag.merge((CompoundTag) OrbitState.CODEC.encodeStart(registryAccess().createSerializationContext(NbtOps.INSTANCE), state).getOrThrow());
    }

    private record OrbitState(UUID owner, UUID source, float damage, int color, int count, int rings, long start) {

        private static final Codec<OrbitState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(OrbitState::owner),
                UUIDUtil.CODEC.fieldOf("source").forGetter(OrbitState::source),
                Codec.FLOAT.validate(value -> Float.isFinite(value) && value >= 0 ? DataResult.success(value) : DataResult.error(() -> "Invalid orbit damage"))
                        .fieldOf("damage").forGetter(OrbitState::damage),
                Codec.INT.fieldOf("color").forGetter(OrbitState::color),
                Codec.intRange(4, 8).fieldOf("count_per_ring").forGetter(OrbitState::count),
                Codec.intRange(1, 2).fieldOf("rings").forGetter(OrbitState::rings),
                Codec.LONG.validate(value -> value >= 0 ? DataResult.success(value) : DataResult.error(() -> "Negative orbit start"))
                        .fieldOf("start").forGetter(OrbitState::start))
                .apply(instance, OrbitState::new));
    }
}
