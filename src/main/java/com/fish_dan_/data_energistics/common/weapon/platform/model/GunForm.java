package com.fish_dan_.data_energistics.common.weapon.platform.model;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public enum GunForm {

    NORMAL("normal", 10, 240, 10),
    SNIPER("sniper", 40, 1600, 30),
    PLASMA("plasma", 20, 800, 18),
    GRENADE("grenade", 30, 480, 15),
    MEDICAL("medical", 10, 240, 13);

    public final String id;
    public final int interval;
    public final int energy;
    public final int heat;
    public final ResourceLocation researchId;

    GunForm(String id, int interval, int energy, int heat) {
        this.id = id;
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
