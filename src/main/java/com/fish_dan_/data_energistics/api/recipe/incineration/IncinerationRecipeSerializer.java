package com.fish_dan_.data_energistics.api.recipe.incineration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

public final class IncinerationRecipeSerializer implements RecipeSerializer<IncinerationRecipe> {

    private static final Codec<Block> BLOCK_CODEC = ResourceLocation.CODEC.flatXmap(
            id -> BuiltInRegistries.BLOCK.getOptional(id)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown block: " + id)),
            block -> DataResult.success(BuiltInRegistries.BLOCK.getKey(block)));
    private static final Codec<List<IncinerationIngredient>> INGREDIENTS_CODEC = IncinerationIngredient.CODEC.codec().listOf()
            .flatXmap(
                    ingredients -> ingredients.isEmpty() ? DataResult.error(() -> "Incineration recipe must have at least one ingredient") : DataResult.success(ingredients),
                    DataResult::success);
    private static final Codec<List<ItemStack>> RESULTS_CODEC = ItemStack.CODEC.listOf()
            .flatXmap(
                    results -> results.isEmpty() ? DataResult.error(() -> "Incineration recipe must have at least one result") : DataResult.success(results),
                    DataResult::success);
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Block>> OPTIONAL_BLOCK_STREAM_CODEC = ByteBufCodecs.optional(
            ResourceLocation.STREAM_CODEC.map(BuiltInRegistries.BLOCK::get, BuiltInRegistries.BLOCK::getKey));

    private static final MapCodec<IncinerationRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BLOCK_CODEC.fieldOf("block").forGetter(IncinerationRecipe::getBaseBlock),
            BLOCK_CODEC.optionalFieldOf("transform_block").forGetter(IncinerationRecipe::getTransformedBlock),
            INGREDIENTS_CODEC.fieldOf("ingredients").forGetter(recipe -> List.copyOf(recipe.getItemInputs())),
            RESULTS_CODEC.fieldOf("results").forGetter(IncinerationRecipe::getResults)).apply(instance, IncinerationRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, IncinerationRecipe> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.map(
                    BuiltInRegistries.BLOCK::get,
                    BuiltInRegistries.BLOCK::getKey),
            IncinerationRecipe::getBaseBlock,
            OPTIONAL_BLOCK_STREAM_CODEC,
            IncinerationRecipe::getTransformedBlock,
            IncinerationIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()),
            recipe -> List.copyOf(recipe.getItemInputs()),
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()),
            IncinerationRecipe::getResults,
            IncinerationRecipe::new);

    @Override
    public MapCodec<IncinerationRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, IncinerationRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
