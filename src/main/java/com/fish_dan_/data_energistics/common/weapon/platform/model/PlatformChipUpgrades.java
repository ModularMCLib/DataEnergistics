package com.fish_dan_.data_energistics.common.weapon.platform.model;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class PlatformChipUpgrades {

    public static final ResourceLocation ROOT = Data_Energistics.id("biochip/specialized/weapon_platform/root");
    public static final ResourceLocation CAPACITY_I = Data_Energistics.id("biochip/specialized/weapon_platform/capacity_1");
    public static final ResourceLocation CAPACITY_II = Data_Energistics.id("biochip/specialized/weapon_platform/capacity_2");
    public static final ResourceLocation COOLING_I = Data_Energistics.id("biochip/specialized/weapon_platform/cooling_1");
    public static final ResourceLocation COOLING_II = Data_Energistics.id("biochip/specialized/weapon_platform/cooling_2");
    public static final ResourceLocation HUD = Data_Energistics.id("biochip/specialized/weapon_platform/tactical_hud");
    public static final ResourceLocation REBOOT = Data_Energistics.id("biochip/specialized/weapon_platform/radix_reboot");
    public static final ResourceLocation PLATFORM = Data_Energistics.id("biochip/specialized/weapon_platform/floating_gun_platform");
    public static final ResourceLocation EFFECT_SUPPRESSION = Data_Energistics.id("biochip/specialized/weapon_platform/effect_suppression");

    private PlatformChipUpgrades() {}

    public static int capacity(ItemStack chip) {
        var ids = ItemResearchUpgrades.of(chip).ids();
        return 2 + (ids.contains(CAPACITY_I) ? 2 : 0) + (ids.contains(CAPACITY_II) ? 2 : 0) + (ids.contains(PLATFORM) ? 2 : 0);
    }

    public static double cooling(ItemStack chip) {
        var ids = ItemResearchUpgrades.of(chip).ids();
        return 0.5 * (1 + (ids.contains(COOLING_I) ? 0.2 : 0) + (ids.contains(COOLING_II) ? 0.2 : 0));
    }
}
