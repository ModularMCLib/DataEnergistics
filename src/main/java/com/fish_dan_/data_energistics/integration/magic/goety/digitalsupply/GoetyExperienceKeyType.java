package com.fish_dan_.data_energistics.integration.magic.goety.digitalsupply;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.MapCodec;

/** AE key type for Goety ritual experience levels. One AE unit is exactly one level. */
public final class GoetyExperienceKeyType extends AEKeyType {

    public static final ResourceLocation ID = Data_Energistics.id("goety_experience_levels");
    public static final GoetyExperienceKeyType INSTANCE = new GoetyExperienceKeyType();

    private GoetyExperienceKeyType() {
        super(ID, GoetyExperienceKey.class, Component.translatable("key_type." + Data_Energistics.MODID + ".goety_experience_levels"));
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return GoetyExperienceKey.MAP_CODEC;
    }

    @Override
    public AEKey readFromPacket(RegistryFriendlyByteBuf buffer) {
        return GoetyExperienceKey.INSTANCE;
    }

    @Override
    public int getAmountPerByte() {
        return 8;
    }
}
