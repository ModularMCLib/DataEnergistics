package com.fish_dan_.data_energistics.api.recipe.incineration;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record IncinerationIngredient(Ingredient ingredient, int count) {

    private static final Codec<Integer> COUNT_CODEC = Codec.INT.flatXmap(
            count -> count <= 0 ? DataResult.error(() -> "Incineration ingredient count must be greater than 0") : DataResult.success(count),
            DataResult::success);

    public static final MapCodec<IncinerationIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(IncinerationIngredient::ingredient),
            COUNT_CODEC.optionalFieldOf("count", 1).forGetter(IncinerationIngredient::count)).apply(instance, IncinerationIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, IncinerationIngredient> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC,
            IncinerationIngredient::ingredient,
            ByteBufCodecs.VAR_INT,
            IncinerationIngredient::count,
            IncinerationIngredient::new);

    public IncinerationIngredient {
        if (count <= 0) {
            throw new IllegalArgumentException("Incineration ingredient count must be greater than 0");
        }
    }
}
