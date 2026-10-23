package com.fish_dan_.data_energistics.common.weapon.platform.network;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.ShotRefund;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;

import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * One server-thread shot transaction; rejected transfers remain on the platform until their original network accepts
 * them.
 */
public final class NetworkShotPayment {

    private NetworkShotPayment() {}

    public static @Nullable GunAmmunition pay(ItemStack platform, TerminalConnection connection, int energyCost,
                                              GunAmmunition selected, boolean medical, Consumer<GunAmmunition> fire) {
        if (!refund(platform, connection)) return null;
        var energy = connection.grid().getEnergyService();
        if (energy.extractAEPower(energyCost, Actionable.SIMULATE, PowerMultiplier.ONE) + 1e-7 < energyCost) return null;
        var storage = connection.storage();
        GunAmmunition actual = selected;
        if (selected.key != null && storage.extract(selected.key, selected.amount, Actionable.SIMULATE, connection.source()) != selected.amount) {
            if (medical) return null;
            actual = GunAmmunition.AE;
        }
        boolean committed = false;
        try {
            double takenEnergy = energy.extractAEPower(energyCost, Actionable.MODULATE, PowerMultiplier.ONE);
            if (!Double.isFinite(takenEnergy) || takenEnergy < 0 || takenEnergy > energyCost + 1e-7)
                throw new IllegalStateException("Invalid platform energy extraction: " + takenEnergy);
            platform.set(DEDataComponents.PLATFORM_REFUNDS, new ShotRefund(connection.frequency(), actual, takenEnergy, 0));
            if (takenEnergy + 1e-7 < energyCost) return null;
            if (actual.key != null) {
                long amount = storage.extract(actual.key, actual.amount, Actionable.MODULATE, connection.source());
                if (amount < 0 || amount > actual.amount) throw new IllegalStateException("Invalid platform resource extraction: " + amount);
                platform.set(DEDataComponents.PLATFORM_REFUNDS, new ShotRefund(connection.frequency(), actual, takenEnergy, amount));
                if (amount != actual.amount) return null;
            }
            fire.accept(actual);
            platform.remove(DEDataComponents.PLATFORM_REFUNDS);
            committed = true;
            return actual;
        } finally {
            if (!committed) refund(platform, connection);
        }
    }

    public static boolean refund(ItemStack platform, TerminalConnection connection) {
        ShotRefund persisted = platform.get(DEDataComponents.PLATFORM_REFUNDS);
        if (persisted == null) return true;
        if (persisted.frequency() != connection.frequency()) return false;
        double energy = persisted.energy();
        long amount = persisted.amount();
        GunAmmunition ammo = persisted.ammunition();
        if (amount > 0) {
            long accepted = connection.storage().insert(ammo.key, amount, Actionable.MODULATE, connection.source());
            if (accepted < 0 || accepted > amount) throw new IllegalStateException("Invalid platform resource refund");
            amount -= accepted;
            platform.set(DEDataComponents.PLATFORM_REFUNDS, new ShotRefund(persisted.frequency(), ammo, energy, amount));
        }
        if (energy > 0) {
            energy = connection.grid().getEnergyService().injectPower(energy, Actionable.MODULATE);
            if (!Double.isFinite(energy) || energy < 0 || energy > persisted.energy())
                throw new IllegalStateException("Invalid platform energy refund");
        }
        if (amount == 0 && energy < 1e-7) platform.remove(DEDataComponents.PLATFORM_REFUNDS);
        else platform.set(DEDataComponents.PLATFORM_REFUNDS, new ShotRefund(persisted.frequency(), ammo, energy, amount));
        return !platform.has(DEDataComponents.PLATFORM_REFUNDS);
    }
}
