package com.fish_dan_.data_energistics.common.weapon.platform.equipment;

import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.integration.MOD;
import com.fish_dan_.data_energistics.integration.library.curios.equipment.CuriosWeaponPlatformAccess;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class EquippedWeaponPlatform {

    private EquippedWeaponPlatform() {}

    public static ItemStack find(Player player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.is(DEItems.DIGITAL_WEAPON_PLATFORM)) return helmet;
        return MOD.CURIOS.isLoaded() ? CuriosWeaponPlatformAccess.find(player) : ItemStack.EMPTY;
    }
}
