package com.fish_dan_.data_energistics.common.crafting.pattern.matching;

import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Resolves explicit ingredient tag declarations; item membership alone never invents a recipe constraint.
 */
@NullMarked
public final class RecipeIngredientTags {

    private RecipeIngredientTags() {}

    /**
     * Resolves on the server during pattern editing. The pattern is read-only; unknown recipes, invalid slots,
     * item-only ingredients and ambiguous custom ingredient encodings return no selectable tag.
     * Multiple native roles matching the same encoded key must all declare the chosen tag.
     */
    public static ObjectList<ResourceLocation> resolve(ServerLevel level, ItemStack encodedPattern, int sparseInputIndex) {
        return resolveSlot(level, encodedPattern, sparseInputIndex, false);
    }

    /** Resolves only explicit native output-role tags; a fixed output item never implies its membership tags. */
    public static ObjectList<ResourceLocation> resolveOutput(ServerLevel level, ItemStack encodedPattern, int outputIndex) {
        return resolveSlot(level, encodedPattern, outputIndex, true);
    }

    private static ObjectList<ResourceLocation> resolveSlot(ServerLevel level, ItemStack encodedPattern, int index, boolean output) {
        var processing = encodedPattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        var recipeId = encodedPattern.get(DEDataComponents.PROCESSING_PATTERN_RECIPE_ID.get());
        if (processing == null || recipeId == null || index < 0) return ObjectList.of();
        var slots = output ? processing.sparseOutputs() : processing.sparseInputs();
        if (index >= slots.size()) return ObjectList.of();
        var selected = slots.get(index);
        if (selected == null || !(selected.what() instanceof AEItemKey item) || selected.amount() <= 0)
            return ObjectList.of();
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty()) return ObjectList.of();
        ItemStack stack = item.toStack((int) Math.min(selected.amount(), Integer.MAX_VALUE));
        var common = new ObjectLinkedOpenHashSet<ResourceLocation>();
        boolean matched = false;
        for (Ingredient ingredient : ingredients(level, recipeId, holder.get().value(), output)) {
            if (ingredient.isEmpty() || !ingredient.test(stack)) continue;
            var encoded = Ingredient.CODEC.encodeStart(JsonOps.INSTANCE, ingredient).getOrThrow();
            var declared = new ObjectLinkedOpenHashSet<ResourceLocation>();
            collectVanillaTags(encoded, declared);
            declared.removeIf(tag -> !stack.is(TagKey.create(Registries.ITEM, tag)));
            if (!matched) common.addAll(declared);
            else common.retainAll(declared);
            matched = true;
            if (common.isEmpty()) return ObjectList.of();
        }
        return matched ? new ObjectImmutableList<>(common) : ObjectList.of();
    }

    private static List<Ingredient> ingredients(ServerLevel level, ResourceLocation recipeId,
                                                Recipe<?> recipe, boolean output) {
        for (var adapter : DataEnergisticsEntrypointLoader.snapshot().recipeMatching()) {
            var roles = output ? adapter.outputIngredients(level, recipeId) : adapter.inputIngredients(level, recipeId);
            if (roles != null) return roles;
        }
        if (output) return ObjectList.of();
        return recipe.getIngredients();
    }

    private static void collectVanillaTags(JsonElement value, ObjectLinkedOpenHashSet<ResourceLocation> tags) {
        if (value.isJsonArray()) {
            for (JsonElement alternative : value.getAsJsonArray()) collectVanillaTags(alternative, tags);
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            // A nested tag in a custom AND/difference/component ingredient is not the full constraint.
            if (object.size() != 1 || !object.has("tag") || !object.get("tag").isJsonPrimitive() ||
                    !object.getAsJsonPrimitive("tag").isString())
                return;
            var id = ResourceLocation.tryParse(object.get("tag").getAsString());
            if (id != null) tags.add(id);
        }
    }
}
