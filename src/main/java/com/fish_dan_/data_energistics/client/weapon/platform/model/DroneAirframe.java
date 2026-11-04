package com.fish_dan_.data_energistics.client.weapon.platform.model;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/** Client geometry and wing-root calibration from the two Blockbench source models. */
@NullMarked
public enum DroneAirframe {

    WHITE("white", List.of(
            wing("white", "front_left", new Pivot(-4, 8.75f, -.1f), new Pivot(-9.5f, 8.7f, -2.1f),
                    new Rotation(-12, -7, -10), true, true),
            wing("white", "front_right", new Pivot(4, 8.75f, -.1f), new Pivot(9.5f, 8.7f, -2.1f),
                    new Rotation(-12, 7, 10), true, false),
            wing("white", "rear_left", new Pivot(-2.9f, 8.75f, 3.6f), new Pivot(-6.8f, 8.7f, 5.8f),
                    new Rotation(12, 9, 14), false, true),
            wing("white", "rear_right", new Pivot(2.9f, 8.75f, 3.6f), new Pivot(6.8f, 8.7f, 5.8f),
                    new Rotation(12, -9, -14), false, false))),
    BLACK("black", List.of(
            wing("black", "front_left", new Pivot(-2.9f, 8.75f, -3.6f), new Pivot(-6.8f, 8.7f, -5.8f),
                    new Rotation(-4, -7, 12), true, true),
            wing("black", "front_right", new Pivot(2.9f, 8.75f, -3.6f), new Pivot(6.8f, 8.7f, -5.8f),
                    new Rotation(-4, 7, -12), true, false),
            wing("black", "rear_left", new Pivot(-3.35f, 8.75f, 1.8f), new Pivot(-9.5f, 8.7f, 2.1f),
                    new Rotation(0, 5, 14), false, true),
            wing("black", "rear_right", new Pivot(3.35f, 8.75f, 1.8f), new Pivot(9.5f, 8.7f, 2.1f),
                    new Rotation(0, -5, -14), false, false)));

    private final ModelResourceLocation body;
    private final ResourceLocation texture;
    private final List<Wing> wings;

    DroneAirframe(String variant, List<Wing> wings) {
        body = model(variant, "body");
        texture = Data_Energistics.id("textures/item/weapon_platform/floating_gun/drones/" + variant + ".png");
        this.wings = wings;
    }

    public static DroneAirframe forForm(GunForm form) {
        return switch (form) {
            case NORMAL, MEDICAL -> WHITE;
            case SNIPER, PLASMA, GRENADE -> BLACK;
        };
    }

    public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (DroneAirframe airframe : values()) {
            event.register(airframe.body);
            for (Wing wing : airframe.wings) {
                event.register(wing.model);
                event.register(wing.rotor);
            }
        }
    }

    public ModelResourceLocation body() {
        return body;
    }

    public ResourceLocation texture() {
        return texture;
    }

    public List<Wing> wings() {
        return wings;
    }

    private static Wing wing(String variant, String section, Pivot hinge, Pivot rotorPivot,
                             Rotation rest, boolean front, boolean left) {
        return new Wing(model(variant, "wing_" + section), model(variant, "rotor_" + section),
                hinge, rotorPivot, rest, front, left);
    }

    private static ModelResourceLocation model(String variant, String part) {
        return ModelResourceLocation.standalone(
                Data_Energistics.id("item/weapon_platform/drones/" + variant + "/" + part));
    }

    /** An unposed wing and its child rotor; pivots use Blockbench model units. */
    public record Wing(ModelResourceLocation model, ModelResourceLocation rotor, Pivot hinge,
                       Pivot rotorPivot, Rotation rest, boolean front, boolean left) {}

    /** Absolute coordinates before the OBJ loader's half-block X/Z centering offset. */
    public record Pivot(float x, float y, float z) {}

    /** Degrees in Blockbench's ZYX Euler order. */
    public record Rotation(float x, float y, float z) {}
}
