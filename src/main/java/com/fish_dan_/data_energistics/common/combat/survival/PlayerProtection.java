package com.fish_dan_.data_energistics.common.combat.survival;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.EquippedWeaponPlatform;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformChipUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldState;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformAbilitySettings;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/** Equipment and research resolved once for one server-thread health transaction. */
public record PlayerProtection(ItemStack platform, @Nullable ShieldUpgrades shield, boolean reboot) {

    public static final PlayerProtection EMPTY = new PlayerProtection(ItemStack.EMPTY, null, false);

    public static PlayerProtection capture(ServerPlayer player) {
        // LivingEntity initializes health before Player has constructed its inventory.
        if (!player.getData(DEAttachments.SHIELD_HEALTH_WRITES).initialized) return EMPTY;
        ItemStack platform = EquippedWeaponPlatform.find(player);
        if (platform.isEmpty()) return EMPTY;
        var research = ItemResearchUpgrades.of(new WeaponPlatformInventory(platform).getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids();
        var settings = platform.getOrDefault(DEDataComponents.PLATFORM_ABILITIES, PlatformAbilitySettings.DEFAULT);
        ShieldUpgrades shield = settings.enabled(PlatformAbility.SHIELD) && research.contains(PlatformAbility.SHIELD.research) ? ShieldUpgrades.of(research) : null;
        if (shield != null) {
            ShieldState previous = platform.getOrDefault(DEDataComponents.PLATFORM_SHIELD, ShieldState.EMPTY);
            ShieldState normalized = new ShieldState(Math.min(previous.charge(), shield.capacity()), shield.overloadTicks() == 0 ? 0 : previous.overloadedUntil(), true);
            if (!normalized.equals(previous)) platform.set(DEDataComponents.PLATFORM_SHIELD, normalized);
        }
        return new PlayerProtection(platform, shield, research.contains(PlatformChipUpgrades.REBOOT));
    }
}
