package com.fish_dan_.data_energistics.common.anvil.payment;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

/** Server-thread payment journal. Failed extraction refunds survive chunk unloads and network outages. */
public final class DigitalAnvilPayment {

    private static final double ENERGY_TOLERANCE = 1.0e-7;
    private final Object2LongMap<AEKey> refunds = new Object2LongLinkedOpenHashMap<>();
    private double energyRefund;

    public boolean available(IGrid grid, IActionSource source, DigitalAnvilCost cost) {
        if (pending()) return false;
        for (var entry : cost.resources().object2LongEntrySet()) {
            if (grid.getStorageService().getInventory().extract(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE, source) != entry.getLongValue()) return false;
        }
        return grid.getEnergyService().extractAEPower(cost.energy(), Actionable.SIMULATE, PowerMultiplier.ONE) + ENERGY_TOLERANCE >= cost.energy();
    }

    public boolean pay(IGrid grid, IActionSource source, DigitalAnvilCost cost, Runnable changed) {
        refund(grid, source, changed);
        if (!available(grid, source, cost)) return false;
        try {
            for (var entry : cost.resources().object2LongEntrySet()) {
                long extracted = grid.getStorageService().getInventory().extract(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, source);
                if (extracted < 0 || extracted > entry.getLongValue()) throw new IllegalStateException("Invalid anvil resource extraction for " + entry.getKey() + ": " + extracted);
                if (extracted > 0) {
                    refunds.put(entry.getKey(), extracted);
                    changed.run();
                }
                if (extracted != entry.getLongValue()) return false;
            }
            energyRefund = grid.getEnergyService().extractAEPower(cost.energy(), Actionable.MODULATE, PowerMultiplier.ONE);
            changed.run();
            if (!Double.isFinite(energyRefund) || energyRefund < 0 || energyRefund > cost.energy() + ENERGY_TOLERANCE) {
                throw new IllegalStateException("Invalid anvil AE energy extraction: " + energyRefund);
            }
            if (energyRefund + ENERGY_TOLERANCE < cost.energy()) return false;
            refunds.clear();
            energyRefund = 0;
            changed.run();
            return true;
        } finally {
            if (pending()) refund(grid, source, changed);
        }
    }

    public boolean pending() {
        return !refunds.isEmpty() || energyRefund > ENERGY_TOLERANCE;
    }

    public void refund(IGrid grid, IActionSource source, Runnable changed) {
        var iterator = refunds.object2LongEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long accepted = grid.getStorageService().getInventory().insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, source);
            if (accepted < 0 || accepted > entry.getLongValue()) throw new IllegalStateException("Invalid anvil refund for " + entry.getKey() + ": " + accepted);
            if (accepted == 0) continue;
            if (accepted == entry.getLongValue()) iterator.remove();
            else entry.setValue(entry.getLongValue() - accepted);
            changed.run();
        }
        if (energyRefund > ENERGY_TOLERANCE) {
            double overflow = grid.getEnergyService().injectPower(energyRefund, Actionable.MODULATE);
            if (!Double.isFinite(overflow) || overflow < 0 || overflow > energyRefund + ENERGY_TOLERANCE) throw new IllegalStateException("Invalid anvil AE refund: " + overflow);
            energyRefund = overflow;
            changed.run();
        }
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : refunds.object2LongEntrySet()) {
            CompoundTag refund = new CompoundTag();
            refund.put("key", entry.getKey().toTagGeneric(registries));
            refund.putLong("amount", entry.getLongValue());
            list.add(refund);
        }
        tag.put("anvil_refunds", list);
        tag.putDouble("anvil_energy_refund", energyRefund);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        refunds.clear();
        for (Tag value : tag.getList("anvil_refunds", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            AEKey key = AEKey.fromTagGeneric(registries, entry.getCompound("key"));
            long amount = entry.getLong("amount");
            if (key == null || amount <= 0) throw new IllegalArgumentException("Invalid persisted digital anvil refund");
            refunds.mergeLong(key, amount, Math::addExact);
        }
        energyRefund = tag.getDouble("anvil_energy_refund");
        if (!Double.isFinite(energyRefund) || energyRefund < 0) throw new IllegalArgumentException("Invalid persisted digital anvil AE refund");
    }

    /** Resource refunds are dropped as wrapped AE stacks if the block is broken before reinsertion succeeds. */
    public Object2LongMap<AEKey> resourceRefunds() {
        return refunds;
    }
}
