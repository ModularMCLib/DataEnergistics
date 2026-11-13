package com.fish_dan_.data_energistics.client.weapon.platform.model;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.weapon.platform.model.DroneAirframe.Pivot;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

import lombok.Getter;
import org.jspecify.annotations.NullMarked;

/** Interchangeable belly modules, each with its own OBJ parts and texture atlas. */
@NullMarked
@Getter
public enum DroneWeapon {

    NORMAL("normal", false),
    SNIPER("sniper", true),
    PLASMA("plasma", true),
    GRENADE("grenade", true),
    MEDICAL("medical", true);

    private final ModelResourceLocation mount;
    private final ModelResourceLocation barrel;
    private final ResourceLocation texture;
    private final boolean articulated;
    private final Pivot pivot = new Pivot(0, 6.35f, -4.55f);

    DroneWeapon(String variant, boolean articulated) {
        mount = model(variant, "mount");
        barrel = model(variant, "barrel");
        texture = Data_Energistics.id("textures/item/weapon_platform/floating_gun/drones/weapons/" + variant + ".png");
        this.articulated = articulated;
    }

    public static DroneWeapon forForm(GunForm form) {
        return switch (form) {
            case NORMAL -> NORMAL;
            case SNIPER -> SNIPER;
            case PLASMA -> PLASMA;
            case GRENADE -> GRENADE;
            case MEDICAL -> MEDICAL;
        };
    }

    public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (DroneWeapon weapon : values()) {
            event.register(weapon.mount);
            event.register(weapon.barrel);
        }
    }

    private static ModelResourceLocation model(String variant, String part) {
        return ModelResourceLocation.standalone(
                Data_Energistics.id("item/weapon_platform/drones/weapons/" + variant + "/" + part));
    }
}
