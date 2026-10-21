package com.fish_dan_.data_energistics.common.item.powered;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.common.item.trident.DataTridentItem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.NullMarked;

/** Stack-bound upgrades for tools whose former card slots have moved to the modification station. */
@NullMarked
public final class PoweredToolResearch {

    public static final String ENERGY = "energy";
    public static final String SPEED = "speed";
    public static final String DAMAGE = "damage";
    public static final String STORAGE = "data_flow";
    public static final String MINING_SPEED = "mining_speed";
    public static final String EMITTER = "light_blade";
    public static final String ORBIT = "orbit";
    public static final ResourceLocation CRYSTAL_SWORD = id("data_crystal_sword", "digitization");
    public static final ResourceLocation CRYSTAL_AXE = id("data_crystal_axe", "chain_mining");
    public static final ResourceLocation CRYSTAL_PICKAXE = id("data_crystal_pickaxe", "ore_duplication");
    public static final ResourceLocation CRYSTAL_HOE = id("data_crystal_hoe", "persistent_farmland");
    public static final ResourceLocation CRYSTAL_SHOVEL = id("data_crystal_shovel", "area_mining");
    public static final ResourceLocation CRYSTAL_CUTTING_KNIFE = id("data_crystal_cutting_knife", "teleport_range");
    public static final ResourceLocation TRIDENT_FIELD = id("data_trident", "mob_field");

    private PoweredToolResearch() {}

    public static boolean supports(ItemStack stack) {
        return stack.getItem() instanceof AbstractPoweredTieredItem || stack.getItem() instanceof PoweredCuttingKnifeItem || stack.getItem() instanceof DataTridentItem;
    }

    public static boolean has(ItemStack stack, ResourceLocation upgrade) {
        return ResearchAccess.hasUpgrade(stack, upgrade);
    }

    public static boolean has(ItemStack stack, String capability) {
        return has(stack, id(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), capability));
    }

    public static int level(ItemStack stack, String capability, int maximum) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        for (int level = maximum; level > 0; level--) {
            if (has(stack, id(itemId, capability + "/" + level))) return level;
        }
        return 0;
    }

    public static int lightSaberOrbitLevel(ItemStack stack) {
        return level(stack, ORBIT, 4);
    }

    public static ResourceLocation id(String itemId, String capability) {
        return Data_Energistics.id("weapon/" + itemId + "/" + capability);
    }
}
