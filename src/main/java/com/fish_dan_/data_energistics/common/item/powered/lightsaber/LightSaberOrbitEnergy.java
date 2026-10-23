package com.fish_dan_.data_energistics.common.item.powered.lightsaber;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.item.powered.PoweredEnergyItem;

import appeng.api.ids.AEComponents;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Keeps active orbit energy authoritative even when its source stack moves into a container or item entity. */
public final class LightSaberOrbitEnergy extends SavedData {

    private static final Factory<LightSaberOrbitEnergy> FACTORY = new Factory<>(LightSaberOrbitEnergy::new, LightSaberOrbitEnergy::load);
    private static final Codec<Object2ObjectMap<UUID, ChargeSnapshot>> CHARGES_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, ChargeSnapshot.CODEC)
            .xmap(map -> (Object2ObjectMap<UUID, ChargeSnapshot>) new Object2ObjectOpenHashMap<>(map), map -> map)
            .fieldOf("charges").codec();
    private final Object2ObjectMap<UUID, Charge> charges = new Object2ObjectOpenHashMap<>();

    public static LightSaberOrbitEnergy get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "data_energistics_light_saber_orbits");
    }

    public static @Nullable Charge find(ItemStack stack) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        UUID id = stack.get(DEDataComponents.LIGHT_SABER_INSTANCE.get());
        if (server == null || !server.isSameThread() || id == null) return null;
        LightSaberOrbitEnergy bank = get(server);
        Charge charge = bank.charges.get(id);
        if (charge != null && !charge.active) {
            stack.set(AEComponents.STORED_ENERGY, charge.energy);
            stack.set(DEDataComponents.LIGHT_SABER_COOLDOWN_END.get(), charge.cooldownEnd);
            bank.charges.remove(id);
            bank.setDirty();
            return null;
        }
        return charge;
    }

    public Charge begin(ItemStack stack, ServerPlayer owner, UUID entityId) {
        UUID id = stack.get(DEDataComponents.LIGHT_SABER_INSTANCE.get());
        if (id == null) {
            id = UUID.randomUUID();
            stack.set(DEDataComponents.LIGHT_SABER_INSTANCE.get(), id);
        }
        double energy = ((PoweredEnergyItem) stack.getItem()).getAECurrentPower(stack);
        Charge charge = new Charge(id, owner.getUUID(), entityId, energy, true, 0);
        charges.put(id, charge);
        setDirty();
        return charge;
    }

    public @Nullable Charge getCharge(UUID sourceId) {
        return charges.get(sourceId);
    }

    public @Nullable Charge findOwner(UUID owner) {
        for (Charge charge : charges.values()) {
            if (charge.active && charge.owner.equals(owner)) return charge;
        }
        return null;
    }

    public void close(UUID id, long gameTime) {
        Charge charge = charges.get(id);
        if (charge == null || !charge.active) return;
        charge.active = false;
        charge.cooldownEnd = gameTime + 900;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Object2ObjectMap<UUID, ChargeSnapshot> snapshots = new Object2ObjectOpenHashMap<>();
        charges.forEach((id, charge) -> snapshots.put(id, new ChargeSnapshot(charge.owner, charge.entity, charge.energy, charge.active, charge.cooldownEnd)));
        tag.merge((CompoundTag) CHARGES_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), snapshots).getOrThrow());
        return tag;
    }

    private static LightSaberOrbitEnergy load(CompoundTag tag, HolderLookup.Provider registries) {
        LightSaberOrbitEnergy bank = new LightSaberOrbitEnergy();
        CHARGES_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow().forEach((id, snapshot) -> bank.charges.put(id, bank.new Charge(id, snapshot.owner, snapshot.entity, snapshot.energy, snapshot.active, snapshot.cooldownEnd)));
        return bank;
    }

    private record ChargeSnapshot(UUID owner, UUID entity, double energy, boolean active, long cooldownEnd) {

        private static final Codec<ChargeSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(ChargeSnapshot::owner),
                UUIDUtil.CODEC.fieldOf("entity").forGetter(ChargeSnapshot::entity),
                Codec.DOUBLE.validate(value -> Double.isFinite(value) && value >= 0 ? DataResult.success(value) : DataResult.error(() -> "Invalid orbit energy"))
                        .fieldOf("energy").forGetter(ChargeSnapshot::energy),
                Codec.BOOL.fieldOf("active").forGetter(ChargeSnapshot::active),
                Codec.LONG.validate(value -> value >= 0 ? DataResult.success(value) : DataResult.error(() -> "Negative orbit cooldown"))
                        .fieldOf("cooldown_end").forGetter(ChargeSnapshot::cooldownEnd))
                .apply(instance, ChargeSnapshot::new));
    }

    public final class Charge {

        private final UUID id;
        private final UUID owner;
        private final UUID entity;
        private double energy;
        private boolean active;
        private long cooldownEnd;

        private Charge(UUID id, UUID owner, UUID entity, double energy, boolean active, long cooldownEnd) {
            this.id = id;
            this.owner = owner;
            this.entity = entity;
            this.energy = energy;
            this.active = active;
            this.cooldownEnd = cooldownEnd;
        }

        public UUID id() {
            return id;
        }

        public UUID entity() {
            return entity;
        }

        public UUID owner() {
            return owner;
        }

        public double energy() {
            return energy;
        }

        public boolean active() {
            return active;
        }

        public void setEnergy(double energy) {
            this.energy = energy;
            setDirty();
        }
    }
}
