package com.fish_dan_.data_energistics.mixin.viewer.jei;

import net.minecraft.world.inventory.AbstractContainerMenu;

import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.library.transfer.BasicRecipeTransferHandler;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes JEI's registered slot semantics for supplemental ME supply, without guessing machine input positions.
 * Only the client transfer callback may use this view while its original menu is still open.
 */
@Mixin(value = BasicRecipeTransferHandler.class, remap = false)
@NullMarked
public interface BasicRecipeTransferHandlerAccessor<C extends AbstractContainerMenu, R> {

    /** Returns the handler's registered transfer contract; querying it does not move items or open menus. */
    @Accessor("transferInfo")
    IRecipeTransferInfo<C, R> dataEnergistics$getTransferInfo();
}
