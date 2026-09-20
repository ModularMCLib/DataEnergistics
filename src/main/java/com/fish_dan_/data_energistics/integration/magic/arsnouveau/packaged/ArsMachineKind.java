package com.fish_dan_.data_energistics.integration.magic.arsnouveau.packaged;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.hollingsworth.arsnouveau.api.imbuement_chamber.IImbuementRecipe;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.common.block.tile.EnchantingApparatusTile;
import com.hollingsworth.arsnouveau.common.block.tile.ImbuementTile;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ApparatusRecipeInput;
import com.hollingsworth.arsnouveau.common.crafting.recipes.IEnchantingRecipe;
import com.hollingsworth.arsnouveau.common.datagen.ItemTagProvider;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.util.List;

public enum ArsMachineKind {

    APPARATUS("enchanting_apparatus", 10, ObjectSet.of(
            id("enchanting_apparatus"), id("enchantment"), id("armor_upgrade"), id("glyph"),
            id("reactive_enchantment"), id("spell_write"), id("book_upgrade"), id("potion_flask"), id("prestidigitation"))),
    IMBUEMENT("imbuement", 2, ObjectSet.of(id("imbuement")));

    final String path;
    final int loadedRadius;
    final ObjectSet<ResourceLocation> categories;

    ArsMachineKind(String path, int loadedRadius, ObjectSet<ResourceLocation> categories) {
        this.path = path;
        this.loadedRadius = loadedRadius;
        this.categories = categories;
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("ars_nouveau", path);
    }

    boolean accepts(@Nullable BlockEntity tile) {
        return this == APPARATUS ? tile instanceof EnchantingApparatusTile : tile instanceof ImbuementTile;
    }

    boolean accepts(Recipe<?> recipe) {
        return this == APPARATUS ? recipe instanceof IEnchantingRecipe : recipe instanceof IImbuementRecipe;
    }

    boolean retained(ItemStack stack) {
        return this == IMBUEMENT || stack.is(ItemTagProvider.APPARATUS_PRESERVES);
    }

    ItemStack remainder(ItemStack stack) {
        return retained(stack) ? stack.copy() : stack.getCraftingRemainingItem();
    }

    @Nullable
    ItemStack validate(ServerLevel level, BlockEntity tile, ResourceLocation recipeId,
                       Recipe<?> recipe, ItemStack center, List<ItemStack> pedestals) {
        if (this == APPARATUS && recipe instanceof IEnchantingRecipe enchanting) {
            var input = new ApparatusRecipeInput(center.copy(), pedestals, null);
            if (!enchanting.matches(input, level)) return null;
            var selected = IEnchantingRecipe.getRecipe(level, input);
            if (selected == null || !selected.id().equals(recipeId)) return null;
            if (enchanting.consumesSource() && !SourceUtil.hasSourceNearby(tile.getBlockPos(), level, 10, enchanting.sourceCost())) return null;
            return enchanting.assemble(input, level.registryAccess());
        }
        if (this == IMBUEMENT && tile instanceof ImbuementTile chamber && recipe instanceof IImbuementRecipe imbuement) {
            var input = new ImbuementInputSnapshot(chamber, center, pedestals);
            if (!imbuement.matches(input, level)) return null;
            var selected = IImbuementRecipe.getRecipe(level, input);
            if (selected == null || !selected.id().equals(recipeId) || imbuement.getSourceCost(input) > chamber.getMaxSource()) return null;
            ItemStack result = imbuement.assemble(input, level.registryAccess());
            // Without a public completion sequence, an unchanged center cannot prove that the chamber has processed it.
            return ItemStack.matches(center, result) ? null : result;
        }
        return null;
    }

    List<BlockPos> positions(BlockEntity tile) {
        return tile instanceof EnchantingApparatusTile apparatus ? apparatus.pedestalList() :
                ((ImbuementTile) tile).getNearbyPedestals();
    }
}
