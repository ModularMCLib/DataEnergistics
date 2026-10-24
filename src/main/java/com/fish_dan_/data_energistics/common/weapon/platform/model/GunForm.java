package com.fish_dan_.data_energistics.common.weapon.platform.model;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public enum GunForm {

    NORMAL("normal", 6, 10, 240, 10),
    SNIPER("sniper", 24, 40, 1600, 30),
    PLASMA("plasma", 8, 20, 800, 18),
    GRENADE("grenade", 14, 30, 1200, 25),
    MEDICAL("medical", 0, 10, 240, 10);

    public final String id;
    public final float damage;
    public final int interval;
    public final int energy;
    public final int heat;
    public final ResourceLocation researchId;

    GunForm(String id, float damage, int interval, int energy, int heat) {
        this.id = id;
        this.damage = damage;
        this.interval = interval;
        this.energy = energy;
        this.heat = heat;
        this.researchId = Data_Energistics.id("floating_gun/form/" + id);
    }

    public static GunForm of(ItemStack stack) {
        var ids = ItemResearchUpgrades.of(stack).ids();
        GunForm result = NORMAL;
        for (GunForm form : values()) {
            if (form == NORMAL || !ids.contains(form.researchId)) continue;
            if (result != NORMAL) throw new IllegalArgumentException("Conflicting floating gun forms");
            result = form;
        }
        return result;
    }
}
