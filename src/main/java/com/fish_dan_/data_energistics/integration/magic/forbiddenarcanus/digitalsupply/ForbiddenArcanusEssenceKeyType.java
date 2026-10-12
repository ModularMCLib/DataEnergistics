package com.fish_dan_.data_energistics.integration.magic.forbiddenarcanus.digitalsupply;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

/** AE2 key type for the stable Forbidden Arcanus essence identities. */
public final class ForbiddenArcanusEssenceKeyType extends AEKeyType {

    static final String RESOURCE_FIELD = "resource";
    public static final ForbiddenArcanusEssenceKeyType TYPE = new ForbiddenArcanusEssenceKeyType();
    private static final MapCodec<ForbiddenArcanusEssenceKey> CODEC = ResourceLocation.CODEC
            .fieldOf(RESOURCE_FIELD)
            .flatXmap(id -> ForbiddenArcanusEssenceKey.resolve(id)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown Forbidden Arcanus essence: " + id)),
                    key -> DataResult.success(key.getId()));

    private ForbiddenArcanusEssenceKeyType() {
        super(Data_Energistics.id("forbidden_arcanus_essence"), ForbiddenArcanusEssenceKey.class,
                Component.translatable("key_type." + Data_Energistics.MODID + ".forbidden_arcanus_essence"));
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return CODEC;
    }

    @Override
    public AEKey readFromPacket(RegistryFriendlyByteBuf buffer) {
        ResourceLocation id = buffer.readResourceLocation();
        return ForbiddenArcanusEssenceKey.resolve(id).orElse(null);
    }

    @Override
    public int getAmountPerByte() {
        return 8;
    }
}
