package com.fish_dan_.data_energistics.common.weapon.platform.model;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectList;

public enum PlatformAbility {

    SHIELD("shield", 0),
    NIGHT_VISION("night_vision", 32),
    FLIGHT("flight", 256),
    MOVEMENT_PROTECTION("movement_protection", 512),
    NOCLIP("noclip", 1024),
    PROSPECTING("prospecting", 256),
    DOMAIN("domain", 64800),
    SATIETY("satiety", 32),
    ARMOR("armor", 32),
    SUFFOCATION_PROTECTION("suffocation_protection", 32),
    KEEP_INVENTORY("keep_inventory", 0),
    THERMAL_IMAGING("thermal_imaging", 32),
    SONAR("sonar", 32),
    LOW_LIGHT_VISION("low_light_vision", 32);

    public static final Codec<Integer> FLAGS_CODEC = Codec.intRange(0, (1 << values().length) - 1);

    public final String id;
    public final ResourceLocation research;
    public final int energy;
    public final int flag = 1 << ordinal();

    PlatformAbility(String id, int energy) {
        this.id = id;
        this.energy = energy;
        this.research = Data_Energistics.id("biochip/specialized/weapon_platform/" + id);
    }

    /** Checks this chip's upgrades, including the three prerequisites required by existing night-vision records. */
    public boolean available(ObjectList<ResourceLocation> upgrades) {
        return upgrades.contains(research) && (this != NIGHT_VISION || upgrades.contains(THERMAL_IMAGING.research) && upgrades.contains(SONAR.research) && upgrades.contains(LOW_LIGHT_VISION.research));
    }
}
