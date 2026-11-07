package com.fish_dan_.data_energistics.common.weapon.platform.ability;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.EquippedWeaponPlatform;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import org.jspecify.annotations.Nullable;

/** Equipped flight research removes mining penalties independently of powered abilities. */
public final class PlatformFlightMining {

    @SubscribeEvent
    public void tick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var platform = EquippedWeaponPlatform.find(player);
        player.getData(DEAttachments.PLATFORM_MOTION).miningPenaltyImmunity = !platform.isEmpty() && ItemResearchUpgrades.of(new WeaponPlatformInventory(platform).getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids().contains(PlatformAbility.FLIGHT.research);
    }

    public static boolean immune(Player player) {
        return player.hasData(DEAttachments.PLATFORM_MOTION) && player.getData(DEAttachments.PLATFORM_MOTION).miningPenaltyImmunity;
    }

    /** Replays vanilla attribute arithmetic while excluding only negative modifiers, without mutating the attribute. */
    public static double withoutNegativeModifiers(@Nullable AttributeInstance attribute, double unmodified) {
        // A supplier-only attribute has no instance and therefore has no modifiers to exclude.
        if (attribute == null) return unmodified;
        var modifiers = attribute.getModifiers();
        double base = attribute.getBaseValue();
        for (AttributeModifier modifier : modifiers) {
            if (modifier.amount() > 0 && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) base += modifier.amount();
        }
        double value = base;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.amount() > 0 && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) value += base * modifier.amount();
        }
        for (AttributeModifier modifier : modifiers) {
            if (modifier.amount() > 0 && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) value *= 1 + modifier.amount();
        }
        return attribute.getAttribute().value().sanitizeValue(value);
    }
}
