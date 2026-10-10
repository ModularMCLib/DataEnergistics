package com.fish_dan_.data_energistics.ae2.key;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Shared AE identity for blood-like resources exposed by optional integrations. */
public final class BloodKey extends DigitalBiologicalResourceKey {

    public static final ResourceLocation ID = Data_Energistics.id("blood");
    public static final BloodKey INSTANCE = new BloodKey();

    private BloodKey() {}

    public static BloodKey of() {
        return INSTANCE;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Component computeDisplayName() {
        return Component.translatable("key.data_energistics.blood");
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof BloodKey;
    }

    @Override
    public int hashCode() {
        return ID.hashCode();
    }

    @Override
    public String toString() {
        return "BloodKey{}";
    }
}
