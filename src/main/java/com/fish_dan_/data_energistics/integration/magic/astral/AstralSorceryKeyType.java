package com.fish_dan_.data_energistics.integration.magic.astral;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

/** AE2 key type for registry-backed Astral Sorcery lumen and constellations. */
public final class AstralSorceryKeyType extends AEKeyType {

    public static final AstralSorceryKeyType TYPE = new AstralSorceryKeyType();
    private static final MapCodec<AstralSorceryKey> CODEC = ResourceLocation.CODEC
            .fieldOf("resource")
            .flatXmap(id -> AstralSorceryKey.resolve(id)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown Astral Sorcery resource " + id)),
                    key -> DataResult.success(key.getResourceId()));

    private AstralSorceryKeyType() {
        super(Data_Energistics.id("astral_sorcery"), AstralSorceryKey.class,
                Component.translatable("key_type." + Data_Energistics.MODID + ".astral_sorcery"));
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return CODEC;
    }

    @Override
    public AEKey readFromPacket(RegistryFriendlyByteBuf buffer) {
        ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buffer);
        return AstralSorceryKey.resolve(id).orElse(null);
    }

    @Override
    public int getAmountPerByte() {
        return 8;
    }

    @Override
    public int getAmountPerOperation() {
        return 1;
    }
}
