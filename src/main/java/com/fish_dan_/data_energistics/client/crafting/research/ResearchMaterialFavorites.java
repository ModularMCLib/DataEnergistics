package com.fish_dan_.data_energistics.client.crafting.research;

import com.fish_dan_.data_energistics.client.crafting.tree.viewer.CraftingPlanIngredientViewers;

import appeng.api.stacks.GenericStack;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Bridges confirmed shortages to the installed ingredient viewer on the client thread. */
public final class ResearchMaterialFavorites {

    private ResearchMaterialFavorites() {}

    public static void receive(Iterable<GenericStack> materials, Player player) {
        int added = 0;
        int unavailable = 0;
        for (GenericStack stack : materials) {
            if (CraftingPlanIngredientViewers.favorite(stack)) added++;
            else unavailable++;
        }
        if (added > 0) player.displayClientMessage(Component.translatable("research.data_energistics.material_favorites_added", added), false);
        if (unavailable > 0) player.displayClientMessage(Component.translatable("research.data_energistics.material_favorites_unavailable", unavailable), false);
    }
}
