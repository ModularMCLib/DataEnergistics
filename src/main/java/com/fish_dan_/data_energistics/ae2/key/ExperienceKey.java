package com.fish_dan_.data_energistics.ae2.key;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Shared AE identity for experience-like resources exposed by optional integrations. */
public final class ExperienceKey extends DigitalBiologicalResourceKey {

    public static final ResourceLocation ID = Data_Energistics.id("experience");
    public static final ExperienceKey INSTANCE = new ExperienceKey();

    private ExperienceKey() {}

    public static ExperienceKey of() {
        return INSTANCE;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Component computeDisplayName() {
        if (Data_Energistics.isModLoaded("forbidden_arcanus")) {
            return Component.translatable("essence.forbidden_arcanus.experience");
        }
        if (Data_Energistics.isModLoaded("neovitae")) {
            return Component.translatable("fluid.neovitae.liquified_experience");
        }
        if (Data_Energistics.isModLoaded("goety")) {
            return Component.translatable("info.goety.ritual.noXP.fail");
        }
        return Component.translatable("key.data_energistics.experience");
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ExperienceKey;
    }

    @Override
    public int hashCode() {
        return ID.hashCode();
    }

    @Override
    public String toString() {
        return "ExperienceKey{}";
    }
}
