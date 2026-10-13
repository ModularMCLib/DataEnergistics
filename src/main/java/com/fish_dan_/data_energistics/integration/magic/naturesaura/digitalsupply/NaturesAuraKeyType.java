package com.fish_dan_.data_energistics.integration.magic.naturesaura.digitalsupply;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

/** AE2 key type for the single network-wide Nature's Aura identity. */
public final class NaturesAuraKeyType extends AEKeyType {

    static final String RESOURCE_FIELD = "resource";
    public static final NaturesAuraKeyType TYPE = new NaturesAuraKeyType();
    private static final MapCodec<NaturesAuraKey> CODEC = ResourceLocation.CODEC
            .fieldOf(RESOURCE_FIELD)
            .flatXmap(id -> NaturesAuraKey.ID.equals(id) ? DataResult.success(NaturesAuraKey.INSTANCE) : DataResult.error(() -> "Unknown Nature's Aura key: " + id),
                    key -> DataResult.success(NaturesAuraKey.ID));

    private NaturesAuraKeyType() {
        super(Data_Energistics.id("natures_aura"), NaturesAuraKey.class,
                Component.translatable("key_type." + Data_Energistics.MODID + ".natures_aura"));
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return CODEC;
    }

    @Override
    public @Nullable AEKey readFromPacket(RegistryFriendlyByteBuf buffer) {
        return NaturesAuraKey.ID.equals(buffer.readResourceLocation()) ? NaturesAuraKey.INSTANCE : null;
    }

    @Override
    public int getAmountPerByte() {
        return 8;
    }
}
