package com.fish_dan_.data_energistics.integration.magic.goety.digitalsupply;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.MapCodec;

/** AE key type for the integer soul amount exposed by Goety's public soul API. */
public final class GoetySoulKeyType extends AEKeyType {

    public static final ResourceLocation ID = Data_Energistics.id("goety_souls");
    public static final GoetySoulKeyType INSTANCE = new GoetySoulKeyType();

    private GoetySoulKeyType() {
        super(ID, GoetySoulKey.class, Component.translatable("key_type." + Data_Energistics.MODID + ".goety_souls"));
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return GoetySoulKey.MAP_CODEC;
    }

    @Override
    public AEKey readFromPacket(RegistryFriendlyByteBuf buffer) {
        return GoetySoulKey.INSTANCE;
    }

    @Override
    public int getAmountPerByte() {
        return 8;
    }
}
