package com.fish_dan_.data_energistics.common.item.powered.lightsaber;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.item.powered.PoweredEnergyItem;

import appeng.api.ids.AEComponents;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Keeps active orbit energy authoritative even when its source stack moves into a container or item entity. */
public final class LightSaberOrbitEnergy extends SavedData {

    private static final Factory<LightSaberOrbitEnergy> FACTORY = new Factory<>(LightSaberOrbitEnergy::new, LightSaberOrbitEnergy::load);
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
        ListTag list = new ListTag();
        for (Charge charge : charges.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Source", charge.id);
            entry.putUUID("Owner", charge.owner);
            entry.putUUID("Entity", charge.entity);
            entry.putDouble("Energy", charge.energy);
            entry.putBoolean("Active", charge.active);
            entry.putLong("Cooldown", charge.cooldownEnd);
            list.add(entry);
        }
        tag.put("Charges", list);
        return tag;
    }

    private static LightSaberOrbitEnergy load(CompoundTag tag, HolderLookup.Provider registries) {
        LightSaberOrbitEnergy bank = new LightSaberOrbitEnergy();
        ListTag list = tag.getList("Charges", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (!entry.hasUUID("Source") || !entry.hasUUID("Owner") || !entry.hasUUID("Entity")) continue;
            double energy = entry.getDouble("Energy");
            if (!Double.isFinite(energy) || energy < 0) continue;
            UUID id = entry.getUUID("Source");
            bank.charges.put(id, bank.new Charge(id, entry.getUUID("Owner"), entry.getUUID("Entity"),
                    energy, entry.getBoolean("Active"), Math.max(0, entry.getLong("Cooldown"))));
        }
        return bank;
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
